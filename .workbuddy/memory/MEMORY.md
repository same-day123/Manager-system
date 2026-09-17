# Manager_system · 项目长期记忆

> **公共入口 = 仓库根 `AGENTS.md`**（项目简述／六步工作流／角色分工／进度台账／偏移检查）——接手先读它。
> **本机环境／构建／截图／git 踩坑 = `ENV_PITFALLS.md`**（同级，开工前必读）。
> 本文件只留**代码约定**与**状态锚点**。Claw 担六角色：PM／前端UI／编码负责／答辩材料与视觉设计／文档负责／测试负责；边界唯一权威 = `AGENTS.md` 第 **0.5 节**，开工先看第 **5.1 节**自己那一行。决策权归**阿辉**。

## 栈与数据
- 若依 RuoYi-Vue 3.9.2 二次开发：**实验室资产台账 + 故障报修闭环**；自定义码只在「实验室管理」。
- 后端 `RuoYi-Vue-fast/`：Boot 2.5.15／Java 8／MyBatis／Druid／Redis／Quartz／Security+JWT；端口 8080。
- 前端 `RuoYi-Vue3/`：Vue 3.5／Vite 6／Element Plus 2.13／Pinia／ECharts 5.6；dev 端口 80（被占则 5173），`/dev-api`→8080。
- MySQL 8 库 `education_system`（root/123456）+ Redis 必启。表 `lab_room`／`lab_asset`／`lab_repair`／`lab_repair_record`／`lab_asset_record`（+4 张 `lab_*` 字典）。
- 演示账号 labadmin／asset01／repair01／room01／student1／viewer01。**真实口令 `admin123`；文档处处写的 `123456` 是错的**（偏移 **D-20**）。
- 远程 `github.com/same-day123/Manager-system`（本地 main + origin 已挂）。**PAT 已吊销：任何角色只 commit、禁止 push**。

## 代码约定
1. 自定义码只在 `com.ruoyi.project.laboratory`（controller／domain／mapper + `resources/mybatis/laboratory/*.xml`／service+impl／constant（`LabConstants` + `LabAssetEvent`/`LabRepairEvent`/`LabRecordFactory`）／util）。若依配置走通配符，**新增子包无需改配置**。
2. **角色口径只能改三处且必须同步**：后端 `LabRoleUtils`、前端 `utils/labPermission.js`、`sql/laboratory_permission_fix.sql` 末尾清单（**D-10 = 漏了第三处**）。`canHandleRepair`=admin/teacher/lab_manager/repair_engineer；`canViewAll`=上述 4 个 + asset_keeper/lab_viewer。
3. 接口前缀 `/laboratory/{room|asset|repair|dashboard}`；权限标识 `laboratory:{模块}:{操作}`。
4. 报修状态机（`LabRepairServiceImpl.validateStatusChange`）：`0 待审核→1 待维修→2 维修中→3 已完成`，`0→4 已拒绝`；非法流转抛 `ServiceException`；传 `null` 或同状态**放行**。
5. 资产联动：提交报修→资产 `2 维修中`；完成／拒绝／删除待审核单→资产回 `0 正常`。报修资产**只在新增时选定**，落库后不可更换。
6. 删除一律**逻辑删除** `del_flag='2'`；列表查询必须带 `del_flag='0'`。
7. SQL 在 `RuoYi-Vue-fast/sql/`，按头部 `[N/7]` 顺序执行：`[1/7] laboratory_schema`→`[2/7] ry_20260417`→`[3/7] laboratory_menu_role`→`[4/7] laboratory_demo_seed`→`[5/7] laboratory_user_seed`→`[6/7] laboratory_upgrade`→`[7/7] laboratory_permission_fix`；**全部可重复执行**。不参与顺序的按需脚本：`laboratory_index_fix.sql`（幂等迁移，删与逻辑删除冲突的唯一索引）、`laboratory_cleanup.sql`（界面精简，非清数据）。
8. 建表硬规矩：① 字段类型/长度**以线上库为准**；② 业务编号（`asset_code`／`repair_code`／`room_no`）**只建普通索引、不建唯一索引**——系统一律逻辑删除、行不消失，唯一索引会与「编号可重用」（校验带 `del_flag='0'`）冲突 → 「删了资产就建不回同编号、接口 500」；唯一性由 `checkAssetCodeUnique`／`checkRoomNoUnique` 承担。字符集显式 `utf8mb4`／`utf8mb4_general_ci`。
9. 安全（写非功能需求可引）：密码 `BCryptPasswordEncoder` 存哈希；`token.header=Authorization`；`token.expireTime=30` 分钟；`token.secret` 默认弱值须用 `RUOYI_TOKEN_SECRET` 覆盖（**D-08**）。
10. **履历写入单点（T5 简单工厂，别再绕过它）**：资产／报修履历的构造一律走 `LabRecordFactory.assetRecord(...)`／`repairRecord(...)`，调用方**只声明事件**。动作名与文案模板只在 `LabAssetEvent`(8)／`LabRepairEvent`(5) 里；**新增履历事件只加枚举常量**，两个 Service 零改动。文案含**中文全角引号 `“”`(U+201C/U+201D) 与全角分号 `；`(U+FF1B)**，改文案务必整段复制、别手打。「资产状态码→动作名」唯一来源 = `LabAssetEvent.ofAssetStatus`（`LabStatusUtils.assetRecordType` 已退化为它的封装）。
11. **卡的示例代码 ≠ 事实**：任务卡是 AI 写的，示例可能与真实代码不符（**T5 卡的 `TRANSFER` 模板就带错了占位符**）。动手前必须 `git show HEAD:` 或读盘核对真实现状，**"行为逐字不变"这类红线优先于卡的示例**。**纯重构的验收要两层证据**：静态（字面量级逐字节比对，别用子串——`"非法的报修状态流转"` 会假阳性）+ 动态（脱离 Spring 的探针／运行期 SQL 参数）。
12. **容器化（T4，2026-09-17）**：`docker compose up -d` 起全套（mysql／redis／lab-backend／lab-frontend）。**三处配置覆盖全部走环境变量、`application*.yml` 一字未改**——`RUOYI_PROFILE`（盖写死的 `D:/ruoyi/uploadPath`）、**`SPRING_REDIS_HOST`/`SPRING_REDIS_PORT`**（盖**写死的** `localhost:6379`，**任务卡的环境变量清单漏了这项 → D-27**）、`RUOYI_DB_*`。**SQL 初始化只走 `deploy/mysql-init.sh` 的显式顺序**（官方镜像按**文件名字母序**执行 initdb，直挂 `.sql` 会让每个脚本都报"表不存在"）；**`quartz.sql` 不进顺序**——`framework/config/ScheduleConfig.java` **整类被注释**且 `resources/` 下无 `quartz.properties` → 走 Boot 默认 `RAMJobStore`，不需要 `QRTZ_*` 表。**nginx**：`proxy_pass http://lab-backend:8080/` **末尾斜杠删不得**（漏了所有接口 404）；已补 `client_max_body_size 20m`（nginx 默认 1m，故障照片会被拦成 413 且后端日志无痕）。改 `.gitignore` 时**绝不能写 `.env.*`**——会连 `RuoYi-Vue3/.env.production` 一起忽略掉，前端构建的 API 前缀就错了。生产叠 `docker-compose.prod.yml`（`!override` 去端口暴露——Compose 对 `ports` 默认是**拼接**、删不掉）。
13. **无 Docker 时怎么验容器化（可复用，同 T5 的两层思路）**：① 静态断言 `.workbuddy/tools/t4_verify.py`（YAML 可解析／**SQL 顺序三方互校**：脚本 `for` 列表 ↔ 磁盘文件 ↔ 各脚本自带 `[N/7]` 标记／nginx 前缀与上传上限／Dockerfile 非 root 与健康检查／`.dockerignore` 正反两面／CI job 结构）；② **环境变量绑定实证** `.workbuddy/tools/t4_env_binding_check.py` + `T4EnvBindingProbe.java`——加载**真实**的 `application*.yml`，用 Spring **真实属性源机制**（配置文件 `addLast` = 最低优先级，与 Boot 一致）跑**两套不同取值对照**：`raw:` 视图证明**配置没被改过**、`eff:` 视图证明**覆盖生效**。**⚠️ 写断言前必须先剥整行注释**——注释里引用的字面量（「`npm ci` 会失败」）会伪造出假阳性。

## 状态锚点（权威看 `AGENTS.md` 第 6／7 节）
- 关键路径 `T0→T1→T2→T3→D1→D2→D3`：**T0／T1／T2／T3／T5 已通**。**T5 已完成**（简单工厂 `LabRecordFactory` + 两个事件枚举 + `docs/大作业/设计模式-类图.md`；**D-05 销项**；纯重构，`Tests run: 49` 用例数不变）。**T4 已完成（2026-09-17 第 20 轮）**：两个多阶段 Dockerfile + `nginx.conf` + `docker-compose.yml` + `.prod.yml` + `deploy/mysql-init.sh` + `.env.example` + `ci.yml` 的 docker job 解注释，**D-04 销项**；**⚠️ 本机未装 Docker，`docker compose up` 未真跑**（见 **D-28**），替代证据 = `t4_verify.py` 94 条静态断言 + `t4_env_binding_check.py` 23 条环境变量绑定断言（全绿）。测试基线 **49 全绿**（单测 **33** + 集成 **16**）。
- 待办：**代码负责只剩 `D3` 源码 ZIP 打包**（T-2 天执行，需「文档负责」交完 5 份 PDF）。**D1** 剩余两份文档（第 3 测试、第 4 CI/CD；T3/T4 素材均已交件）。**D-24 / D-25**（看板"5 台维修中 / 0 待处理"自相矛盾 + 两张趋势图空）归属代码负责／T0，**待阿辉在 A/B 方案间拍板**，未擅动。**D-20** 演示口令待拍板。**新登记 D-27**（T4 卡自身两处缺口：环境变量清单漏 redis host/port、nginx 示例漏 `client_max_body_size`）／**D-28**（无 Docker → 卡的 `docker compose ps` 截图要求无法产出，**《CI/CD 部署方案》不要等这张图**）。
- **文档侧（交付物 1~5，70 分）**：**第 1／2／5 份已完成并出 PDF** —— 01 需求 **8** 页、02 架构 **13** 页、05 AI 报告 **6** 页，**全部命中任务书区间**。剩余 **第 3 份《测试计划与用例文档》**（素材已交件：`AI留痕/测试素材交付.md`）与 **第 4 份《CI/CD 部署方案》**（T3/T4 素材已到位）。
- **PDF 流水线已打通（零依赖，可复用）**：`Markdown →(自写 GFM 渲染器)→ 打印 HTML →(Chrome 152 无头 + CDP `Page.printToPDF`)→ PDF`。工具 `.workbuddy/tools/md2pdf/` 5 个脚本：`md2pdf.mjs`（渲染，**整批只起一次浏览器**）、`pdfinfo.mjs`（不装依赖读页数/`FontFile2` 嵌入/`ToUnicode` 可复制）、`build.mjs`（批量 + **页数区间 PASS/FAIL**，报告 `.workbuddy/logs/md2pdf-build.txt`）、`build.ps1`（纯 ASCII 包装器）、`pagemap.mjs`（**量每章落第几页**，报告 `md2pdf-pagemap.txt`）。命令：`node .workbuddy/tools/md2pdf/build.mjs`；单份 `MD2PDF_ONLY=05 node ...build.mjs`。**新增文档放进 `docs/大作业/文档/` 即自动纳入构建**（区间表 `RANGE` 在 `build.mjs`，`05` 已配 `[4,6]`）。
- **压页数两条硬经验**：① 打印 CSS 对表格加了 `page-break-inside: avoid` → **放不下的表格整张跳页**，留白累计可达 1 页，故 `ceil(scrollHeight/页高)` **会低估 1 页，判据要留余量**；先跑 `pagemap.mjs` 定位再定点删，**别盲删**。② **收紧 CSS 是双向的**：为压 02（19→13 页）收紧后，01 从 10 页掉到 8 页贴住下限——**上限下限一起盯**。
- **D-21** 真实库缺 `laboratory:dashboard:view` 菜单（除超管外首页看板不可见）→ 修复脚本 `[7/7] laboratory_permission_fix.sql` **已就绪且幂等，但 `AGENTS.md` §7.3 仍记 `❌ 未修`**（行内未回写执行结果）——**以台账为准**；截图因此用 `admin` 取景。含本条的教训：**"脚本存在"≠"已在真实库执行"，回写状态只看台账行内文字**。（`巡检-UI演示路径-2026-09-17.md` 实测该菜单已存在、`labadmin` 能看到看板，但§7.3 未回写，**两处不一致时以台账为准**。）
- **测试侧两条已收口（别再当遗留）**：**D-07** 原 5 个反射版状态机用例**有意保留**（契约层），另新增 `LabRepairStatusMachinePublicEntryTest` 走 `updateLabRepair` 公开入口（入口层）；**D-19** 看板**已覆盖**（8 条 mapper 查询、7 个用例）——靠测试侧 `create alias ... for "类.方法"` 注册同名 `date_format`/`date_sub`，**生产 mapper XML 一字未改**。答辩口径：看板已测。
- 容忍偏移（答辩主动说明）：**D-01** 二维码相对路径手机扫不开；**D-06** 前后端状态机两张手写表；**D-08** 弱密钥。**D-19 已移出容忍清单**。
- **测试的两条如实说明（素材包 `AI留痕/测试素材交付.md` 已写，文档与答辩沿用）**：① **行/分支覆盖率百分比出不来**（离线仓库缺 `jacoco-maven-plugin`/`org.jacoco.core`），只写「用例↔BR 矩阵 + 断言 177 / 交互校验 27」，**不要编百分比**；② 构建日志里 `IllegalStateException: Cannot use PPID ... NOOP events` 是 Surefire 调 WMIC 被安全策略拦截后自行降级，**不影响测试**。
- **⚠️ 答辩截图必须在 9-19 冻结代码前用掉**：`docs/大作业/AI留痕/截图/T6-核心功能界面/` 4 张（首页看板／资产台账／资产履历／报修时间线），1920×1080，取景用 `admin`。9-19 后再改 UI 这批图作废。
- **演示数据有两类固有缺陷，且都不是「真实库漂移」**（2026-09-17 核现状推翻原始记录，见 `AI留痕/巡检-演示数据校准-2026-09-17.md`）：① **种子自身不自洽** —— `[4/7]` 把 `LAB-DEMO-2026-009/024` 置「维修中」，而 `[5/7]` 那 4 张工单**全是终态、且挂在别的资产上** → 影子库重建实测「维修中 2 台 / 在途工单 0 张」，**光重建治不好**；已在 `[5/7]` 补 2 张**在途**工单（1 张待审核可演示审核 + 1 张维修中可演示推进，履历文案逐字对齐 `LabRepairEvent`，`repair_user` 必须 `left join` 否则待审核单静默插不进）。② **相对日期会自然老化** —— 报修单有 `repair_code` 幂等保护（`where not exists`，**只插不更新**），`create_time` 永远停在首次插入那天，看板「近七日」两张图会一天天滑空。
- **演示数据校准 = `RuoYi-Vue-fast/sql/laboratory_demo_refresh.sql`**（幂等、按需、**不参与 `[N/7]` 顺序**）：先补履历再改状态，把「无在途工单的维修中资产」归位；再把 6 张演示单日期平移回近 7 天。**答辩前 1 天必跑一次**（一次平移约维持 7 天）。断言工具 `.workbuddy/tools/demo_refresh_verify.sql`（12 条 **只读**，关键不变式 = **维修中资产数 == 有在途工单的资产数**），证据 `.workbuddy/logs/demo_refresh_verify.txt`（克隆真实库路径：修复前 3/12 → 修复后 12/12 → 幂等复跑不变）。
- **一律「只补不重建」**：空库重建会把资产 **32 → 25 台**（丢 7 台早期 `LAB-CHEM-*`），与已交付的 T6 截图（资产台账 32 行）对不上 → 截图与现场不一致比数字矛盾更糟。真实库 `education_system` **已于 2026-09-18 00:0x 经阿辉授权执行完毕**（先备份 `.workbuddy/logs/db-backup-education_system-20260918.sql`）：维修中资产 **5→2**、在途工单 **0→2**、待审核 **1**、近七日趋势 **5 天有柱子（09-16 含 ¥120）**、断言 **12/12**、资产规模 **32 台不变**。**⚠️ 副作用：`T6-01` 首页看板截图当场过期 → 已按 `AGENTS.md` 6.2 交件台通知「前端UI负责/PPT和设计」重拍**；答辩前 1 天需再跑一次校准，**建议把"最终校准 + 截图定稿"排在同一天**。
