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
1. 自定义码只在 `com.ruoyi.project.laboratory`（controller／domain／mapper + `resources/mybatis/laboratory/*.xml`／service+impl／constant/LabConstants／util）。若依配置走通配符，**新增子包无需改配置**。
2. **角色口径只能改三处且必须同步**：后端 `LabRoleUtils`、前端 `utils/labPermission.js`、`sql/laboratory_permission_fix.sql` 末尾清单（**D-10 = 漏了第三处**）。`canHandleRepair`=admin/teacher/lab_manager/repair_engineer；`canViewAll`=上述 4 个 + asset_keeper/lab_viewer。
3. 接口前缀 `/laboratory/{room|asset|repair|dashboard}`；权限标识 `laboratory:{模块}:{操作}`。
4. 报修状态机（`LabRepairServiceImpl.validateStatusChange`）：`0 待审核→1 待维修→2 维修中→3 已完成`，`0→4 已拒绝`；非法流转抛 `ServiceException`；传 `null` 或同状态**放行**。
5. 资产联动：提交报修→资产 `2 维修中`；完成／拒绝／删除待审核单→资产回 `0 正常`。报修资产**只在新增时选定**，落库后不可更换。
6. 删除一律**逻辑删除** `del_flag='2'`；列表查询必须带 `del_flag='0'`。
7. SQL 在 `RuoYi-Vue-fast/sql/`，按头部 `[N/7]` 顺序执行：`[1/7] laboratory_schema`→`[2/7] ry_20260417`→`[3/7] laboratory_menu_role`→`[4/7] laboratory_demo_seed`→`[5/7] laboratory_user_seed`→`[6/7] laboratory_upgrade`→`[7/7] laboratory_permission_fix`；**全部可重复执行**。不参与顺序的按需脚本：`laboratory_index_fix.sql`（幂等迁移，删与逻辑删除冲突的唯一索引）、`laboratory_cleanup.sql`（界面精简，非清数据）。
8. 建表硬规矩：① 字段类型/长度**以线上库为准**；② 业务编号（`asset_code`／`repair_code`／`room_no`）**只建普通索引、不建唯一索引**——系统一律逻辑删除、行不消失，唯一索引会与「编号可重用」（校验带 `del_flag='0'`）冲突 → 「删了资产就建不回同编号、接口 500」；唯一性由 `checkAssetCodeUnique`／`checkRoomNoUnique` 承担。字符集显式 `utf8mb4`／`utf8mb4_general_ci`。
9. 安全（写非功能需求可引）：密码 `BCryptPasswordEncoder` 存哈希；`token.header=Authorization`；`token.expireTime=30` 分钟；`token.secret` 默认弱值须用 `RUOYI_TOKEN_SECRET` 覆盖（**D-08**）。

## 状态锚点（权威看 `AGENTS.md` 第 6／7 节）
- 关键路径 `T0→T1→T2→T3→D1→D2→D3`：**T0／T1／T2 已通，瓶颈 = T3（CI 流水线）**。测试基线 **49 全绿**（单测 **33** + 集成 **16**）；加固轮后 T1 卡口径由 30→33、T2 由 7→16。
- 待办：**T3** CI+金丝雀／**T4** Dockerfile／**T5** 设计模式（简单工厂）；**D1** 五份 PDF（70 分）归文档负责。
- **D-21** 真实库缺 `laboratory:dashboard:view` 菜单（除超管外首页看板不可见）→ 修复脚本 `[7/7] laboratory_permission_fix.sql` **已就绪且幂等，但 `AGENTS.md` §7.3 仍记 `❌ 未修`**（行内未回写执行结果）——**以台账为准**；截图因此用 `admin` 取景。含本条的教训：**"脚本存在"≠"已在真实库执行"，回写状态只看台账行内文字**。
- **测试侧两条已收口（别再当遗留）**：**D-07** 原 5 个反射版状态机用例**有意保留**（契约层），另新增 `LabRepairStatusMachinePublicEntryTest` 走 `updateLabRepair` 公开入口（入口层）；**D-19** 看板**已覆盖**（8 条 mapper 查询、7 个用例）——靠测试侧 `create alias ... for "类.方法"` 注册同名 `date_format`/`date_sub`，**生产 mapper XML 一字未改**。答辩口径：看板已测。
- 容忍偏移（答辩主动说明）：**D-01** 二维码相对路径手机扫不开；**D-06** 前后端状态机两张手写表；**D-08** 弱密钥。**D-19 已移出容忍清单**。
- **测试的两条如实说明（素材包 `AI留痕/测试素材交付.md` 已写，文档与答辩沿用）**：① **行/分支覆盖率百分比出不来**（离线仓库缺 `jacoco-maven-plugin`/`org.jacoco.core`），只写「用例↔BR 矩阵 + 断言 177 / 交互校验 27」，**不要编百分比**；② 构建日志里 `IllegalStateException: Cannot use PPID ... NOOP events` 是 Surefire 调 WMIC 被安全策略拦截后自行降级，**不影响测试**。
- **⚠️ 答辩截图必须在 9-19 冻结代码前用掉**：`docs/大作业/AI留痕/截图/T6-核心功能界面/` 4 张（首页看板／资产台账／资产履历／报修时间线），1920×1080，取景用 `admin`。9-19 后再改 UI 这批图作废。
