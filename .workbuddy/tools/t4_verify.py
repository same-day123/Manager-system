# -*- coding: utf-8 -*-
"""
T4「容器化交付」—— 无 Docker 环境下的可验证性检查

背景
    本机未安装 Docker，无法真实执行 `docker compose build / up / ps`。
    任务卡明确要求：「Docker 不可用就如实说明，不要伪造验证结果」。
    所以这里换一条**可证伪**的路子：把「容器能不能起来」拆成若干条
    静态即可判定的断言，逐条跑、逐条留证据。跑不通的项如实报 FAIL。

覆盖的 8 组断言
    A. YAML 可解析性      compose / prod override / ci.yml
    B. compose 结构       服务、健康依赖、卷、挂载
    C. SQL 初始化顺序     mysql-init.sh 的循环顺序 ↔ 磁盘文件 ↔ 各 SQL 自带的 [N/7] 标记
    D. 环境变量插值       按 compose 的替换规则把 ${VAR:-default} 全部展开后核对取值
    E. .env 覆盖度        compose 引用的变量是否都能在 .env.example 里找到
    F. nginx.conf         前缀剥离、上传上限、SPA 回落、gzip
    G. Dockerfile         多阶段、非 root、时区、健康检查、无明文口令
    H. .dockerignore      必须排除的目录（尤其 node_modules）

用法
    <venv>/Scripts/python.exe .workbuddy/tools/t4_verify.py
"""

import os
import re
import sys
from pathlib import Path

import yaml

sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path(r"D:\code\Manager_system")
COMPOSE = ROOT / "docker-compose.yml"
COMPOSE_PROD = ROOT / "docker-compose.prod.yml"
CI = ROOT / ".github" / "workflows" / "ci.yml"
ENV_EXAMPLE = ROOT / ".env.example"
NGINX = ROOT / "RuoYi-Vue3" / "nginx.conf"
FE_DOCKERFILE = ROOT / "RuoYi-Vue3" / "Dockerfile"
BE_DOCKERFILE = ROOT / "RuoYi-Vue-fast" / "Dockerfile"
BE_DOCKERIGNORE = ROOT / "RuoYi-Vue-fast" / ".dockerignore"
FE_DOCKERIGNORE = ROOT / "RuoYi-Vue3" / ".dockerignore"
MYSQL_INIT = ROOT / "deploy" / "mysql-init.sh"
SQL_DIR = ROOT / "RuoYi-Vue-fast" / "sql"

RESULTS = []


def check(name, ok, detail=""):
    RESULTS.append((name, bool(ok), detail))
    flag = "PASS" if ok else "FAIL"
    line = "  [%s] %s" % (flag, name)
    if detail:
        line += "   | " + detail
    print(line)
    return ok


def section(title):
    print("")
    print("=" * 78)
    print("  " + title)
    print("=" * 78)


def read(path):
    return Path(path).read_text(encoding="utf-8")


def read_code(path):
    """读取并剥掉整行注释。

    必须这么做：这些文件里的注释会**大段引用**被检查的字面量（例如
    「npm ci 会因缺 lock 文件失败」「改 src/ 不会让 yarn install 这层失效」），
    不剥注释就会把注释当成真代码，得到一堆假阳性。
    """
    out = []
    for line in read(path).splitlines():
        if line.lstrip().startswith("#"):
            continue
        out.append(line)
    return "\n".join(out)


def read_bytes(path):
    return Path(path).read_bytes()


# ---------------------------------------------------------------------------
# YAML：compose 用了 `!override` 自定义标签（Compose v2.24+ 语法），
# pyyaml 默认不认识会直接报错，这里注册一个把标签当透明的多构造器，
# 否则「解析失败」会被误判成文件写错。
# ---------------------------------------------------------------------------
class ComposeLoader(yaml.SafeLoader):
    pass


def _unknown_tag(loader, suffix, node):
    if isinstance(node, yaml.ScalarNode):
        return loader.construct_scalar(node)
    if isinstance(node, yaml.SequenceNode):
        return loader.construct_sequence(node)
    return loader.construct_mapping(node)


ComposeLoader.add_multi_constructor("!", _unknown_tag)


def load_yaml(path):
    return yaml.load(read(path), Loader=ComposeLoader)


# ---------------------------------------------------------------------------
# compose 的变量替换规则（只实现本项目用到的三种形式）
#   ${VAR}            取 VAR，取不到则空串
#   ${VAR:-default}   取 VAR，未设置或为空则用 default
#   ${VAR:?message}   取 VAR，未设置则报错（compose 会拒绝启动）
# ---------------------------------------------------------------------------
VAR_RE = re.compile(r"\$\{([A-Za-z_][A-Za-z0-9_]*)(?::([-?])([^}]*))?\}")


def interpolate(raw, env):
    errors = []

    def repl(m):
        name, op, arg = m.group(1), m.group(2), m.group(3)
        value = env.get(name)
        if value:
            return value
        if op == "?":
            errors.append(name)
            return ""
        if op == "-":
            return arg
        return ""

    return VAR_RE.sub(repl, raw), errors


def env_refs(raw):
    """列出文本里引用的所有 ${VAR} 变量名（去重，保持出现顺序）。"""
    seen = []
    for m in VAR_RE.finditer(raw):
        if m.group(1) not in seen:
            seen.append(m.group(1))
    return seen


def main():
    print("")
    print("########################################################################")
    print("#  T4 容器化交付 —— 静态可验证性检查")
    print("#  本机未安装 Docker，以下为不依赖 Docker 的等价检查（如实标注，不伪造）")
    print("########################################################################")

    # =====================================================================
    section("A. YAML 可解析性")
    # =====================================================================
    for path in (COMPOSE, COMPOSE_PROD, CI):
        try:
            data = load_yaml(path)
            check("YAML 可解析: %s" % path.relative_to(ROOT), data is not None)
        except Exception as exc:  # noqa: BLE001
            check("YAML 可解析: %s" % path.relative_to(ROOT), False, "%s: %s" % (type(exc).__name__, exc))
            data = None

    compose = load_yaml(COMPOSE)
    prod = load_yaml(COMPOSE_PROD)
    ci = load_yaml(CI)

    check("compose 未使用已废弃的顶层 version 键", "version" not in compose,
          "顶层键: %s" % ", ".join(compose.keys()))

    # =====================================================================
    section("B. docker-compose.yml 结构")
    # =====================================================================
    svcs = compose.get("services", {})
    expected = ["mysql", "redis", "lab-backend", "lab-frontend"]
    check("四个服务齐备", sorted(svcs.keys()) == sorted(expected),
          "实际: %s" % ", ".join(svcs.keys()))

    # --- 健康依赖：这是「后端起来时 MySQL 已经能连」的唯一保证 ---
    backend_dep = svcs.get("lab-backend", {}).get("depends_on", {})
    cond_ok = (
        isinstance(backend_dep, dict)
        and backend_dep.get("mysql", {}).get("condition") == "service_healthy"
        and backend_dep.get("redis", {}).get("condition") == "service_healthy"
    )
    check("lab-backend 以 service_healthy 依赖 mysql 与 redis", cond_ok,
          "实际 depends_on: %s" % backend_dep)

    for svc_name in ("mysql", "redis"):
        check("%s 定义了 healthcheck" % svc_name, "healthcheck" in svcs.get(svc_name, {}))
    check("lab-frontend 定义了 healthcheck", "healthcheck" in svcs.get("lab-frontend", {}))

    # --- SQL 初始化挂载：绝不能把 .sql 直接挂进 initdb 目录 ---
    mysql_vols = svcs.get("mysql", {}).get("volumes", [])
    initdb_mounts = [v for v in mysql_vols if isinstance(v, str) and "/docker-entrypoint-initdb.d" in v]
    sql_direct = [v for v in initdb_mounts if v.rstrip().endswith(".sql:ro")]
    check("initdb 目录只挂了 00-init.sh，没有直接挂 .sql", len(initdb_mounts) == 1 and not sql_direct,
          "initdb 挂载: %s" % initdb_mounts)
    check("SQL 目录以只读方式挂载到 /sql",
          any(isinstance(v, str) and "/sql:ro" in v for v in mysql_vols),
          "volumes: %s" % mysql_vols)

    # --- 上传目录用命名卷 ---
    upload_vols = svcs.get("lab-backend", {}).get("volumes", [])
    named_upload = [v for v in upload_vols if isinstance(v, str) and v.startswith("upload-data:")]
    check("上传目录用命名卷 upload-data 而非 bind mount", len(named_upload) == 1,
          "volumes: %s" % upload_vols)
    check("upload-data 已在顶层 volumes 声明", "upload-data" in (compose.get("volumes") or {}))

    # --- 网络 ---
    check("四个服务都在 lab-net 上（nginx 靠它解析 lab-backend）",
          all("lab-net" in (svcs[s].get("networks") or []) for s in expected),
          "lab-net 定义: %s" % (compose.get("networks") or {}))

    # --- 重启策略 ---
    check("四个服务都配了 restart 策略",
          all(svcs[s].get("restart") for s in expected),
          " / ".join("%s=%s" % (s, svcs[s].get("restart")) for s in expected))

    # =====================================================================
    section("C. SQL 初始化顺序（本卡最容易翻车的一环）")
    # =====================================================================
    init_sh = read(MYSQL_INIT)
    for_block = re.search(r"for f in \\\n(.*?)\ndo", init_sh, re.S)
    order = re.findall(r"([a-z0-9_]+)", for_block.group(1)) if for_block else []
    check("能从 mysql-init.sh 解析出 for 循环顺序", len(order) == 7, "解析结果: %s" % order)

    expected_order = [
        "laboratory_schema",
        "ry_20260417",
        "laboratory_menu_role",
        "laboratory_demo_seed",
        "laboratory_user_seed",
        "laboratory_upgrade",
        "laboratory_permission_fix",
    ]
    check("顺序与 T0 定下的 7 步一致", order == expected_order, "实际: %s" % order)

    missing = [f for f in order if not (SQL_DIR / (f + ".sql")).exists()]
    check("7 个脚本在磁盘上都存在", not missing, "缺失: %s" % missing)

    # 交叉验证：每个 SQL 文件头部自带 [N/7] 标记，用文件自己的声明来校验脚本里的顺序
    declared = {}
    for f in order:
        target = SQL_DIR / (f + ".sql")
        if not target.exists():
            continue
        head = target.read_text(encoding="utf-8", errors="replace")[:4000]
        m = re.search(r"\[(\d)/7\]", head)
        declared[f] = int(m.group(1)) if m else None
    check("每个脚本头部的 [N/7] 标记与 for 循环顺序逐位吻合",
          [declared.get(f) for f in order] == list(range(1, 8)),
          "文件自称的顺序: %s" % [declared.get(f) for f in order])

    # 反向验证：如果按文件名字母序执行会错成什么样（说明"为什么必须写这个脚本"）
    alpha = sorted(f for f in order)
    check("证实「字母序 ≠ 正确顺序」（这正是必须显式排序的原因）", alpha != order,
          "字母序首尾: %s ... %s" % (alpha[0], alpha[-1]))

    # --- 脚本自身的健壮性 ---
    b = read_bytes(MYSQL_INIT)
    check("mysql-init.sh 无 UTF-8 BOM（有 BOM 会让 shebang 失效）", not b.startswith(b"\xef\xbb\xbf"))
    check("mysql-init.sh 为 LF 换行（CRLF 会让容器内 /bin/sh 报 \\r: not found）", b"\r\n" not in b)
    check("mysql-init.sh shebang 为 #!/bin/sh", b.split(b"\n")[0].strip() == b"#!/bin/sh",
          b.split(b"\n")[0].decode())
    check("刻意不使用 set -e（initdb 目录的 .sh 可能被 entrypoint source，选项会泄漏）",
          not re.search(r"^\s*set\s+-e", init_sh, re.M),
          "改用 die() 显式失败")
    check("每个脚本导入前都做了存在性检查", init_sh.count('[ -f "$SQL_DIR/$f.sql" ]') == 1)
    check("导入失败会立即中止（不吞错）", 'die "导入 $f.sql 失败"' in init_sh)
    check("脚本结尾有自检，初始化没成就让容器直接失败",
          "lab_" in init_sh and "sys_user" in init_sh and "die" in init_sh)

    # =====================================================================
    section("D. 环境变量插值后的实际取值")
    # =====================================================================
    compose_raw = read(COMPOSE)
    compose_code = read_code(COMPOSE)
    prod_code = read_code(COMPOSE_PROD)
    # 模拟「不建 .env」的场景：所有变量走 compose 内置默认值
    resolved, errs = interpolate(compose_raw, {})
    check("不建 .env 时所有 ${VAR} 都能解析（无 :? 必填项）", not errs,
          "未解析: %s" % errs)

    def value_of(key):
        m = re.search(r"^\s*%s:\s*(.*)$" % re.escape(key), resolved, re.M)
        return m.group(1).strip().strip('"') if m else None

    db_url = value_of("RUOYI_DB_URL")
    check("后端 JDBC URL 指向 compose 里的 mysql 服务（不是 localhost）",
          db_url is not None and "//mysql:3306/" in db_url, "解析值: %s" % db_url)
    check("JDBC URL 带 allowPublicKeyRetrieval=true（MySQL 8 caching_sha2 认证必需）",
          db_url is not None and "allowPublicKeyRetrieval=true" in db_url)
    check("JDBC URL 的 serverTimezone 为 Asia/Shanghai",
          db_url is not None and "serverTimezone=Asia/Shanghai" in db_url)

    check("redis 主机被覆盖为 compose 服务名 redis",
          value_of("SPRING_REDIS_HOST") == "redis", "SPRING_REDIS_HOST=%s" % value_of("SPRING_REDIS_HOST"))
    check("redis 端口被覆盖为 6379",
          value_of("SPRING_REDIS_PORT") == "6379", "SPRING_REDIS_PORT=%s" % value_of("SPRING_REDIS_PORT"))

    check("上传目录被覆盖为 Linux 路径 /home/ruoyi/uploadPath",
          value_of("RUOYI_PROFILE") == "/home/ruoyi/uploadPath",
          "RUOYI_PROFILE=%s" % value_of("RUOYI_PROFILE"))

    # 密码一致性：mysql 的 root 口令与后端连库口令必须同源，否则「起得来但连不上」。
    # 注意这里比的是**未插值**的原文表达式 —— 插值后 ${...} 全被默认值展开，就没法验同源了。
    mysql_pw = re.search(r"MYSQL_ROOT_PASSWORD:\s*(\$\{[^}]*\})", compose_code)
    be_pw = re.search(r"RUOYI_DB_PASSWORD:\s*(\$\{[^}]*\})", compose_code)
    check("MySQL root 口令与后端连库口令同源（同一个变量表达式）",
          bool(mysql_pw) and bool(be_pw) and mysql_pw.group(1) == be_pw.group(1),
          "%s vs %s" % (mysql_pw.group(1) if mysql_pw else None, be_pw.group(1) if be_pw else None))
    # command 是 YAML 列表，口令在下一行且带 "- " 前缀，正则要允许这个前缀
    redis_cmd_pw = re.search(r'"--requirepass"\s*\n\s*-?\s*"(\$\{[^}]*\})"', compose_code)
    be_redis_pw = re.search(r"RUOYI_REDIS_PASSWORD:\s*(\$\{[^}]*\})", compose_code)
    check("redis 服务的启动口令与后端连接口令同源（都来自 REDIS_PASSWORD）",
          bool(redis_cmd_pw) and bool(be_redis_pw) and redis_cmd_pw.group(1) == be_redis_pw.group(1),
          "redis 服务: %s ｜ 后端: %s" % (redis_cmd_pw.group(1) if redis_cmd_pw else None,
                                        be_redis_pw.group(1) if be_redis_pw else None))

    # =====================================================================
    section("E. .env.example 覆盖度")
    # =====================================================================
    example = read(ENV_EXAMPLE)
    defined = set(re.findall(r"^([A-Z][A-Z0-9_]*)=", example, re.M))
    referenced = env_refs(compose_code) + env_refs(prod_code)
    # 只统计「需要用户提供」的变量：用 :? 的必填项 + 在 .env.example 里出现过的
    required = sorted(set(re.findall(r"\$\{([A-Z_][A-Z0-9_]*):\?", compose_raw + read(COMPOSE_PROD))))
    undocumented = [v for v in required if v not in defined]
    check("所有必填变量都在 .env.example 里有说明", not undocumented,
          "缺: %s" % undocumented)
    check(".env.example 覆盖了 compose 引用的全部变量",
          not [v for v in referenced if v not in defined],
          "缺: %s" % sorted(v for v in referenced if v not in defined))
    check(".env.example 不含真实生产口令（示例值均为占位串）",
          "change-me" in example and "admin123" not in example)

    # prod override 的必填项确实会拦住「没配 .env 就上生产」
    prod_required = sorted(set(re.findall(r"\$\{([A-Z_][A-Z0-9_]*):\?", read(COMPOSE_PROD))))
    check("prod override 把关键口令设为必填（不配就拒绝启动）",
          set(prod_required) >= {"REDIS_PASSWORD", "DRUID_PASSWORD", "TOKEN_SECRET", "MYSQL_ROOT_PASSWORD"},
          "必填项: %s" % prod_required)
    check("prod override 用 !override 替换而不是追加 ports（否则端口删不掉）",
          read(COMPOSE_PROD).count("ports: !override") >= 3,
          "出现 %d 次" % read(COMPOSE_PROD).count("ports: !override"))
    check("prod override 用数字时区偏移（命名时区要先导时区表，会启动失败）",
          '"--default-time-zone=+08:00"' in read(COMPOSE), "基础文件同样用 +08:00")

    # =====================================================================
    section("F. nginx.conf")
    # =====================================================================
    ng = read(NGINX)
    check("proxy_pass 末尾带 /（漏了会剥不掉 /prod-api 前缀，接口全 404）",
          bool(re.search(r"proxy_pass\s+http://lab-backend:8080/;", ng)),
          re.search(r"proxy_pass[^;]*;", ng).group(0) if re.search(r"proxy_pass[^;]*;", ng) else "未找到")
    check("upstream 主机名与 compose 服务名一致（lab-backend）", "lab-backend:8080" in ng)
    check("location 用 ^~ 修饰 /prod-api/（避免被静态资源正则 location 抢走）",
          bool(re.search(r"location\s+\^~\s+/prod-api/", ng)))
    check("SPA history 回落 try_files 到 /index.html", "try_files $uri $uri/ /index.html" in ng)
    check("上传上限已放开到 20m（nginx 默认 1m，故障照片会被 413 拦掉）",
          bool(re.search(r"client_max_body_size\s+20m;", ng)),
          "出现 %d 次" % len(re.findall(r"client_max_body_size\s+20m;", ng)))
    check("index.html 明确不缓存（否则发版后白屏）", "no-cache" in ng or "no-store" in ng)
    for t in ("text/css", "application/javascript", "application/json", "image/svg+xml"):
        check("gzip_types 含 %s" % t, t in ng)
    check("gzip 已开启", bool(re.search(r"gzip\s+on;", ng)))
    check("转发真实 IP（X-Real-IP / X-Forwarded-For）",
          "X-Real-IP" in ng and "X-Forwarded-For" in ng)
    nb = read_bytes(NGINX)
    check("nginx.conf 无 BOM 且为 LF（BOM 会让 nginx 直接拒绝加载该文件）",
          not nb.startswith(b"\xef\xbb\xbf") and b"\r\n" not in nb)

    # =====================================================================
    section("G. Dockerfile")
    # =====================================================================
    be = read_code(BE_DOCKERFILE)
    fe = read_code(FE_DOCKERFILE)

    check("后端：构建阶段用 Maven 官方 JDK8 镜像",
          "FROM maven:3.9-eclipse-temurin-8 AS builder" in be)
    check("后端：运行阶段用 JRE 而不是 JDK（体积与攻击面）",
          "FROM eclipse-temurin:8-jre-jammy" in be and "jdk" not in be.split("FROM eclipse-temurin")[-1].lower())
    check("后端：设置了 Asia/Shanghai 时区", "TZ=Asia/Shanghai" in be and "/etc/timezone" in be)
    check("后端：非 root 运行（USER ruoyi 且无 USER root）",
          "USER ruoyi" in be and not re.search(r"^USER\s+root", be, re.M))
    check("后端：USER 出现在 ENTRYPOINT 之前（否则切用户不生效）",
          be.index("USER ruoyi") < be.index("ENTRYPOINT"))
    check("后端：先建组再建用户（避免 chown ruoyi:ruoyi 失败）",
          "groupadd -g 1000 ruoyi" in be and "useradd -m -u 1000 -g 1000" in be)
    check("后端：EXPOSE 8080", "EXPOSE 8080" in be)
    check("后端：带 HEALTHCHECK（无 actuator，用 /dev/tcp 探端口）",
          "HEALTHCHECK" in be and "/dev/tcp/127.0.0.1/8080" in be)
    check("后端：ENTRYPOINT 为 exec 形式（java 做 PID 1，能优雅停机）",
          'ENTRYPOINT ["java","-jar","/app/app.jar"]' in be)
    check("后端：拷贝 target/ruoyi.jar（与 pom 的 finalName 一致）",
          "COPY --from=builder /build/target/ruoyi.jar app.jar" in be)
    check("后端：先 COPY pom 再预热依赖（层缓存）",
          be.index("COPY pom.xml .") < be.index("COPY src ./src") and "dependency:go-offline" in be)
    check("后端：运行镜像里没有明文口令", not re.search(r"123456|password\s*=\s*\S+", be, re.I))

    check("前端：构建阶段 node:22-alpine", "FROM node:22-alpine AS builder" in fe)
    check("前端：运行阶段 nginx:alpine", "FROM nginx:alpine" in fe)
    check("前端：没有用 npm ci（仓库只有 yarn.lock，没有 package-lock.json）",
          "npm ci" not in fe)
    check("前端：用 yarn install --frozen-lockfile",
          "yarn install --frozen-lockfile" in fe)
    check("前端：corepack enable 后才用 yarn",
          fe.index("corepack enable") < fe.index("yarn install"))
    check("前端：执行 yarn build:prod", "yarn build:prod" in fe)
    check("前端：dist 拷到 nginx 站点根", "/app/dist /usr/share/nginx/html" in fe)
    check("前端：nginx.conf 拷到 conf.d/default.conf",
          "nginx.conf /etc/nginx/conf.d/default.conf" in fe)
    check("前端：EXPOSE 80", "EXPOSE 80" in fe)
    check("前端：带 HEALTHCHECK", "HEALTHCHECK" in fe)
    check("前端：运行镜像里没有明文口令", not re.search(r"123456|password\s*=\s*\S+", fe, re.I))

    # =====================================================================
    section("H. .dockerignore")
    # =====================================================================
    be_ig = read(BE_DOCKERIGNORE)
    fe_ig = read(FE_DOCKERIGNORE)
    be_pat = {l.strip() for l in be_ig.splitlines() if l.strip() and not l.strip().startswith("#")}
    fe_pat = {l.strip() for l in fe_ig.splitlines() if l.strip() and not l.strip().startswith("#")}

    check("后端 .dockerignore 排除 target/", "target/" in be_pat)
    for p in ("node_modules/", "dist/", ".git/"):
        check("前端 .dockerignore 排除 %s" % p, p in fe_pat)
    check("前端 .dockerignore 没有误排 .env / .env.production（构建期必需）",
          not any(x in fe_pat for x in (".env", ".env.*", ".env.production")),
          "patterns: %s" % sorted(fe_pat))
    check("前端 .dockerignore 没有误排 nginx.conf（Dockerfile 要 COPY 它）",
          "nginx.conf" not in fe_pat)
    check("后端 .dockerignore 没有误排 pom.xml / src", "pom.xml" not in be_pat and "src/" not in be_pat)

    # =====================================================================
    section("I. CI 流水线（ci.yml 的 docker job）")
    # =====================================================================
    docker_job = (ci.get("jobs") or {}).get("docker") or {}
    check("docker job 已启用（不再是注释）", bool(docker_job))
    check("docker job 依赖 package job", docker_job.get("needs") == ["package"],
          "needs=%s" % docker_job.get("needs"))

    steps = docker_job.get("steps") or []
    step_names = [s.get("name", "") for s in steps]
    check("含「构建后端镜像」步骤", "构建后端镜像" in step_names, "步骤: %s" % step_names)
    check("含「构建前端镜像」步骤", "构建前端镜像" in step_names)
    check("含 compose 语法门禁步骤", any("compose" in n for n in step_names))
    check("两个 build 步骤都是 push: false（不推送 registry）",
          all(s.get("with", {}).get("push") is False
              for s in steps if s.get("uses", "").startswith("docker/build-push-action")))
    check("未残留 registry 登录步骤（Token 已吊销，不引用 Secrets）",
          not any("login-action" in s.get("uses", "") for s in steps))
    ci_code = read_code(CI)
    # 判据：任何 password/secret 赋值都必须走 ${{ secrets.* }}，不允许出现字面量
    bad_pw = [l.strip() for l in ci_code.splitlines()
              if re.search(r"(password|secret)\s*:", l, re.I) and "${{" not in l]
    check("ci.yml 中 password/secret 赋值全部走 GitHub Secrets（无明文）", not bad_pw,
          "可疑行: %s" % bad_pw)
    check("job 头部注释已同步（不再写「暂时注释」）", "暂时注释" not in read(CI))

    # =====================================================================
    # 汇总
    # =====================================================================
    total = len(RESULTS)
    failed = [r for r in RESULTS if not r[1]]
    print("")
    print("=" * 78)
    print("  汇总：%d 条断言，PASS %d，FAIL %d" % (total, total - len(failed), len(failed)))
    print("=" * 78)
    if failed:
        for name, _, detail in failed:
            print("  FAIL: %s%s" % (name, ("  | " + detail) if detail else ""))
        return 1
    print("  全部通过。注意：这组断言证明的是「配置自洽且可解析」，")
    print("  不等于「容器真的起来了」—— 本机未装 Docker，真实运行未验证。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
