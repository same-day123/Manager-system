# 高校实验室资产与报修系统

[![CI](https://github.com/same-day123/Manager-system/actions/workflows/ci.yml/badge.svg)](https://github.com/same-day123/Manager-system/actions/workflows/ci.yml)

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
├── .github/workflows/ci.yml              GitHub Actions 流水线定义
├── deploy/
│   ├── local-pipeline.ps1                本地等价流水线（6 阶段，与 ci.yml 一一对应）
│   └── mysql-init.sh                     容器首次启动时按显式顺序导入 SQL
├── docker-compose.yml                    一键起全套环境（mysql / redis / 后端 / 前端）
├── docker-compose.prod.yml               生产覆盖层：去掉端口暴露、加资源与日志上限
├── .env.example                          编排口令模板（复制成 .env 后使用，.env 不入库）
├── RuoYi-Vue-fast/                      后端
│   ├── Dockerfile                        后端镜像（两段式 maven → JRE 8，非 root 运行）
│   ├── .dockerignore
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
    ├── Dockerfile                        前端镜像（node 构建 → nginx 托管）
    ├── nginx.conf                        容器内站点配置（/prod-api/ 反代并剥离前缀）
    ├── .dockerignore
    └── src/
        ├── api/laboratory/              ★ 自定义接口封装
        ├── views/laboratory/            ★ 自定义页面（room / asset / repair）
        ├── utils/labPermission.js       ★ 前端角色口径
        └── views/index.vue              首页运维工作台
```

## 后端自定义模块分层

```
com.ruoyi.project.laboratory
├── constant/
│   ├── LabConstants               状态码常量（资产状态、报修状态、逻辑删除标记）
│   ├── LabAssetEvent              资产履历事件枚举（动作名 + 文案模板 + 状态码反查）
│   ├── LabRepairEvent             报修履历事件枚举（动作名 + 文案模板）
│   └── LabRecordFactory           ★ 履历记录工厂（简单工厂，见「设计模式」一节）
├── controller/                    LabRoom / LabAsset / LabRepair / LabDashboard Controller
├── domain/                        LabRoom、LabAsset、LabRepair、LabRepairRecord、LabAssetRecord
├── mapper/                        LabXxxMapper
├── service/ + service/impl/       ILabXxxService + LabXxxServiceImpl
└── util/
    ├── LabRoleUtils               角色判定唯一来源
    ├── LabStatusUtils             状态中文标签（assetRecordType 委托 LabAssetEvent）
    ├── LabSecurityUtils           操作人取值
    └── QrCodeUtils                资产二维码 SVG 生成
```

若依的 MyBatis 配置使用通配符（`typeAliasesPackage: com.ruoyi.project.**.domain`、
`mapperLocations: classpath*:mybatis/**/*Mapper.xml`），因此新增子包**无需修改任何配置**。

## 设计模式：履历记录简单工厂

资产履历（`lab_asset_record`）与报修履历（`lab_repair_record`）原先由两个 Service 各自
手工 `new` 对象、手工写动作名、手工拼文案、手工兜底操作人，同样四步在 10 个调用点上重复。
现用一个**简单工厂（创建型）**把「一次业务事件 → 一条履历记录」的组装规则收口：

```
Service 声明事件 → LabRecordFactory.assetRecord/repairRecord(...) 组装 → Mapper 落库
                        ↑ 动作名与文案来自 LabAssetEvent / LabRepairEvent
```

| 收益 | 说明 |
| --- | --- |
| 文案集中 | 模板只存在枚举里，改措辞一处生效 |
| 满足 OCP | 新增履历事件只加枚举常量，**两个 Service 零改动** |
| 兜底不漏 | 操作人兜底与 `createBy` 赋值只在工厂里存在一处 |
| 口径唯一 | 「资产状态码 → 动作名」只有 `LabAssetEvent.ofAssetStatus` 一张表 |

工厂是**无状态纯静态工具类**（无 Spring 注解、不写库、不加缓存），单元测试无需 Spring 上下文。
完整类图、选型理由与「为什么不选状态模式」见 [`docs/大作业/设计模式-类图.md`](docs/大作业/设计模式-类图.md)。

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
yarn install         # 仓库里只有 yarn.lock（没有 package-lock.json），用 yarn 而非 npm
yarn dev             # http://localhost
yarn build:prod      # 产物 dist/
```

前端通过 Vite 代理把 `/dev-api` 转发到 `http://localhost:8080`（见 `vite.config.js`）。

### 4. 运行测试

```bash
cd RuoYi-Vue-fast
mvn clean test
```

> 本机若报 `ClassNotFoundException: plexus-classworlds.launcher`，说明 `mvn` 启动脚本损坏，改用包装脚本：
> `powershell -NoProfile -ExecutionPolicy Bypass -File .workbuddy/tools/mvnx.ps1 -o -B clean test`。

测试分两层，**合计 49 个用例，全部通过**（`Tests run: 49, Failures: 0, Errors: 0`，2026-09-17 22:21 实测）：

| 层次 | 数量 | 位置 | 依赖 |
| --- | :-: | --- | --- |
| **单元测试** | 33 | `src/test/java/com/ruoyi/project/laboratory/{service/impl,util}/` | 仅 JUnit 5 + Mockito，**不连数据库** |
| **集成测试** | 16 | `src/test/java/com/ruoyi/project/laboratory/integration/` | **H2 1.4.199 内存库，本地无需安装 MySQL / Redis** |

- **单元测试**覆盖报修状态机全部合法/非法流转矩阵（含 `updateLabRepair` 公开入口版）、提交校验、
  评价规则、资产与房间校验、以及权限判定口径（`LabRoleUtils`）。
- **集成测试**跑真实的 `Service → Mapper → 数据库` 链路，验证多表事务落库结果（提交报修时
  报修单 + 资产状态 + 履历三张表一起写、非法状态流转时整体不变、逻辑删除后行仍在等），
  并覆盖首页看板的 4 项指标与 4 张图查询。
  建表脚本由 `src/test/resources/sql/lab-schema-h2.sql` 提供（由 `sql/laboratory_schema.sql` 转 H2 方言）。
  跑集成测试**不需要任何中间件**，CI 里可直接零依赖执行。

### 演示账号

密码统一 `123456`，账号见上文「角色与数据权限」表格。

## 容器化部署（docker compose）

> **诚实说明：交付本机未安装 Docker，下面这些命令尚未在本地实跑过。**
> 因此改用两层**不依赖 Docker** 的检查替代（证据见 `.workbuddy/logs/` 与
> `docs/大作业/AI留痕/T4-2026-09-17.md`）：
> ① **94 条配置静态断言**（`.workbuddy/tools/t4_verify.py`）—— YAML 可解析、SQL 顺序与磁盘文件互校、
> nginx 前缀剥离、Dockerfile 非 root/健康检查、口令无明文、`.dockerignore` 该排的排了；
> ② **23 条环境变量绑定实证**（`.workbuddy/tools/t4_env_binding_check.py`）—— 用 Spring 真实的
> 属性源机制证明环境变量确实盖得住配置文件里写死的值（并跑两组不同取值做对照，排除"碰巧相等"）。
> **这不等于容器真的起来了**，答辩时按实情说明。

```bash
cp .env.example .env      # 按需改口令；.env 已被 .gitignore 忽略，不会入库
docker compose up -d
docker compose ps         # mysql/redis 应为 healthy，backend/frontend 为 running
docker compose logs lab-backend | tail -50
# 浏览器打开 http://localhost ，用 labadmin / admin123 登录
```

| 服务 | 镜像 / 构建源 | 端口 | 关键设计 |
| --- | --- | --- | --- |
| `mysql` | `mysql:8.0`（锁定版本，不用 latest） | 3306 | 首次启动按 `deploy/mysql-init.sh` 的**显式顺序**导入 7 个 SQL；时区用数字偏移 `+08:00` |
| `redis` | `redis:7-alpine` | 6379 | 若依强制依赖；命名卷持久化，重启不掉登录态 |
| `lab-backend` | `./RuoYi-Vue-fast` | 8080 | 非 root（uid 1000）、`TZ=Asia/Shanghai`、`/dev/tcp` 端口健康检查、exec 形式 ENTRYPOINT |
| `lab-frontend` | `./RuoYi-Vue3` | 80 | nginx 托管 `dist/`，`/prod-api/` 反代到后端并剥离前缀 |

**三处「不改配置文件」的环境差异覆盖**，全部由 compose 注入（这正是《CI/CD 部署方案》里
「三环境配置差异」那张表的技术依据）：

| 配置项 | 配置文件里写死的值 | 容器里生效的值 | 覆盖方式 |
| --- | --- | --- | --- |
| `ruoyi.profile` | `D:/ruoyi/uploadPath` | `/home/ruoyi/uploadPath` | `RUOYI_PROFILE` |
| `spring.redis.host` / `spring.redis.port` | `localhost` / `6379`（**无 `${}` 占位符**） | `redis` / `6379` | `SPRING_REDIS_HOST` / `SPRING_REDIS_PORT` |
| 数据源 url / 账号 / 口令 | 本机默认值 | `jdbc:mysql://mysql:3306/...` | `RUOYI_DB_URL` / `RUOYI_DB_USERNAME` / `RUOYI_DB_PASSWORD` |

生产或类生产再叠一层覆盖文件（需要 Compose v2.24+，用到了 `!override` 标签）：

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

差异是：端口只绑回环、容器内关闭接口文档、关键口令改为**必填**（不配就拒绝启动）、
内存与日志都加上限、`restart: always`。

> **最容易踩的坑：SQL 初始化顺序。** 不要把 `sql/*.sql` 直接挂进 `/docker-entrypoint-initdb.d/` ——
> 官方镜像按**文件名字母序**执行，`laboratory_*` 会全部排在 `ry_20260417.sql` 之前，
> 结果是每个脚本都报「表不存在」。必须像本仓库这样，只挂一个显式排序的 `mysql-init.sh`。

## CI/CD

流水线定义在 [`.github/workflows/ci.yml`](.github/workflows/ci.yml)，由 GitHub Actions 执行。
触发条件：推送到 `main`、发起 Pull Request，以及手动触发（`workflow_dispatch`）。

| job | 阶段 | 命令 | 产物 |
| :---: | --- | --- | --- |
| `backend` | 后端构建与测试 | `mvn -B clean test`（JDK 8 temurin） | surefire 测试报告 |
| `frontend` | 前端生产构建 | `yarn install --frozen-lockfile` + `yarn build:prod`（Node 22） | `dist/` |
| `package` | 打包可运行 jar | `mvn -B package -DskipTests`（依赖前两个 job 全绿） | `target/ruoyi.jar` |
| `docker` | 镜像构建 | `docker/build-push-action`（buildx，**只 build 不 push**）+ `docker compose config` 语法门禁 | 后端/前端两个镜像 |

> 四个 job 的产物都以 artifact 形式上载，可在 Actions 运行页面直接下载。
> 凭据一律走 GitHub Secrets，`ci.yml` 内不出现任何明文密码。

### 本地等价流水线

真实流水线跑在 GitHub Actions 上，答辩或本地自检时用一个等价脚本按**相同阶段顺序**跑一遍：

```bash
powershell -NoProfile -ExecutionPolicy Bypass -File deploy/local-pipeline.ps1
```

脚本共 6 个阶段（版本信息 / 后端构建与测试 / 前端构建 / 打包产物 / 镜像构建 / 冒烟测试），
与 `ci.yml` 的 job 一一对应；每阶段打印 `[n/6] 阶段名 ... PASS (耗时)`，末尾输出汇总表，
任一阶段失败即 `exit 1`。

- 阶段 2 会把 Maven 的 `Tests run: ...` 汇总行单独摘出来打印。
- 阶段 5/6 在缺少 Docker 或后端未启动时打印 `SKIPPED` 并继续（**不伪装成 PASS**）。
- 全过程日志落在 `.workbuddy/logs/local-pipeline-<时间戳>.log`。

### 环境规划

| 维度 | 开发环境 dev | 类生产环境 staging | 生产环境 prod |
| --- | --- | --- | --- |
| 用途 | 本地开发与联调 | 集成验证、答辩演示 | 正式对外服务 |
| 部署方式 | IDEA 直跑 + `vite dev` | docker compose 单机 | docker compose + Nginx 反代 |
| 数据库 | 本机 MySQL 8 `education_system` | 容器 MySQL 8 `education_system_staging` | 容器 MySQL 8 `education_system`，每日备份 |
| 缓存 | 本机 Redis | 容器 Redis 7 | 容器 Redis 7 + 持久化卷 |
| 配置来源 | `application.yml` 默认值 | 环境变量 `RUOYI_DB_*` | 环境变量 + 密钥管理 |
| 前端 API 前缀 | `/dev-api` | `/stage-api` | `/prod-api`（Nginx 反代并剥离前缀） |
| 日志级别 | `com.ruoyi: debug` | `com.ruoyi: info` | `com.ruoyi: warn` |
| 访问控制 | 无 | 内网 + 基础认证 | 公网 + HTTPS |

**部署策略：金丝雀发布。** 本项目是 docker compose 单机部署、没有 K8s，滚动发布所依赖的
编排与就绪探针不具备；蓝绿发布在单机上要同时维持两套完整实例、资源翻倍，且若依用 Redis
存登录态、两套实例会话不共享，切换时会把正在报修的用户踢下线。金丝雀只需在 Nginx
`upstream` 里给新版本一个较小权重（5% → 50% → 100%），出错时把权重改回 0 即可秒级回滚。
放量观察窗口与回滚阈值见《CI/CD 部署方案》。

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
      **容器化侧已给出解法**：`docker-compose.yml` 用 `RUOYI_TOKEN_SECRET: ${TOKEN_SECRET:-...}` 注入，
      `docker-compose.prod.yml` 进一步把它设为**必填项**（不配 `.env` 就拒绝启动）。裸机部署仍须自行覆盖。
- [ ] nginx 的 `client_max_body_size` 已在容器站点配置里放开到 `20m`（nginx 默认 1m 会把故障照片拦成 413）；
      **若把后端直接暴露给外网而不经本仓库的 nginx**，需要在对应的反向代理上补同一项。
- [ ] `statusOrText`、`displayAssetName` 等小工具仍留在各自 ServiceImpl 中，可视需要继续下沉。
- [x] ~~未初始化 Git 仓库（当前无 `.git`）~~ —— **2026-09-17 已解除**：仓库已 `git init -b main` 并挂上远程
      `origin`（`https://github.com/same-day123/Manager-system.git`），首次提交 `57c81b9` 已推送；
      其后 `ed971fd`（T0 补齐建表脚本与种子数据）待用新凭据推送。
