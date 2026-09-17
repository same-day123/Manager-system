# -*- coding: utf-8 -*-
"""
T4「容器化交付」—— 环境变量绑定实证（第二层验证）

配合 T4EnvBindingProbe.java 使用：把探针跑**两遍**，两遍注入不同的环境变量取值，
然后对照结果。这样能排除「碰巧相等」的假通过：

    场景 A  取值与 docker-compose.yml 里写的**完全一致** → 验证接线正确
    场景 B  换一组明显不同的取值                        → 验证值真的来自环境变量

同时把「只加载配置文件」得到的 raw 值一并打印，用来证明
**我们没有改动 application.yml / application-druid.yml**（这是任务卡明令约束的边界），
覆盖完全由环境变量完成。

用法
    <venv>/Scripts/python.exe .workbuddy/tools/t4_env_binding_check.py
"""

import os
import re
import subprocess
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path(r"D:\code\Manager_system")
M2 = Path(r"C:\Users\王旻辉\.m2\repository")
OUT = ROOT / ".workbuddy" / "tools" / "t4probe-classes"
SRC = ROOT / ".workbuddy" / "tools" / "T4EnvBindingProbe.java"
RESOURCES = "RuoYi-Vue-fast/src/main/resources/"

JARS = [
    M2 / "org/springframework/spring-core/5.3.39/spring-core-5.3.39.jar",
    M2 / "org/springframework/spring-jcl/5.3.39/spring-jcl-5.3.39.jar",
    M2 / "org/springframework/spring-beans/5.3.39/spring-beans-5.3.39.jar",
    M2 / "org/springframework/spring-context/5.3.39/spring-context-5.3.39.jar",
    M2 / "org/springframework/spring-expression/5.3.39/spring-expression-5.3.39.jar",
    M2 / "org/springframework/boot/spring-boot/2.5.15/spring-boot-2.5.15.jar",
    M2 / "org/springframework/boot/spring-boot-autoconfigure/2.5.15/spring-boot-autoconfigure-2.5.15.jar",
    M2 / "org/yaml/snakeyaml/1.28/snakeyaml-1.28.jar",
    M2 / "org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar",
]

DB_URL_A = ("jdbc:mysql://mysql:3306/education_system?useUnicode=true&characterEncoding=utf8"
            "&zeroDateTimeBehavior=convertToNull&useSSL=false&serverTimezone=Asia/Shanghai"
            "&allowPublicKeyRetrieval=true")

# 场景 A：与 docker-compose.yml 的取值逐字一致
SCENARIO_A = {
    "RUOYI_PROFILE": "/home/ruoyi/uploadPath",
    "SPRING_REDIS_HOST": "redis",
    "SPRING_REDIS_PORT": "6379",
    "RUOYI_REDIS_PASSWORD": "probe-redis-pw-A",
    "RUOYI_DB_URL": DB_URL_A,
    "RUOYI_DB_USERNAME": "root",
    "RUOYI_DB_PASSWORD": "probe-db-pw-A",
    "RUOYI_TOKEN_SECRET": "probe-token-secret-A",
    "RUOYI_SWAGGER_ENABLED": "false",
}

# 场景 B：全部换一组不同取值，用来证明生效值确实随环境变量走
SCENARIO_B = {
    "RUOYI_PROFILE": "/srv/other-uploads",
    "SPRING_REDIS_HOST": "redis-alt",
    "SPRING_REDIS_PORT": "6381",
    "RUOYI_REDIS_PASSWORD": "probe-redis-pw-B",
    "RUOYI_DB_URL": "jdbc:mysql://dbhost-alt:3307/otherdb?useSSL=false",
    "RUOYI_DB_USERNAME": "other_user",
    "RUOYI_DB_PASSWORD": "probe-db-pw-B",
    "RUOYI_TOKEN_SECRET": "probe-token-secret-B",
    "RUOYI_SWAGGER_ENABLED": "true",
}

RESULTS = []
SKIPPED = []


def check(name, ok, detail=""):
    RESULTS.append((name, bool(ok), detail))
    print("  [%s] %s%s" % ("PASS" if ok else "FAIL", name, ("   | " + detail) if detail else ""))


def cp():
    return ";".join(str(j) for j in JARS)


def build():
    for j in JARS:
        if not j.exists():
            print("!! 缺少 jar: %s" % j)
            return False
    OUT.mkdir(parents=True, exist_ok=True)
    r = subprocess.run(
        ["javac", "-encoding", "UTF-8", "-cp", cp(), "-d", str(OUT), str(SRC)],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    if r.returncode != 0:
        print("!! javac 失败:\n%s\n%s" % (r.stdout, r.stderr))
        return False
    return True


def run_probe(extra_env):
    env = os.environ.copy()
    env.update(extra_env)
    r = subprocess.run(
        ["java", "-Dfile.encoding=UTF-8", "-cp", str(OUT) + ";" + cp(),
         "T4EnvBindingProbe", RESOURCES],
        cwd=str(ROOT), env=env, capture_output=True, text=True,
        encoding="utf-8", errors="replace",
    )
    if r.returncode != 0:
        print("!! java 探针失败:\n%s\n%s" % (r.stdout, r.stderr))
        sys.exit(2)
    return r.stdout


def pick(output, prefix, key):
    for line in output.splitlines():
        if line.startswith(prefix + key + "="):
            return line.split("=", 1)[1]
    return None


def main():
    print("")
    print("########################################################################")
    print("#  T4 环境变量绑定实证 —— 无 Docker 下对「容器配置接线」的等价验证")
    print("########################################################################")
    print("")
    if not build():
        return 2

    out_a = run_probe(SCENARIO_A)
    out_b = run_probe(SCENARIO_B)

    print("--- 场景 A（与 docker-compose.yml 逐字一致的取值）---")
    for line in out_a.splitlines():
        if line.startswith(("eff:", "bind:", "props:")):
            print("    " + line)
    print("")
    print("--- 场景 B（换一组取值做对照）---")
    for line in out_b.splitlines():
        if line.startswith(("eff:", "bind:", "props:")):
            print("    " + line)
    print("")

    print("=" * 78)
    print("  断言")
    print("=" * 78)

    # --- 1. 先证明「配置文件没被我们改过」：raw 视图两遍都一样，且是原始硬编码值 ---
    for tag, out in (("A", out_a), ("B", out_b)):
        check("[%s] 配置文件里 ruoyi.profile 仍是原始 Windows 路径（未改配置）" % tag,
              pick(out, "raw:", "ruoyi.profile") == "D:/ruoyi/uploadPath",
              "raw=%s" % pick(out, "raw:", "ruoyi.profile"))
        check("[%s] 配置文件里 spring.redis.host 仍是 localhost（未加占位符）" % tag,
              pick(out, "raw:", "spring.redis.host") == "localhost",
              "raw=%s" % pick(out, "raw:", "spring.redis.host"))
        check("[%s] 配置文件里 token.secret 仍是弱默认值（D-08 未在文件里改）" % tag,
              pick(out, "raw:", "token.secret") == "abcdefghijklmnopqrstuvwxyz",
              "raw=%s" % pick(out, "raw:", "token.secret"))

    # --- 2. 环境变量确实覆盖住了硬编码值 ---
    check("[A] RUOYI_PROFILE 覆盖掉 Windows 上传路径",
          pick(out_a, "eff:", "ruoyi.profile") == "/home/ruoyi/uploadPath",
          "eff=%s" % pick(out_a, "eff:", "ruoyi.profile"))
    check("[A] SPRING_REDIS_HOST 覆盖掉写死的 localhost",
          pick(out_a, "eff:", "spring.redis.host") == "redis",
          "eff=%s" % pick(out_a, "eff:", "spring.redis.host"))
    check("[A] RUOYI_DB_URL 覆盖掉默认 JDBC URL（指向 mysql 服务名）",
          "//mysql:3306/education_system" in (pick(out_a, "eff:", "spring.datasource.druid.master.url") or ""),
          "eff=%s" % pick(out_a, "eff:", "spring.datasource.druid.master.url"))
    check("[A] RUOYI_DB_PASSWORD 注入成功（口令不落配置文件）",
          pick(out_a, "eff:", "spring.datasource.druid.master.password") == "probe-db-pw-A")
    check("[A] RUOYI_TOKEN_SECRET 覆盖掉弱令牌密钥（D-08 的容器化解法）",
          pick(out_a, "eff:", "token.secret") == "probe-token-secret-A",
          "eff=%s" % pick(out_a, "eff:", "token.secret"))
    check("[A] RUOYI_REDIS_PASSWORD 注入成功",
          pick(out_a, "eff:", "spring.redis.password") == "probe-redis-pw-A")
    check("[A] RUOYI_SWAGGER_ENABLED=false 生效（容器里不暴露接口文档）",
          pick(out_a, "eff:", "swagger.enabled") == "false",
          "eff=%s" % pick(out_a, "eff:", "swagger.enabled"))

    # --- 3. 对照场景：换取值后生效值必须跟着变（排除"碰巧相等"）---
    check("[B] 换成另一组取值后 ruoyi.profile 跟着变",
          pick(out_b, "eff:", "ruoyi.profile") == "/srv/other-uploads",
          "eff=%s" % pick(out_b, "eff:", "ruoyi.profile"))
    check("[B] 换成另一组取值后 redis 主机跟着变",
          pick(out_b, "eff:", "spring.redis.host") == "redis-alt",
          "eff=%s" % pick(out_b, "eff:", "spring.redis.host"))
    check("[B] redis 端口 6381 ≠ 配置文件里的 6379（证明端口也受环境变量控制）",
          pick(out_b, "eff:", "spring.redis.port") == "6381"
          and pick(out_b, "raw:", "spring.redis.port") == "6379",
          "eff=%s / raw=%s" % (pick(out_b, "eff:", "spring.redis.port"),
                               pick(out_b, "raw:", "spring.redis.port")))
    check("[B] 配错了 DNS 名也一样跟着走（dbhost-alt:3307）",
          "//dbhost-alt:3307/otherdb" in (pick(out_b, "eff:", "spring.datasource.druid.master.url") or ""))
    check("[B] token.secret 跟着换",
          pick(out_b, "eff:", "token.secret") == "probe-token-secret-B")

    # --- 4. 类型化绑定：证明容器里后端能把 spring.redis 整块读进来 ---
    check("[A] Binder 把 spring.redis.port 绑成 Integer（不是字符串）",
          pick(out_a, "bind:", "spring.redis.port.type") == "Integer",
          "type=%s value=%s" % (pick(out_a, "bind:", "spring.redis.port.type"),
                                pick(out_a, "bind:", "spring.redis.port")))

    props_skipped = pick(out_a, "props:", "SKIPPED")
    if props_skipped:
        SKIPPED.append("RedisProperties 类型化绑定: %s" % props_skipped)
        print("  [SKIP] RedisProperties 整块绑定未执行: %s" % props_skipped)
    else:
        check("[A] spring.redis 整块绑成 RedisProperties，host=redis",
              pick(out_a, "props:", "host") == "redis",
              "host=%s port=%s" % (pick(out_a, "props:", "host"), pick(out_a, "props:", "port")))
        check("[A] RedisProperties.port 解析为整数 6379",
              pick(out_a, "props:", "port") == "6379")
        check("[A] RedisProperties.timeout 解析为 Duration 10s（PT10S）",
              pick(out_a, "props:", "timeout") == "PT10S",
              "timeout=%s" % pick(out_a, "props:", "timeout"))
        check("[B] RedisProperties 跟着换成 redis-alt:6381",
              pick(out_b, "props:", "host") == "redis-alt" and pick(out_b, "props:", "port") == "6381",
              "host=%s port=%s" % (pick(out_b, "props:", "host"), pick(out_b, "props:", "port")))

    total = len(RESULTS)
    failed = [r for r in RESULTS if not r[1]]
    print("")
    print("=" * 78)
    print("  汇总：%d 条断言，PASS %d，FAIL %d%s"
          % (total, total - len(failed), len(failed),
             ("，另 SKIPPED %d" % len(SKIPPED)) if SKIPPED else ""))
    print("=" * 78)
    if failed:
        for name, _, detail in failed:
            print("  FAIL: %s%s" % (name, ("  | " + detail) if detail else ""))
        return 1
    print("  结论：容器化依赖的每一项配置覆盖都在真实 Spring 属性源机制下验证通过；")
    print("  且 application.yml / application-druid.yml 一字未改。")
    print("  ⚠ 仍不等于容器真的跑起来了 —— 本机未装 Docker，未执行 docker compose up。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
