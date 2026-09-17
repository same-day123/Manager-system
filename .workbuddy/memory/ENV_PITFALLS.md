# 本机环境 / 构建 / 工具踩坑（Manager_system）

> 从 `MEMORY.md` 迁出（原文过长导致注入被截断）。**开工前必读**，尤其涉及编译、测试、截图、git。

## 构建与 Maven
- **`mvn` 命令是坏的**（`ClassNotFoundException: plexus-classworlds.launcher`），一律用包装脚本：
  - `bash .workbuddy/tools/mvnx.sh -o -B clean test`（Git Bash 可用时）
  - `powershell -NoProfile -ExecutionPolicy Bypass -File .workbuddy/tools/mvnx.ps1 -o -B clean test`（兜底）
  两者走 JDK17 + `D:\IDEA\apache-maven-3.9.4`；**离线必须加 `-o`**。
- 给 `java.exe` 传 `-Dclassworlds.conf=` 时 PowerShell 会拆坏参数，必须用参数数组 `& $java @argList`；**PowerShell 版脚本不能加 param 块**（否则 `-o`/`-v` 被通用参数拦截）。
- **`mvn compile` 不编译 `src/test`**：搬包／改包名后必须跑 `mvn test`，否则测试里的旧包引用不暴露。
- **⚠️ 反过来：`mvn test` 不清理 `target/test-classes/`，所以它不复现 `clean test`。**（2026-09-17 实测翻车）
  验证「改了配置 / 删了测试资源文件」这类改动**必须用 `clean test`** —— 否则上一轮残留的 `target/test-classes/application.yml` 仍在 classpath 生效，结论完全错误。
  **判断生产配置有没有被读到**：看构建日志 `Application Version:` 后是 `${ruoyi.version}`（未解析）还是 `3.9.2`（已解析）。
- **⚠️ 别用 `*>` 重定向 Maven 输出**：`powershell -File mvnx.ps1 ... *> out.log` 会**挂死**（实测挂 32 分钟无输出、java 进程僵住）。改用 `2>&1 | Out-File -Encoding utf8 xxx.log`。正常耗时：`clean test` ≈1:46，`clean compile` ≈36s。
- `mvn test` 出现「WMIC.exe 被安全策略拦截」是干扰项，不影响结果。
  **根因（2026-09-17 定位）**：Surefire 2.22.2 的 `PpidChecker` 靠 **WMIC 查父进程是否存活** → 被本机安全策略拦截 → 抛 `IllegalStateException: Cannot use PPID xxxx process information. Going to use NOOP events.` → 它**自行降级为 NOOP events**、并在 `target/surefire/` 留一个 `jvmRun1.dump`。**纯噪声，测试照常全跑**。**决定不修**（修了反而引入不确定性），只写进留痕/交付文档，避免被误读成"构建坏了"。
- **⚠️ Maven 本地仓库 = `D:\IDEA\apache-maven-3.9.4\mvn_repo`，不是 `~/.m2/repository`**（从 `-X` 日志确认）。查「本机有没有某个 jar」必须查这个目录，否则结论完全错误。
  **离线可用的测试依赖**（实测）：h2 **仅 1.4.199**（pom 必须写死版本，Boot 2.5.15 会指向 1.4.200）、spring-boot-starter-test 2.5.15、spring-test 5.3.39、junit-jupiter(+params) 5.7.2、mockito-core/-junit-jupiter 3.9.0、mybatis-spring-boot-starter 2.3.1、HikariCP 4.0.3、spring-jdbc 5.3.39、hamcrest 2.2。→ H2/MockMvc/@SpringBootTest 全部齐备，**无需联网**。
- **`target/` 会被并行会话的 `mvn clean test` 清掉**（jar 凭空消失）→ `mvnx.ps1 package` 后**立刻把 jar 复制到仓库外** `D:\code\_Manager_system_build_cache\ruoyi.jar`。相对路径 `target/ruoyi.jar` 找不到（bash shim 的 `cd` 失效）→ **一律绝对路径**。
- **⚠️ 并发跑 Maven 会互撞 `target/`**，症状是**莫名编译错**：`Cannot create resource output directory: ...target/test-classes`、或 `target/` 里的 jar 突然消失。**不是代码问题**——确认无其他构建在跑后，**等 2 分钟重跑即可**（2026-09-17 实测印证）。

## H2 1.4.199（`MODE=MySQL`）函数支持边界（集成测试必读）
探针输出在 `.workbuddy/logs/h2-probe*.txt`。
- **可用**：`sysdate()`（⚠️ **只到「日」精度**，时间部分全 `00:00:00` → 依赖 `create_time` 排序/比较的断言在 H2 上不可靠）、`now()`、`limit 1`、内联 `key idx_x (col)`、`auto_increment` + `useGeneratedKeys` 回填、`left join` 别名、多参数 `concat()`、`ifnull()`、`group by`、中文读写。
- ~~**不可用**：`date_format()`、`date_sub()`~~ → **✅ 已解决（2026-09-17，别再当限制）**。
  H2 1.4.199 支持 **`create alias <名> for "<全限定类名>.<静态方法>"`** —— 在**测试侧注册同名函数**，生产 SQL **一字未改**就能跑。
  实现见 `src/test/java/.../laboratory/support/H2MySqlDateFuncs.java`（函数体）+ `H2MySqlCompat.java`（注册器，`installIfNeeded` 挂 `AbstractLabIntegrationTest` 的 `@BeforeEach`）。三个实测细节：
  1. **`FOR` 子句必须用双引号**标识符（`for "类.方法"`；单引号报 `Syntax error ... expected "identifier"`）；
  2. **`date_sub` 第二参数必须声明成 `String`** —— H2 把 `interval 6 day` 转成 `INTERVAL '6' DAY` 字符串传入；声明 `int` → `Data conversion error`，声明 `Object` → `Hexadecimal string contains non-hex character`；
  3. 内联 `as $$…$$` 形式**也可行**，但函数体里的 `;` 会被 Spring 的 SQL 脚本切分器截断 → 取 `for "类.方法"`。
  **设计铁律**：垫片只实现生产真正用到的语义（DAY 单位 + 9 个格式符），**不认识就抛异常** —— 宁可红灯，不产假绿灯。**必须 `if not exists` 幂等**（内存库 `DB_CLOSE_DELAY=-1` 被整个测试 JVM 共用）。
  → 看板已由 `LabDashboardIntegrationTest`（DB-01~DB-07）覆盖 **8 条 mapper 查询**，**D-19 已收口**；**绝不许改生产 mapper XML** 这条规矩依然有效。
- 集成基建：`src/test/java/.../laboratory/integration/`（骨架 `AbstractLabIntegrationTest`）+ `src/test/resources/{application.yml,application-integration.yml,sql/lab-schema-h2.sql}` + `support/LabIntegrationTestApplication`（最小上下文，排 Redis×2 与 Security，**不挂 `RuoYiApplication`**）。注意 **Druid 与 Quartz 自动配置仍会加载**（回落 H2／RAMJobStore，无害）。
- **⚠️ `laboratory_schema.sql` 是 MySQL 方言**（engine/collate）→ **H2 解析不了**，集成测试需单独的 H2 DDL。

## 前端构建
- **`vite build` 必须在 `RuoYi-Vue3/` 目录内执行**（vite root 取当前工作目录；在仓库根跑 12ms 报 `Could not resolve entry module "index.html"` —— **调用姿势问题，不是代码问题**）。
- 前端包管理用 **yarn**（有 `yarn.lock`、无 `package-lock.json`）。`build:prod = vite build`，`build:stage = vite build --mode staging`。

## 零依赖浏览器截图 / 量化探针（⭐ 高频复用）
本机**没有** playwright / puppeteer，AGENTS 禁止新增依赖，**离线也装不上**。
方案：**Chrome `--headless=new --remote-debugging-port=9222` + Node 22 自带 `WebSocket`/`fetch` 直驱 CDP**，自写脚本截图与量化，**全程零 npm 安装**。
脚本在 `.workbuddy/tools/`：`cdp_shot.mjs`（单页截图）/ `ui_shots.mjs`（批量取景）/ `ui_probe*.mjs`（量化探针）/ `ui_login.mjs` / `ui_captcha.mjs`。
- **验证码是算术题**（`sys.account.captchaEnabled=true`）：低分辨率读不出 → 图放大到 900/1600px 再读（出现过 `1-1=0`、`5*6=30`）。
- **初始密码弹窗**（`store/modules/user.js` 的 `isDefaultModifyPwd`）会跳个人中心 → 脚本点「取消」关掉。
- **两个自欺陷阱**：① `querySelector('.el-overlay-dialog')` 会**抓到隐藏弹窗组**（实验室页 `el-dialog` 都 `append-to-body`）→ 必须走「**可见** `.el-dialog` 的 `closest()` 祖先链」；② 解析 SFC 列宽用 `indexOf('</template>')` 会被**内层插槽截断** → 截到 `<script` 前，并排除弹窗内小表。**修正前后结论完全相反，别省这一步。**
- **量「真实数据里不可达的状态」的通用手法（不必改库）**：某些渲染分支现有数据构造不出来（如「操作列同时 3 个按钮」需一条 `status=0` 工单）。**不要往演示库插数据**（污染答辩素材）→ **在浏览器内存里造**：① 从表格行 `__vueParentComponent` 沿 `parent` 链找**页面组件**（判据：`setupState` 有目标 ref 名）；② `push` 一行合成数据；③ 量完 `splice` 移除并**复核合成行消失**。示例 `.workbuddy/tools/ui_probe_v4.mjs`。**前提是 dev server**（生产构建拿不到 `__vueParentComponent`）。
- **T6 列宽口径（易误判）**：报修表格列宽 **1535px 是 SFC 声明值**，浏览器实测 **1678px**（=容器宽：5 列 `min-width` + `table-layout=fixed` 摊分）——**属正常**；收敛判据是「1535 < 1920 屏内容区 1678 → 不横滚」，别当没收敛。

## 联调环境端口坑（2026-09-17 实测）
- **80 端口被 Steam++.Accelerator 占用** → dev server 改 `--port 5173`。
- **后端会绑到 6716 并启动失败**（6716 被 WorkBuddy 自身进程占）→ 启动参数显式加 `--server.port=8080`（**命令行参数优先级最高**）。vite 的 `/dev-api` 代理目标写死 `localhost:8080`，**后端必须落这个端口**。

## Shell / 工具坑
- **Git Bash 的 PATH 反复损坏**（`ls`/`dirname`/`head`/`find` 全 command not found）→ 改用 Glob/Grep/Read 专用工具，或 PowerShell `Out-File -Encoding utf8` 落盘再 Read（**PowerShell stdout 也常不返回**，务必落盘）。
- 本机删除走「安全删除（回收站）」通道且常失败 → `dist/`／`target/` 可能残留，**打源码 ZIP 前人工确认已排除**。
- 构建日志统一放 `.workbuddy/logs/`。
- **PowerShell 抓不到 `git push` 输出**（退出 128 但日志空）：`2>&1 | Out-File` 会在原生命令写 stderr 时抛 `RemoteException` 中断管道。可行写法：`$out = & git -c credential.helper= push --progress $url main:main 2>&1` 再 `Set-Content` 落盘，并设 `GIT_TERMINAL_PROMPT=0` / `GCM_INTERACTIVE=never`。`cmd /c` 被 PowerShell 工具安全策略拦截，不可用。

## Git 仓库坑（⚠️ 高价值）
- **⚠️ 嵌套仓库（gitlink 160000）大坑**：`RuoYi-Vue-fast/`、`RuoYi-Vue3/` 原各带一个**上游 clone 来的内层 `.git`** → 外层 `git add -A` 会把整个目录记成 gitlink、**静默跳过全部源码**（首次提交只进 38 个文件、`git status` 却看着干净）。修法：把内层 `.git` **移动**到 `D:\code\_Manager_system_git_backup\`（可逆备份，未删），`git rm -r --cached` 掉 gitlink 再 `git add -A`。**别往这两个目录里 `git init`**。排查命令：`git ls-files -s | grep 160000`。
- 首次推送已完成（`main = 57c81b9`，699 文件 / 82212 行）。每次 push 前自查：勿带 `.workbuddy/logs`、`target/`、`dist/`、`node_modules/`、密钥。
- **⚠️ 阿辉已吊销那个 PAT —— 任何角色都不许 `git push`**（本地 `commit` 后交阿辉/PM 用新凭据推）。
