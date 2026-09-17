# 高校实验室资产与报修系统

基于 **若依 RuoYi-Vue 3.9.2**（前后端分离版）二次开发的实验室资产与报修管理平台。
在保留若依原生系统管理、监控、代码生成能力的基础上，新增了「实验室管理」业务模块。

> **《软件新技术专题》大作业的公共入口是根目录的 `AGENTS.md`** —— 项目简述、工作流、
> 分工台账、进度状态、偏移检查都在里面。**接手项目先读它。**
> 细则在 `docs/大作业/`：`00-项目方向与协作规范.md`（选题决策与范围）、`01-需求基线.md`（用户故事与业务规则）、
> `02-交付物验收清单.md`（硬指标自查表）、`任务卡/T0~T5`（含可直接复制给编码 Agent 的提示词）。
> **动手改代码前先认领对应任务卡。**


## 功能概览

| 模块 | 主要能力 |
| --- | --- |
| 实验室房间 | 房间编号 / 名称 / 所属学院 / 管理员维护，删除前校验房间下是否还有资产 |
| 资产台账 | 资产 CRUD、按实验室归属、状态管理、Excel 导入导出、资产二维码标签、资产履历追溯 |
| 设备报修 | 提交报修（含故障图片）、审核与状态流转、维修成本登记、完成后用户评价、全流程处理时间线 |
| 运维工作台 | 首页看板：资产总数 / 维修中资产 / 待审核报修 / 已完成报修 4 项指标 + 报修趋势、故障等级分布、实验室维修排名、维修费用趋势 4 张图 |

## 技术栈

| 层 | 技术 |
| --- | --- |
| 后端 `RuoYi-Vue-fast/` | Spring Boot 2.5.15、Java 8、MyBatis、Druid、Redis、Quartz、Spring Security + JWT、Swagger 3、Apache POI、Velocity。单体 jar，端口 8080 |
| 前端 `RuoYi-Vue3/` | Vue 3.5、Vite 6、Element Plus 2.13、Pinia、ECharts 5.6、axios。开发服务器端口 80 |
| 数据库 | MySQL 8，库名 `education_system` |
| 缓存 | Redis（若依登录态与验证码依赖，必须启动） |

## 目录结构

```
Manager_system/
├── RuoYi-Vue-fast/                      后端
│   ├── sql/                             数据库初始化脚本（可重复执行，见下文执行顺序）
│   └── src/main/
│       ├── java/com/ruoyi/
│       │   ├── common/                  若依原生：通用工具、常量、异常
│       │   ├── framework/               若依原生：安全、AOP、数据源、配置
│       │   └── project/
│       │       ├── system/              若依原生系统管理 + 公告已读回执扩展
│       │       ├── monitor/             若依原生监控
│       │       ├── tool/                若依原生代码生成
│       │       └── laboratory/          ★ 本项目自定义业务模块，见下方分层说明
│       └── resources/mybatis/
│           ├── system/                  若依原生 Mapper XML
│           └── laboratory/              ★ 自定义 Mapper XML
└── RuoYi-Vue3/                          前端
    └── src/
        ├── api/laboratory/              ★ 自定义接口封装
        ├── views/laboratory/            ★ 自定义页面（room / asset / repair）
        ├── utils/labPermission.js       ★ 前端角色口径
        └── views/index.vue              首页运维工作台
```

## 后端自定义模块分层

```
com.ruoyi.project.laboratory
├── constant/LabConstants          状态码常量（资产状态、报修状态、逻辑删除标记）
├── controller/                    LabRoom / LabAsset / LabRepair / LabDashboard Controller
├── domain/                        LabRoom、LabAsset、LabRepair、LabRepairRecord、LabAssetRecord
├── mapper/                        LabXxxMapper
├── service/ + service/impl/       ILabXxxService + LabXxxServiceImpl
└── util/
    ├── LabRoleUtils               角色判定唯一来源
    ├── LabStatusUtils             状态中文标签
    ├── LabSecurityUtils           操作人取值
    └── QrCodeUtils                资产二维码 SVG 生成
```

若依的 MyBatis 配置使用通配符（`typeAliasesPackage: com.ruoyi.project.**.domain`、
`mapperLocations: classpath*:mybatis/**/*Mapper.xml`），因此新增子包**无需修改任何配置**。

## 角色与数据权限

角色口径只有两个来源，改角色白名单必须同步改这两处，否则会出现「按钮可见但接口 403」：
后端 `LabRoleUtils`、前端 `src/utils/labPermission.js`。

| 账号 | 角色 | 角色 key | 可看全部数据 | 可处理报修 |
| --- | --- | --- | :---: | :---: |
| admin | 超级管理员 | `admin` | ✔ | ✔ |
| labadmin | 实验室管理员 | `lab_manager` | ✔ | ✔ |
| repair01 | 维修工程师 | `repair_engineer` | ✔ | ✔ |
| asset01 | 资产管理员 | `asset_keeper` | ✔ | ✘ |
| viewer01 | 实验室观察员 | `lab_viewer` | ✔ | ✘ |
| room01 | 房间管理员 | `room_keeper` | ✘（仅本人） | ✘ |
| student1 | 学生助管 | `student_assistant` | ✘（仅本人） | ✘ |

「可看全部数据」以外的用户，报修列表、报修详情与看板的**报修类**指标会被强制按「申请人 = 自己」过滤。
看板里的「资产总数」「维修中资产」是全局口径，不按申请人过滤（资产不归属任何申请人）。

首页运维工作台的接口由独立权限 `laboratory:dashboard:view` 保护，只授予「可看全部数据」的 6 个角色；
其余角色登录后首页只显示可访问的快捷入口，不会产生 403。

## 报修状态机

```
0 待审核 ──审核通过──► 1 待维修 ──开始维修──► 2 维修中 ──维修完成──► 3 已完成
   │                                                                    │
   └──拒绝──► 4 已拒绝                                                   └──► 提交人评价（1~5 分）

资产状态联动：提交报修 → 资产置 2 维修中；报修完成/被拒绝/删除待审核单 → 资产回到 0 正常
报修资产在「新增报修」时一次性选定，之后任何人都不能更换（否则普通用户可借修改待审核单把任意资产置为维修中）
```

非法流转由 `LabRepairServiceImpl.validateStatusChange` 直接抛 `ServiceException`，不依赖前端限制。
该状态机由 `LabRepairServiceImplTest` 用「全部合法流转 + 全部非法流转」矩阵做契约测试，
前端 `repair/index.vue` 的 `nextStatusOptions` 必须与这张表一致。

## 本地运行

### 1. 初始化数据库

创建库 `education_system`，然后**按顺序**执行 `RuoYi-Vue-fast/sql/` 下的脚本：

| 顺序 | 脚本 | 作用 |
| :---: | --- | --- |
| 1 | `laboratory_schema.sql` | **实验室 5 张业务表**（房间 / 资产 / 报修 + 两张记录表） |
| 2 | `ry_20260417.sql` | 若依基础表结构与基础数据 |
| 3 | `laboratory_menu_role.sql` | 实验室菜单、按钮权限、`teacher` 角色 |
| 4 | `laboratory_demo_seed.sql` | 6 个实验室演示角色及其菜单授权；演示房间与 25 条资产 |
| 5 | `laboratory_user_seed.sql` | 演示账号与账号-角色绑定；演示报修单 4 条及处理记录 |
| 6 | `laboratory_upgrade.sql` | 报修附件/评价字段兜底、两张记录表、4 组字典、新增按钮权限 |
| 7 | `laboratory_permission_fix.sql` | 看板独立权限 `laboratory:dashboard:view` 及其授权 |

除第 2 个以外，脚本都写成**可重复执行**（`where not exists` / `if not exists` / 存储过程判列），
重复跑不会报错也不会造重复数据。**`ry_20260417.sql` 是例外**：它是若依官方脚本，内部是
`drop table` + `create table`，重复执行会清空 `sys_*` 的数据，**只在空库上跑一次**。

另有 3 个**按需执行、不参与上面顺序**的维护脚本：

| 脚本 | 什么时候用 |
| --- | --- |
| `laboratory_index_fix.sql` | **老库纠偏**：删掉线上库那两个与逻辑删除冲突的唯一索引（`uni_asset_code` / `uni_repair_code`），并补齐缺失的普通索引。空库重建时**不需要**跑它（`laboratory_schema.sql` 建的表本来就没有唯一索引） |
| `laboratory_cleanup.sql` | 界面精简（隐藏监控/工具入口、把「系统管理」改名），与 `laboratory_menu_role.sql` 末尾内容重复 |
| `quartz.sql` | 若依定时任务表，只有启用 Quartz 时才需要 |

### 2. 启动后端

```bash
cd RuoYi-Vue-fast
mvn clean package -DskipTests
java -jar target/ruoyi.jar
```

或在 IDEA 中直接运行 `com.ruoyi.RuoYiApplication`。

数据库、Redis 连接默认为本机；生产环境请用环境变量覆盖，不要改配置文件：
`RUOYI_DB_URL` / `RUOYI_DB_USERNAME` / `RUOYI_DB_PASSWORD` / `RUOYI_REDIS_PASSWORD` / `RUOYI_TOKEN_SECRET`。

### 3. 启动前端

```bash
cd RuoYi-Vue3
npm install
npm run dev          # http://localhost
npm run build:prod   # 产物 dist/
```

前端通过 Vite 代理把 `/dev-api` 转发到 `http://localhost:8080`（见 `vite.config.js`）。

### 4. 运行单元测试

```bash
cd RuoYi-Vue-fast
mvn test
```

现有测试位于 `src/test/java/com/ruoyi/project/laboratory/service/impl/`，覆盖四条业务硬约束：
报修状态机的全部合法/非法流转矩阵、维修中资产不可删除、房间下仍有资产时不可删除。

### 演示账号

密码统一 `123456`，账号见上文「角色与数据权限」表格。

## 项目约定

1. 自定义代码一律放在 `com.ruoyi.project.laboratory` 下，不污染若依原生包。
2. 接口前缀统一 `/laboratory/{room|asset|repair|dashboard}`，权限标识 `laboratory:{模块}:{操作}`。
3. 角色判定只改 `LabRoleUtils` 与 `utils/labPermission.js` 这两处；
   涉及「可查看全部数据」的角色时，`sql/laboratory_permission_fix.sql` 末尾的角色清单也要同步。
4. 业务流水（报修处理记录、资产履历）的写入与状态变更在同一事务内。
5. 删除资产/报修单采用逻辑删除（`del_flag = '2'`），不做物理删除。

## 已知待办

- [ ] 前端 `repair/index.vue` 的 `nextStatusOptions` 与后端 `validateStatusChange` 是两张手写表，跨语言无法真正共享；
      现有后端矩阵测试可兜住后端，前端改动时需人工比对。
- [ ] 资产二维码内容是相对路径 `/laboratory/repair?assetId=x`（见 `LabAssetServiceImpl.buildAssetQrcode`），
      手机相机直接扫描无法打开，只能由站内登录后跳转。若要贴纸扫码直达，需要配一个站点基址。
- [ ] `application.yml` 中 `token.secret` 的默认值为弱密钥，生产环境必须用环境变量覆盖。
- [ ] `statusOrText`、`displayAssetName` 等小工具仍留在各自 ServiceImpl 中，可视需要继续下沉。
- [x] ~~未初始化 Git 仓库（当前无 `.git`）~~ —— **2026-09-17 已解除**：仓库已 `git init -b main` 并挂上远程
      `origin`（`https://github.com/same-day123/Manager-system.git`），首次提交 `57c81b9` 已推送；
      其后 `ed971fd`（T0 补齐建表脚本与种子数据）待用新凭据推送。
