# Manager_system 项目长期记忆

> **公共协作入口是仓库根目录 `AGENTS.md`** —— 项目简述、6 步工作流、角色分工、进度台账、偏移检查全在那里。**接手项目先读它。**
> 本文件只保留**代码层面的长期约定**与**本机环境踩坑**，不重复台账、任务卡与任务书指标（那些看 `AGENTS.md` 与 `docs/大作业/`）。
>
> **Claw 在本项目担六重角色**：① PM／产品负责人　② 前端 UI 负责人　③ 编码负责人　④ 答辩材料与视觉设计负责人　⑤ 文档负责（交付物 1~5）　⑥ 测试负责（T1/T2）。
> 职责边界（做什么／明确不做什么）唯一权威 = `AGENTS.md` 第 **0.5 节**；开工前只看第 **5.1 节**自己那一行（任务卡／完成判据／**可写目录硬边界**）。
> 六个并行会话即角色名：`产品经理`／`代码负责`／`测试负责`／`文档负责`／`UI设计`／`PPT和设计`；角色创建提示词在 `docs/大作业/角色prompt/`。
> 决策权（选题／范围／时间节点）归**阿辉**。注意区分：**前端 UI 负责人**做产品界面（`views/laboratory/*`），**视觉设计负责人**做答辩材料本身（PPT／海报），两者共用同一套色板。

## 项目与栈
- 定位：基于 **若依 RuoYi-Vue 3.9.2**（前后端分离版）二次开发的**实验室资产台账 + 故障报修闭环**系统；自定义业务只在「实验室管理」模块。
- 后端 `RuoYi-Vue-fast/`：Spring Boot 2.5.15／Java 8／MyBatis／Druid／Redis／Quartz／Security+JWT／Swagger3／POI／Velocity；单体 jar，端口 8080。
- 前端 `RuoYi-Vue3/`：Vue 3.5／Vite 6／Element Plus 2.13／Pinia／ECharts 5.6；dev 端口 80，`/dev-api` 代理到 8080。
- DB：MySQL 8，库 `education_system`（`application-druid.yml`，默认 root/123456，可被 `RUOYI_DB_*` 覆盖）；Redis 必启。
- 业务表：`lab_room`、`lab_asset`、`lab_repair`、`lab_repair_record`、`lab_asset_record`；字典 `lab_asset_type`／`lab_asset_status`／`lab_fault_level`／`lab_repair_status`。
- 演示账号（密码均 123456）：labadmin／asset01／repair01／room01／student1／viewer01。

## 交付物署名与封面（2026-09-17 阿辉确认；五份 PDF + PPT 通用）
- 项目名称：高校实验室资产与报修管理平台 ｜ **团队编号：第 6 组**
- 成员：王旻辉、巴力江·托力肯别克、廖煌、迪丽热巴·阿布都西克尔 ｜ 班级：软件工程 2023-2 ｜ 授课教师：谭清萱
- 学号：**2311231056**（阿辉只给了一个，其余三人待补）
- **仍待填**：二级学院／专业、封面单数「姓名／学号」的填写口径、编写人／审核人、文档日期
- **《AI 辅助开发实践报告》工具清单**（阿辉原话）：`deepseekV4flash，GLM5.3flash，VScode，workbuddy`
- **五份文档写作顺序**（已批准）：`1 需求 → 2 架构 → 5 AI 报告 → 3 测试 → 4 CI/CD`
- 明细见 `docs/大作业/AI留痕/D1-2026-09-17.md` 第 10 节。

## 代码约定
1. 自定义代码只在 `com.ruoyi.project.laboratory`：`controller`／`domain`／`mapper`＋`resources/mybatis/laboratory/*.xml`／`service`+`service/impl`／`constant/LabConstants`／`util/{LabRoleUtils,LabStatusUtils,LabSecurityUtils,QrCodeUtils}`。
   若依配置用通配符（`typeAliasesPackage: com.ruoyi.project.**.domain`、`mapperLocations: classpath*:mybatis/**/*Mapper.xml`），**新增子包无需改配置**。
2. **角色口径只能改两处且必须同步**：后端 `LabRoleUtils`（`canHandleRepair` = admin/teacher/lab_manager/repair_engineer；`canViewAll` = 上述 4 个 + asset_keeper/lab_viewer）、前端 `utils/labPermission.js`；涉及「可看全部数据」的角色还要同步 `sql/laboratory_permission_fix.sql` 末尾清单——**共三处，D-10 就是漏了第三处**。
3. 接口前缀 `/laboratory/{room|asset|repair|dashboard}`，权限标识 `laboratory:{模块}:{操作}`；看板独立权限 `laboratory:dashboard:view`。
4. 报修状态机（`LabRepairServiceImpl.validateStatusChange`）：`0 待审核 →1 待维修 →2 维修中 →3 已完成`，`0 →4 已拒绝`；非法流转抛 `ServiceException`；传 `null` 或同状态**直接放行**。
5. 资产联动：提交报修 → 资产 `2 维修中`；完成／拒绝／删除待审核单 → 资产回 `0 正常`。报修资产**只在新增时选定**，落库后任何人不可更换。
6. 删除一律**逻辑删除** `del_flag='2'`，列表查询必须带 `del_flag='0'`。
7. SQL 在 `RuoYi-Vue-fast/sql/`，按脚本头部 `[N/6]` **顺序执行**：`ry_20260417` → `laboratory_menu_role` → `laboratory_demo_seed` → `laboratory_user_seed` → `laboratory_upgrade` → `laboratory_permission_fix`；全部可重复执行。`laboratory_cleanup.sql` 是**界面精简**脚本（非清数据），按需跑。
8. **`sql/` 缺 `lab_room`／`lab_asset`／`lab_repair` 三张主表的建表语句**（偏移 D-02，T0 卡修）——在此之前仓库**无法从零复现**；表字段名可从对应 Mapper XML 的 `resultMap` 还原，但类型／长度／索引仍需 T0 的 DDL。
9. 安全事实（写非功能需求可引）：密码用 `BCryptPasswordEncoder` 存哈希；`token.header=Authorization`、`token.expireTime=30`（分钟）；`token.secret` 默认弱值 `abcdefghijklmnopqrstuvwxyz`，须用 `RUOYI_TOKEN_SECRET` 覆盖。

## 本机构建与踩坑
- **`mvn` 命令是坏的**（`ClassNotFoundException: plexus-classworlds.launcher`），用包装脚本：
  - `bash .workbuddy/tools/mvnx.sh -o -B clean test`（Git Bash 可用时）
  - `powershell -NoProfile -ExecutionPolicy Bypass -File .workbuddy/tools/mvnx.ps1 -o -B clean test`（兜底）
  两者走 JDK17 + `D:\IDEA\apache-maven-3.9.4`；**离线必须加 `-o`**。
- 给 `java.exe` 传 `-Dclassworlds.conf=` 时 PowerShell 会拆坏参数，必须用参数数组 `& $java @argList`；**PowerShell 版脚本不能加 param 块**（否则 `-o`/`-v` 被通用参数拦截）。
- **`mvn compile` 不编译 `src/test`**：搬包／改包名后必须跑 `mvn test`，否则测试里的旧包引用不会暴露。
- **`vite build` 必须在 `RuoYi-Vue3/` 目录内执行**（vite root 取当前工作目录；在仓库根跑会 12ms 报 `Could not resolve entry module "index.html"`——**是调用姿势问题，不是代码问题**）。
- **⚠️ Maven 本地仓库不是 `~/.m2/repository`，而是 `D:\IDEA\apache-maven-3.9.4\mvn_repo`**（2026-09-17 实测，从 `-X` 日志里的 jar 路径确认）。查「本机有没有某个 jar」必须查这个目录；`~/.m2\repository` 里另有一批旧版本 jar，拿它当依据会得出完全错误的结论。
  新增依赖前先确认该仓库已有 jar。**离线可用的测试相关版本**（2026-09-17 实测）：h2 **仅 1.4.199**（pom 必须写死版本，Boot 2.5.15 会指向 1.4.200）、spring-boot-starter-test/-test/-test-autoconfigure 2.5.15、spring-test 5.3.39、junit-jupiter(+params) 5.7.2、mockito-core/-junit-jupiter 3.9.0、mybatis-spring-boot-starter(+autoconfigure) 2.3.1、HikariCP 4.0.3、spring-jdbc 5.3.39、hamcrest 2.2。→ **T2 所需的 H2/MockMvc/@SpringBootTest 依赖本机全部齐备，无需联网。**
- `mvn test` 出现「WMIC.exe 被安全策略拦截」是干扰项，不影响结果。
- **⚠️ 别用 `*>` 重定向 Maven 输出**：`powershell -File mvnx.ps1 ... *> out.log` 会**挂死**（2026-09-17 实测挂了 32 分钟无任何输出、java 进程僵住）。改用 `2>&1 | Out-File -Encoding utf8 xxx.log`，正常（`clean test` 全程约 1 分 46 秒，`clean compile` 约 36 秒）。
- **Git Bash 的 PATH 反复损坏**（`ls`/`dirname`/`head`/`find` 全部 command not found）→ 改用 Glob/Grep/Read 专用工具，或 PowerShell 输出 `Out-File -Encoding utf8` 落盘再 Read（PowerShell stdout 也常不返回）。
- 本机删除走「安全删除（回收站）」通道且常失败 → `dist/`／`target/` 可能残留，**打源码 ZIP 前人工确认已排除**。
- 构建日志统一放 `.workbuddy/logs/`。
- 远程仓库 `https://github.com/same-day123/Manager-system.git`：本地 `main` + `origin` 已挂；**首次 commit/push 须先经阿辉确认提交范围**（勿带 `.workbuddy/logs`、`target/`、`dist/`、密钥）。

## 当前状态锚点（细节看 `AGENTS.md` 第 6／7 节，不在此展开）
- **测试基线：单测 7 个全绿**（`LabAssetServiceImplTest` 1／`LabRepairServiceImplTest` 5／`LabRoomServiceImplTest` 1），**集成测试 0 个**；`mvnx.ps1 -o -B clean test` → `Tests run: 7, Failures: 0`，BUILD SUCCESS（1:39）。
- 硬指标缺口对应任务卡：**T0** 建表脚本／**T1** 单测→29／**T2** 集成→7／**T3** CI+金丝雀／**T4** Dockerfile／**T5** 设计模式；**D1** 五份 PDF（70 分）归「文档负责」。
- 已记录**容忍**的偏移（答辩要主动说明）：**D-01** 资产二维码是相对路径、手机扫码打不开；**D-06** 前后端状态机是两张手写表；**D-08** `token.secret` 弱密钥。
- 文档侧素材依赖：测试数字看「测试负责」，流水线／Dockerfile 看「代码负责」，设计模式类图看 T5，**T0/T5 未完成时架构文档相应章节只能留骨架**。
- **2026-09-17「文档负责」新登记两条未修问题**（详见 `AGENTS.md` 7.3）：**D-16** `deploy/local-pipeline.ps1` **不存在**（该文件原被 AGENTS 第 8 节当作流水线截图来源，已在第 8 节就近修正；脚本归 T3 产出）；**D-17** `README.md`「已知待办」仍写「未初始化 Git 仓库」，与 D-09 已解除矛盾 → **C4（README→事实）检查不通过**，README 不在「文档负责」可写目录，交「代码负责」或「产品经理」收口。
