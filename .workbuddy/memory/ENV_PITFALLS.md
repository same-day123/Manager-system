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
- `mvn test` 出现「WMIC.exe 被安全策略拦截」是干扰项，**多数情况下**不影响结果。
  **根因（2026-09-17 定位）**：Surefire 2.22.2 的 `PpidChecker` 靠 **WMIC 查父进程是否存活** → 被本机安全策略拦截 → 抛 `IllegalStateException: Cannot use PPID xxxx process information. Going to use NOOP events.` → 它**自行降级为 NOOP events**、并在 `target/surefire/` 留一个 `jvmRun1.dump`。**决定不修**（修了反而引入不确定性），只写进留痕/交付文档。
- **⚠️ 但同一根因会「概率性掐掉整棵命令树」（T3 定位，已登记偏移 D-26）**：拦截事件抛 `PROGRAM BLOCKED BY SECURITY POLICY`，**连带把正在运行的整条命令链杀掉**。实测同一份 `deploy/local-pipeline.ps1` 连跑 7 次，第 3/5/6 次完整跑到汇总表，第 4 次**死在阶段 3（vite 构建）中途且无任何报错输出**（日志只到 `transforming...`）。
  - **别误判成「vite/构建挂了」** —— 单独跑 vite `exit=0 / 67.9s` 完全正常；线程栈会指到 `LabSecurityUtils`/`Surefire` 一侧。
  - **处置：① 跑不通就重跑；② 关键输出用 `-l <文件>` 原生落盘**（进程被杀也不丢已产出内容，`BUILD SUCCESS` 照样在文件里）；③ 流水线脚本已内置 Maven 阶段最多 3 次自愈重试。**CI（GitHub Actions / Linux）无此问题**。
  - Surefire 2.22.2 **没有**可关闭 `PpidChecker` 的属性（`ProcessCheckerType` 只在 3.x 有），**不为绕沙箱升 surefire**。
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

## 验证纯重构：两层证据怎么做（2026-09-17 T5 用例，**可复用**）

**第一层 静态（字面量级逐字节比对）** —— 证明「文案是原样搬家，不是重写」：
- 基线取 `git show HEAD:<file>`（改造前），判据是**字符串字面量内容精确相等**。
- **⚠️ 千万别用子串包含判断**：`"非法的报修状态流转"`（异常文案）含子串 `状态流转`，会报**假阳性**。T5 第一版脚本就这样误报 2 项，人工核查后改判据才成立。
- **按改前形态分类判**（形态不同判据不同）：A 迁移字面量（改前就是独立字面量）／B 拼接→模板（改前是若干拼接片段，整条模板改前**不存在**，不能要求"改前存在"）／C 派生值（改前住在别的方法的 `return` 里，基线要取那个方法体）。工具 `.workbuddy/tools/t5_literal_proof.py`。
- **中文全角标点要查码点**：`“`=U+201C、`”`=U+201D、`；`=U+FF1B。用 `indexOf('\u201C')` 断言位置，顺带断言 `indexOf('"') == -1`。

**第二层 动态（脱离 Spring 的 Java 探针）** —— 证明「搬家后的文案运行期真能渲染出原样」：
- 为什么不靠测试就够：单元测试用 Mockito 只验「Mapper 被调用」，**文案一个都没渲染**；集成测试只覆盖到部分分支。
- 探针放 `.workbuddy/tools/`（**别放 `src/test`** —— 那是「测试负责」的领地，越界）。
- **本机没有 `maven-dependency-plugin`（离线装不上）** → **不能**用 `dependency:build-classpath`，要手拼 classpath。`target/classes` + 探针输出目录 + 下面这些 jar（都实测可用，取自 `C:\Users\王旻辉\.m2\repository`）：
  `commons-lang3-3.12.0`、`spring-security-core-5.7.14`、`spring-security-crypto-5.7.14`、`spring-core/beans/context/web/expression-5.3.39`、`jackson-annotations/-core/-databind-2.13.3`、`slf4j-api-1.7.36`、`javax.servlet-api-3.1.0`、`fastjson2-2.0.62`。
- 命令（PowerShell，`;` 分隔 classpath）：
  `javac -encoding UTF-8 -cp RuoYi-Vue-fast\target\classes -d .workbuddy\logs\t5probe .workbuddy\tools\T5EventProbe.java`
  `java -cp "RuoYi-Vue-fast\target\classes;.workbuddy\logs\t5probe;<上面那些 jar>" T5EventProbe <UTF-8 结果文件>`
- 探针**自己用 UTF-8 写结果文件**（别靠控制台，会乱码），返回码 0/1 表通过与否。

**第三层 运行期实据（最省事、最有说服力）**：集成测试跑真实库，**MyBatis DEBUG 日志会把 SQL 参数逐字打出来** —— 枚举渲染的文案直接可见。查法见下条（日志是 GBK）。

## ⚠️ `mvn -l <file>` 写出的日志是 **GBK**，不是 UTF-8
- 现象：用 ripgrep/Grep 工具搜日志里的中文**一条都搜不到**（文件被当二进制或编码不匹配），但 `Get-Content` 能正常显示。
- 正确读法：`Get-Content <log> -Encoding Default`（PS 5.1 的 ANSI=GBK）后再 `-match`/`.Contains()`；要留档就用 `[System.IO.File]::WriteAllText($p, $s, [Text.UTF8Encoding]::new($false))` 转写成 UTF-8。
- 好处：`-l` 是 Maven **原生**落盘（不套管道），**进程被杀也不丢内容** —— 这正是 D-26 场景下唯一能拿到 `BUILD SUCCESS` 的办法。


## 零依赖 Markdown → PDF（交付文档用，2026-09-17 打通）
本机**没有 pandoc / LaTeX / wkhtmltopdf**，离线装不上。路子与截图同一套：
`Markdown →(自写 GFM 渲染器)→ 打印 HTML →(Chrome 无头 + CDP Page.printToPDF)→ PDF`，脚本全在 `.workbuddy/tools/md2pdf/`（`md2pdf.mjs` / `pdfinfo.mjs` / `build.mjs` / `build.ps1` / `pagemap.mjs`）。
- **`Page.printToPDF` 的内容区尺寸可算**：`(8.27-0.65*2)×96 = 669px` 宽、`(11.69-0.71-0.67)×96 = 990px` 高 —— 用它做 `Emulation.setDeviceMetricsOverride` 就能**离线量页数**（`pagemap.mjs`）。
- **⚠️ 页数会「多出 1 页」**：打印 CSS 里 `page-break-inside: avoid` 会让**放不下的表格整张跳页**，留白累计可达 1 页。`ceil(scrollHeight/990)` 只是**下界**，判据必须留余量；**页数超限先量再删，别盲删**。
- **收紧 CSS 是双向刀**：为压一份（19→13 页）收紧后，另一份会同时掉页（10→8，贴住下限）→ **上限下限一起盯**。
- **字体嵌入与可复制的判据**（`pdfinfo.mjs`，不装依赖读 PDF 字节）：`/FontFile2` 数 > 0 = 字体已嵌入（中文不丢字）；`/ToUnicode` 数 > 0 = 文字可选中可复制。本机可用中文族：SimSun / SimHei / NSimSun（**无 Microsoft YaHei**）。
- **CDP 的 `setTimeout` 必须 `clearTimeout`**（收到响应即清 + `timer.unref?.()`），否则事件循环被吊住、**进程 120s 不退出**，看起来像「被工具超时杀掉」；退出时 `taskkill /PID <pid> /T /F` 收拾 Chrome 进程树，否则留孤儿进程。
- headless Chrome **偶发启动失败**（尤其连着起多个实例）→ **整批只起一次浏览器复用 session**，并给启动加换端口 + 换临时目录的重试。

## 联调环境端口坑（2026-09-17 实测）
- **80 端口被 Steam++.Accelerator 占用** → dev server 改 `--port 5173`。
- **后端会绑到 6716 并启动失败**（6716 被 WorkBuddy 自身进程占）→ 启动参数显式加 `--server.port=8080`（**命令行参数优先级最高**）。vite 的 `/dev-api` 代理目标写死 `localhost:8080`，**后端必须落这个端口**。

## Shell / 工具坑
- **Git Bash 的 PATH 反复损坏**（`ls`/`dirname`/`head`/`find` 全 command not found）→ 改用 Glob/Grep/Read 专用工具，或 PowerShell `Out-File -Encoding utf8` 落盘再 Read（**PowerShell stdout 也常不返回**，务必落盘）。
- **⚠️ PowerShell 会吞掉 `node` 的 stdout 与 stderr**（2026-09-17 实测，代价：排查 4 轮）：`& node xxx.mjs` 在工具里只回一句「Command completed with exit code 0」，**报错一个字都看不到**；`Start-Process -RedirectStandardOutput/-RedirectStandardError` 拿到的**两个文件都是空的**。
  → **排查本机脚本问题的第一件事：让脚本自己把结果写进 UTF-8 文件**（`say()` 双写 console + 文件，并把 `main()` 包进 `try/catch/finally`，异常也写进去）。本次就是靠这个才拿到真正的 `ReferenceError: ROOT is not defined`。
  → 拿不到的应急手段：`$x = (& node ... 2>&1 | Out-String)` 再用 `[System.IO.File]::WriteAllText($p, $x, (New-Object System.Text.UTF8Encoding($false)))` 落盘。
- **`Edit` 工具偶发「报成功但未落盘」**：连续多次 Edit 同一文件时，出现过两次「Successfully edited」但文件内容未变（其中一次是缺失的 `import`，直接导致脚本 `ReferenceError`）→ **多步 Edit 后务必 Read 复核关键行**。
- 本机删除走「安全删除（回收站）」通道且常失败 → `dist/`／`target/` 可能残留，**打源码 ZIP 前人工确认已排除**。
- 构建日志统一放 `.workbuddy/logs/`。
- **PowerShell 抓不到 `git push` 输出**（退出 128 但日志空）：`2>&1 | Out-File` 会在原生命令写 stderr 时抛 `RemoteException` 中断管道。可行写法：`$out = & git -c credential.helper= push --progress $url main:main 2>&1` 再 `Set-Content` 落盘，并设 `GIT_TERMINAL_PROMPT=0` / `GCM_INTERACTIVE=never`。`cmd /c` 被 PowerShell 工具安全策略拦截，不可用。

## Git 仓库坑（⚠️ 高价值）
- **⚠️ 嵌套仓库（gitlink 160000）大坑**：`RuoYi-Vue-fast/`、`RuoYi-Vue3/` 原各带一个**上游 clone 来的内层 `.git`** → 外层 `git add -A` 会把整个目录记成 gitlink、**静默跳过全部源码**（首次提交只进 38 个文件、`git status` 却看着干净）。修法：把内层 `.git` **移动**到 `D:\code\_Manager_system_git_backup\`（可逆备份，未删），`git rm -r --cached` 掉 gitlink 再 `git add -A`。**别往这两个目录里 `git init`**。排查命令：`git ls-files -s | grep 160000`。
- 首次推送已完成（`main = 57c81b9`，699 文件 / 82212 行）。每次 push 前自查：勿带 `.workbuddy/logs`、`target/`、`dist/`、`node_modules/`、密钥。
- **⚠️ 阿辉已吊销那个 PAT —— 任何角色都不许 `git push`**（本地 `commit` 后交阿辉/PM 用新凭据推）。
