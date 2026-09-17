# T3 CI 流水线与部署策略

| 项 | 值 |
| --- | --- |
| **优先级** | P1 |
| **依赖** | T1、T2（流水线的 Test 阶段要有测试可跑） |
| **阻塞** | 交付物 4《CI/CD 部署方案》全文、答辩 PPT 的「流水线运行截图」 |
| **产出** | `.github/workflows/ci.yml`＋`deploy/` 目录＋README 徽章 |
| **远程仓库** | ✅ **已定**：`https://github.com/same-day123/Manager-system.git`（阿辉 2026-09-17 提供）。本地已 `git init -b main` 并挂 `origin`；**首次 push 待阿辉确认提交范围** |

---

## 1. 目标

任务书要求 CI/CD 文档必须包含「环境规划 / 流水线设计图 / Jenkinsfile 或 `.yml` / 部署策略（选一并说明理由）」，占 **10 分**，其中「配置文件可运行性」3 分。

同时答辩明确要求演示「**测试或流水线运行结果截图**」。所以本卡有两个交付面：
1. **配置文件**：真实可用的 GitHub Actions 流水线
2. **可演示的运行证据**：一个本地等价脚本，按流水线阶段顺序执行并输出结果——**这是拿不到真实 CI 环境时的诚实替代方案**，不要伪造 CI 截图

---

## 2. 交付内容（验收标准）

### 2.1 前置：初始化 Git 仓库

- [x] 在 `D:\code\Manager_system` 执行 `git init -b main`（2026-09-17 已完成，D-09 解除）；首次提交待提交范围确认后执行
- [ ] 确认 `.gitignore` 已排除 `target/`、`dist/`、`node_modules/`、`.workbuddy/logs/`（已人工核对，提交前用 `git status --short --ignored` 复核）
- [ ] **绝对不要**把 `node_modules/` 提交进去（会变成几个 G 的仓库，push 必失败）
- [ ] 推到远程仓库 `origin` = `https://github.com/same-day123/Manager-system.git`（地址已提供，推送前先 `git ls-files | grep -c node_modules` 应为 0）

### 2.2 `.github/workflows/ci.yml`

- [ ] 三个 job：`backend` / `frontend` / `package`，`package` 依赖前两者全绿
- [ ] 触发条件：`push` 到 `main`、`pull_request`
- [ ] 用 `actions/setup-java@v4` 装 **JDK 8（temurin）**，`actions/setup-node@v4` 装 **Node 22**
- [ ] 开启 Maven 缓存（`cache: maven`）和 Yarn/NPM 缓存
- [ ] 上传测试报告与构建产物为 artifact（答辩截图就用这个）
- [ ] 镜像构建步骤先写成**注释掉的可选 job**，等 T4 完成后再打开

### 2.3 `deploy/local-pipeline.ps1`（答辩用）

- [ ] 按流水线阶段顺序在本地执行，每阶段打印 `[1/6] 后端测试 ... PASS (42.3s)`
- [ ] 末尾输出汇总表格：阶段 / 结果 / 耗时
- [ ] 任何一阶段失败立即 `exit 1` 并打印 `FAILED`
- [ ] 阶段划分与 `ci.yml` **一一对应**（这样截图才站得住）

阶段设计：

| # | 阶段 | 对应 CI job / step | 命令 |
| :-: | --- | --- | --- |
| 1 | 版本信息 | checkout | 打印 `git rev-parse --short HEAD`（无 git 时打印 `local`） |
| 2 | 后端构建与测试 | backend → mvn test | `.workbuddy/tools/mvnx.ps1 -o -B clean test` |
| 3 | 前端构建 | frontend → vite build | `node RuoYi-Vue3/node_modules/vite/bin/vite.js build --mode production` |
| 4 | 打包产物 | package → mvn package | `.workbuddy/tools/mvnx.ps1 -o -B package -DskipTests` |
| 5 | 镜像构建 | docker build（T4 后启用） | `docker build -t lab-asset-backend:ci ./RuoYi-Vue-fast` |
| 6 | 冒烟测试 | smoke | 起容器后 `curl` 健康检查；docker 不可用时打印 `SKIPPED` 而不是失败 |

### 2.4 三环境规划（文档用，本卡只产出表格）

| 维度 | 开发环境 dev | 类生产环境 staging | 生产环境 prod |
| --- | --- | --- | --- |
| 用途 | 本地开发与联调 | 集成验证、答辩演示 | 正式对外服务 |
| 部署方式 | IDEA 直跑 `RuoYiApplication` + `vite dev` | docker compose 单机 | docker compose + Nginx 反向代理 |
| 数据库 | 本机 MySQL，库 `education_system` | 容器 MySQL 8，库 `education_system_staging` | 容器 MySQL 8，库 `education_system`，每日备份 |
| 缓存 | 本机 Redis | 容器 Redis 7 | 容器 Redis 7 + 持久化卷 |
| 配置来源 | `application.yml` 默认值 | 环境变量 `RUOYI_DB_*` | 环境变量 + 密钥管理 |
| 前端 API 前缀 | `/dev-api`（Vite 代理到 8080） | `/stage-api` | `/prod-api`（Nginx 反代并剥离前缀） |
| 日志级别 | `com.ruoyi: debug` | `com.ruoyi: info` | `com.ruoyi: warn` |
| 数据 | 演示数据 | 脱敏副本 | 真实数据 |
| 访问控制 | 无 | 内网 + 基础认证 | 公网 + HTTPS |

### 2.5 部署策略：**选金丝雀发布**（理由必须写进文档）

**选它的理由**：

1. **滚动发布不具备前提。** 滚动发布依赖编排器逐个替换实例并配合就绪探针做流量摘除，本项目是 docker compose 单机部署，**没有 K8s**，编排能力不存在。
2. **蓝绿在单机上是浪费。** 蓝绿要同时维持两套完整实例，单机资源直接翻倍；而且若依用 Redis 存登录态，蓝绿两套切换时**会话不共享，用户会被强制踢下线**——对"报修到一半"的场景是真实伤害。
3. **金丝雀只需要改 Nginx 权重**，投入最小：
   ```nginx
   upstream lab_backend {
       server lab-backend-v1:8080 weight=95;   # 稳定版
       server lab-backend-v2:8080 weight=5;    # 金丝雀
   }
   ```
4. **故障影响面大，值得慢一点上。** 系统一旦不可用，实验设备就只能带病运行。先放 5% 流量观察错误率，是成本最低的风险对冲。
5. **回滚是秒级的**：把 v2 权重改成 0（或从 upstream 摘掉），无需重新部署。

**放量节奏**（写进文档）：

| 阶段 | 流量比例 | 观察时长 | 回滚触发条件 |
| :-: | :-: | :-: | --- |
| 1 | 5% | 30 分钟 | 错误率 > 1% 或出现 5xx |
| 2 | 50% | 1 小时 | 错误率 > 0.5% |
| 3 | 100% | — | 人工确认后全量 |

**同时要在文档里写明"为什么不选另两种"**——任务书要的是"并解释原因"，只说选了什么只能拿一半分。

### 2.6 README 更新

- [ ] 顶部加 CI 状态徽章
- [ ] 补一节「CI/CD」，简述流水线阶段与本地等价脚本用法

---

## 3. 边界（**不要做**）

- **不要**写 Jenkinsfile。任务书是"Jenkinsfile **或** `.yml`"二选一，交两份只会分散精力且增加不一致风险。
- **不要**伪造 CI 运行截图。本卡明确了用本地等价脚本产出真实证据。伪造在答辩提问环节极易被拆穿（老师会问"你这个流水线在哪台机器上跑的"）。
- **不要**在 `ci.yml` 里硬编码任何密码。数据库/SMTP 之类的凭据一律走 GitHub Secrets。
- **不要**把 `node_modules`、`target`、`dist`、`.workbuddy/logs` 提交进仓库。
- **不要**为了"让 CI 变绿"而删测试或加 `-DskipTests`。流水线的 Test 阶段跳过测试等于没写。

---

## 4. 提示词（复制给编码 Agent）

```
你是本项目的 DevOps 工程师。项目是若依 RuoYi-Vue 3.9.2 二次开发的
「高校实验室资产与报修管理平台」，工作目录 D:\code\Manager_system，结构：
  RuoYi-Vue-fast/   后端，Spring Boot 2.5.15 + Java 8 + Maven，产物 target/ruoyi.jar，端口 8080
  RuoYi-Vue3/       前端，Vue 3.5 + Vite 6，产物 dist/，生产 API 前缀 /prod-api
数据库 MySQL 8（库 education_system），缓存 Redis 7（若依强制依赖）。

【任务】建立 CI 流水线 + 一个本地等价执行脚本。

【第一步：初始化 git】
在 D:\code\Manager_system 执行 git init，确认根目录 .gitignore 已排除
target/、dist/、node_modules/、.workbuddy/logs/、*.log。做一次首次提交。
【重要】不要把 node_modules 提交进去，仓库会膨胀到几个 G 导致 push 失败。
如果 node_modules 已经被 add 进暂存区，用 git rm -r --cached RuoYi-Vue3/node_modules 移除。

【第二步：写 .github/workflows/ci.yml】
要求：
- 触发：push 到 main、pull_request
- 三个 job：backend、frontend、package；package 用 needs: [backend, frontend]
- backend：
    runs-on: ubuntu-latest
    actions/checkout@v4
    actions/setup-java@v4 → distribution: temurin, java-version: '8', cache: maven
    在 RuoYi-Vue-fast 目录跑 mvn -B clean test
    上传 surefire 报告为 artifact（actions/upload-artifact@v4）
- frontend：
    actions/setup-node@v4 → node-version: '22'，开启 npm/yarn 缓存
    在 RuoYi-Vue3 目录安装依赖并 npm run build:prod
    【注意】仓库里有 yarn.lock 但没有 package-lock.json。
    优先用 corepack enable && yarn install --frozen-lockfile；
    如果因为 lockfile 与 package.json 不同步而失败，改用 npm install，
    并在同一次提交里把生成的 package-lock.json 也提交上去。
    上传 dist/ 为 artifact
- package：needs 前两个 job，跑 mvn -B package -DskipTests，上传 ruoyi.jar
- 一个被注释掉的 docker job 占位，等容器化任务（T4）完成后再打开
- 【硬性】不得硬编码任何密码，凭据一律走 secrets

【第三步：写 deploy/local-pipeline.ps1】
作用：在没有真实 CI 环境的情况下，按流水线阶段顺序在本地执行并输出可截图的结果。
要求：
- PowerShell 脚本（本机 Git Bash 的 PATH 不稳定，不要写成 .sh）
- 6 个阶段，与 ci.yml 的阶段一一对应：
  1) 版本信息       打印 git rev-parse --short HEAD（无 .git 时打印 local）
  2) 后端构建与测试 powershell -NoProfile -ExecutionPolicy Bypass -File .workbuddy/tools/mvnx.ps1 -o -B clean test
                     【本机 mvn 启动脚本是坏的，必须用这个包装脚本，不要直接调 mvn】
  3) 前端构建        node RuoYi-Vue3/node_modules/vite/bin/vite.js build --mode production
  4) 打包产物        powershell -NoProfile -ExecutionPolicy Bypass -File .workbuddy/tools/mvnx.ps1 -o -B package -DskipTests
  5) 镜像构建        docker build -t lab-asset-backend:ci ./RuoYi-Vue-fast
                     如果 docker 命令不存在，打印 SKIPPED 并继续，不算失败
  6) 冒烟测试        curl -s -o NUL -w "%{http_code}" http://localhost:8080/captchaImage
                     连不上就打印 SKIPPED 并继续
- 每个阶段打印  [n/6] 阶段名 ... PASS (耗时)  或  FAIL (耗时)
- 任一阶段失败立即 exit 1
- 末尾输出汇总表格：阶段 / 结果 / 耗时（秒）
- 阶段 2 的输出里要把 "Tests run: xxx" 那一行提取出来单独高亮打印，
  因为答辩 PPT 要用这张截图

【第四步：README 更新】
- 顶部加 CI 状态徽章（占位，推到 GitHub 后自动生效）
- 加一节「CI/CD」：流水线阶段说明 + deploy/local-pipeline.ps1 的用法

【不要做的事】
- 不要写 Jenkinsfile（任务书是 Jenkinsfile 或 .yml 二选一，只交 .yml）
- 不要在 ci.yml 里硬编码密码
- 不要加 -DskipTests 到测试阶段
- 不要把 node_modules / target / dist 提交进仓库
- 不要伪造 CI 截图

【验证】
1. powershell -NoProfile -ExecutionPolicy Bypass -File deploy/local-pipeline.ps1
   要求：阶段 1-4 全部 PASS，阶段 5/6 允许 SKIPPED
2. 用 actionlint 或在线 YAML 校验器检查 ci.yml 语法（没有工具就人工逐行核对缩进）
3. git status 确认 node_modules 未被追踪

汇报格式：新增文件清单 + local-pipeline.ps1 的真实运行输出（完整）+ ci.yml 的 job 结构说明。
```

---

## 5. 执行指导与踩坑

| 坑 | 说明 |
| --- | --- |
| **`node_modules` 进仓库 = 灾难** | `RuoYi-Vue3/node_modules` 有 15919 个文件。一旦 `git add .`，仓库体积几个 G，push 会超时失败，而且很难清理。**`git add` 之前先 `git status --short | head -50` 确认。** |
| **`package-lock.json` 不存在，只有 `yarn.lock`** | 这是若依源码带来的历史状态。`npm ci` 会直接失败（它强制要求 lock 文件）。要么用 yarn，要么生成并提交 package-lock.json。**别写 `npm ci`。** |
| **JDK 版本选 8 而不是 17** | 本机离线构建用的是 JDK 17，但项目 `pom.xml` 里 `java.version=1.8`，生产运行镜像也是 Java 8。CI 里用 temurin 8 与运行时一致，且能避开 Mockito 3.9.0 在新 JDK 上的字节码增强问题。用 17 也能过，但 8 更稳。 |
| **`actions/setup-java` 的缓存对多模块无效** | 项目是单模块，`cache: maven` 直接可用，不需要额外配 `cache-dependency-path`。 |
| **GitHub Actions 的 `working-directory`** | 后端和前端在不同子目录，`run` 步骤要指定 `working-directory`，或者用 `cd`。两者选一，**不要混用**，否则路径会重复拼接。 |
| **`local-pipeline.ps1` 不要写成 `.sh`** | 本机 Git Bash 的 PATH 多次损坏（`ls`/`dirname` 都找不到），`.sh` 脚本经常跑不起来。PowerShell 脚本更可靠。但注意：**PowerShell 工具在本机的 stdout 有时不返回**，所以脚本内部要把关键输出 `Out-File -Encoding utf8` 落盘，再读文件。 |
| **`curl` 在 Windows 上是 `curl.exe`** | PowerShell 里 `curl` 是 `Invoke-WebRequest` 的别名，参数完全不同。脚本里写 `curl.exe` 避免歧义；或者干脆用 `Invoke-WebRequest`。 |
| **冒烟测试为什么用 `/captchaImage`** | 若依没有引入 actuator，没有 `/actuator/health`。`/captchaImage` 是若依的匿名接口（`@Anonymous`），且它会读 Redis——所以它同时验证了「后端起来了」和「Redis 通了」，是性价比最高的探针。注意它**需要 Redis 可用**，否则返回 500。 |
| **只跳过、不伪装** | docker 和冒烟阶段在本地大概率跑不了。脚本里要明确打印 `SKIPPED`，**不要**打印 PASS。答辩时如实说"镜像构建与冒烟在本地环境跳过，完整流水线在 CI 上执行"——诚实比好看更安全。 |

---

## 6. 验收方式

```bash
# 1. 本地流水线跑通（阶段 1-4 必须 PASS）
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/local-pipeline.ps1

# 2. 确认没把依赖提交进仓库（输出应为空）
git ls-files | grep -c node_modules

# 3. 确认 YAML 结构完整
grep -n "jobs:\|needs:\|runs-on:" .github/workflows/ci.yml

# 4. 推到远端后看 Actions 是否触发（remote 已挂：https://github.com/same-day123/Manager-system.git）
git push -u origin main
```

**通过标准**：本地流水线阶段 1–4 全 PASS；`git ls-files | grep -c node_modules` 输出 0；`ci.yml` 三个 job 结构完整。

---

## 7. 留痕要求

在 `docs/大作业/AI留痕/` 下记一份 T3 记录，**必须包含**：

- 完整提示词（本卡第 4 节原文）
- `local-pipeline.ps1` 的**完整运行输出截图**（这张图直接就是答辩 PPT 的「流水线运行结果」）
- 如果推到了真实 GitHub 并触发了 Actions，把 Actions 页面截图一起存下来
- **人工修改点**（AI 报告素材），例如：
  - AI 一开始用了 `npm ci`，你从 `yarn.lock` 的存在判断必须改成 yarn → 好素材
  - AI 想写 Jenkinsfile，你按任务书"二选一"的判断砍掉了 → 说明你在做范围控制
- 抄送一份给文档组：`ci.yml` 的 job 结构表与三环境规划表（第 2.4 节）是《CI/CD 部署方案》的现成素材
