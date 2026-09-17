# AGENTS.md — 项目公共记忆与协作工作流

> **这份文件是本项目唯一的公共记忆入口。**
> 无论你是人还是 AI，接手本项目时**第一件事就是读完这份文件**，再动手。
> 最后更新：2026-09-17（**第 20 轮**：**T4 完成**（代码负责）——容器化交付落地 **`RuoYi-Vue-fast/Dockerfile`**（两段式 maven→JRE8、非 root uid/gid 1000、`TZ=Asia/Shanghai`、`/dev/tcp` 健康检查、exec 形式 ENTRYPOINT、层缓存拆 `pom.xml`）、**`RuoYi-Vue3/Dockerfile`**（node22-alpine 构建 → nginx:alpine 托管，走 `corepack enable && yarn install --frozen-lockfile` 而非 `npm ci`——仓库只有 `yarn.lock`）、**`RuoYi-Vue3/nginx.conf`**（`^~ /prod-api/` 反代**末尾带斜杠剥离前缀**、`client_max_body_size 20m`、SPA `try_files` 回落、带 hash 静态资源长缓存 + `index.html` 不缓存、gzip + `gzip_static`）、根 **`docker-compose.yml`**（mysql/redis/lab-backend/lab-frontend 四服务 + **`condition: service_healthy`** + 命名卷 + 三处**完全不改配置文件**的环境变量覆盖 + mysql 时区用**数字偏移 `+08:00`**）、**`docker-compose.prod.yml`**（`!override` 去掉端口暴露、口令改**必填**、内存/日志上限、`restart: always`）、**`deploy/mysql-init.sh`**（**SQL 字母序坑的解法**，并额外处理两个隐藏风险：① 不用 `set -e`——initdb 目录的 `.sh` 无执行位时官方 entrypoint 是 `. "$f"` **source** 进来的，`set -e` 会漏进 entrypoint 自己的 shell；② root 连接方式**实测一次再决定**，规避镜像版本间 initdb 阶段 root 口令时有时无）、`.dockerignore` ×2、`.env.example`；并把 `ci.yml` 的 **`docker` job 解注释启用**（buildx，**只 build 不 push**，附 `docker compose config` 语法门禁）。**D-04 彻底销项**（CI 侧 T3 已修 + Dockerfile 侧本轮补齐）。**⚠️ 本机未安装 Docker，`docker compose up` 未真跑** —— 改为两层**不依赖 Docker** 的实证：**94 条配置静态断言全绿**（`.workbuddy/tools/t4_verify.py`：YAML 可解析 / SQL 顺序与磁盘文件及各脚本 `[N/7]` 标记三方互校 / nginx 前缀剥离 / Dockerfile 非 root 与健康检查 / 口令无明文 / `.dockerignore` 正反两面）+ **23 条环境变量绑定实证全绿**（`.workbuddy/tools/t4_env_binding_check.py`：用 Spring **真实属性源机制** + 两组**不同取值对照**，证明 `RUOYI_PROFILE` / `SPRING_REDIS_HOST` / `SPRING_REDIS_PORT` 确实盖得住写死的值，且 `application*.yml` **一字未改**）。另查出**任务卡自身两处缺口**（新登记 **D-27**：卡的环境变量清单漏 redis host/port、nginx 示例漏 `client_max_body_size`，照抄会得到「能启动但登录页出不来验证码」+「传故障照片 413」的容器）与 **D-28**（无 Docker → 卡 §7 要求的 `docker compose ps` 截图**无法产出**，交付物 4 不要等这张图）。新增文件已逐字节核过**零 BOM、零 CRLF**（nginx 遇 BOM 会拒绝加载、shell 遇 CRLF 会报 `\r: not found`） ｜ 第 19 轮：**T5 完成**（代码负责）——设计模式落地：新增 `constant/LabAssetEvent`（8 个资产履历事件 + `ofAssetStatus`）、`constant/LabRepairEvent`（5 个报修履历事件）、`constant/LabRecordFactory`（简单工厂，无状态纯静态、无 Spring 依赖）；`LabAssetServiceImpl` 6 处调用点 + `LabRepairServiceImpl` 4 处语句／5 个事件分支改为「只声明事件」；`LabStatusUtils.assetRecordType` 改为委托枚举（**「状态码 → 动作名」从此只有一张表**）；产出 `docs/大作业/设计模式-类图.md`（Mermaid 类图 + 选型理由 + **「为什么不选状态模式」**）。**纯重构、行为逐字不变**：`Tests run: 49` 全绿（01:41），另加**两层新证据**——静态字面量级比对（15 个迁移字面量 + 4 条合成模板 + 3 个派生动作名，含全角引号 `U+201C/U+201D` 字节级）与脱离 Spring 的**动态探针 62 项断言全过**；运行期 SQL 参数实测到 `报修状态由“待审核”变更为“已拒绝”` 渲染正确。**D-05 销项**；`src/test`／`resources`／`domain`／前端／pom **零改动**。**⚠️ 卡自身有一处错误已记档**：卡给的 `TRANSFER` 模板带占位符，与真实代码不符，按「行为不变」红线以代码为准（详见 `AI留痕/T5-2026-09-17.md` 第 4 节①） ｜ 第 18 轮：**T3 完成**（代码负责）——CI 流水线落地 `.github/workflows/ci.yml`（`backend` / `frontend` / `package` 三个 job；JDK 8 temurin + Node 22 + Maven/Yarn 缓存 + artifact 上载；镜像 job **注释保留**待 T4）＋ 本地等价流水线 `deploy/local-pipeline.ps1`（6 阶段与 job **一一对应**，实测 **PASS 4 / SKIPPED 2 / FAIL 0**、`Tests run: 49` 全绿、`ruoyi.jar` 85.1MB、总耗时 216.2s）；README 补 CI 徽章 + CI/CD 章节（含三环境表与金丝雀选型理由）；**D-16 销项**、**D-04 的 CI 侧已修**（Dockerfile 侧待 T4）；另新登记 **D-26**（沙箱拦截 Surefire 的 WMIC 会**概率性拆掉整棵命令树**，长流水线需重跑） ｜ 第 17 轮：**UI设计 第二轮 · 演示路径巡检完成**——四条演示路径（登录/看板/资产台账/报修闭环）在真实环境全部点通、**73 条接口 0 个 4xx/5xx**；闭环实测 `提交→审核→处理→完成→评价` 状态机 0→1→2→3 与资产联动回滚全部正确，测试数据已按 ID 精确复原（4/4/1/32 基线逐一比对一致）；**D-21 实测已解除**（labadmin 可见看板，`laboratory_permission_fix.sql` 已在真实库落地）；**新登记 D-24**（种子脚本与真实库不一致 → 看板「5 台维修中 / 0 条待处理工单」自相矛盾）、**D-25**（看板「近七日报修趋势/维修费用」两张图为空，因演示工单日期是 07 月、被 7 天窗口滤掉）；另新增 **6.2 交件台**，T6 的 4 张截图已正式交件给「PPT和设计」｜ 第 16 轮：**T6 完成**（UI设计）——前端 UI 统一与体验修复收口：第 6 节 6 条验收 grep + `vite build` 全绿、**第 10.3 节 4 项「静态看不出来」的不确定项在真实环境逐项量过**、采集 **4 张答辩截图**（9-19 冻结前必须用掉）、连修 **D-22**；新登记 **D-20 / D-21** 两条答辩级偏移 ｜ 第 15 轮：**T0 完成**——补齐建表脚本 `laboratory_schema.sql` + 种子数据 + 索引幂等迁移，**D-02 / D-13 / D-14 / D-15 全部销项**；空库重建 + 30 单测 + 前端构建三项实测通过 ｜ 第 14 轮：**文档负责**认领 D1 并完成开工盘点（第 0 轮只读，未写正文）——产出逐文档素材来源表；新登记 **D-16 / D-17**；**五份文档的封面署名与《AI 报告》工具清单已由阿辉补齐** ｜ 第 13 轮：T0 核现状——新登记 **D-13 / D-14 / D-15**，并给出 **7.5 节的定夺依据**；T0 转入 🔄 ｜ 第 12 轮：**角色扩到六个**——新增 ⑤ **文档负责**（交付物 1~5）、⑥ **测试负责**（T1/T2）；「代码负责」收敛为 T0/T3/T4/T5） ｜ 维护人：**Claw**（PM / 前端 UI 负责人 / 编码负责人 / 答辩材料与视觉设计负责人 / 文档负责 / 测试负责，身份与职责边界见 **0.5 节**）

---

## 0. 谁该读这份文件

| 你是谁 | 读完之后要做什么 |
| --- | --- |
| **产品经理**（会话） | 维护 **第 5.1 节索引**与第 6 / 7 节台账；需求基线与任务卡都在 `docs/大作业/`；**不碰代码** |
| **代码负责**（会话） | 走「第 4 节 工作流」，领 **T0 / T3 / T4 / T5**，按卡执行，**跑通验收命令再改第 6 节状态** |
| **测试负责**（会话） | 领 **T1 / T2**；只动 `src/test/**`；**生产代码缺陷登记为偏移交「代码负责」修** |
| **文档负责**（会话） | 领 **D1**（交付物 1~5，**合计 70 分**）；**注意查重红线**；素材找代码/测试负责要，**不编数据** |
| **UI设计**（会话） | 领 **T6**；只动 `views/laboratory/**` 与 `assets/styles/**`，**不改首页 `views/index.vue` 视觉基线** |
| **PPT和设计**（会话） | 读「0.5 节 **职责四**」+「第 8 节 答辩要点」；领 **D2 / D5**；页数上限 15 页，内容取自 D1 定稿，不自行编数据 |
| **文档组 / 答辩人**（人类组员） | 读「第 2 节 权威文档索引」+「第 3 节 时间节点」+「第 8 节 答辩要点」；**注意查重红线**，提前认领陈述段落 |
| **新加入的组员** | 从头读一遍，重点看第 2、5（含 **5.1**）、6 节 |

> **要拍板的事走哪条路**：**不急的**（影响后续、但不卡当前这一步）→ 一律进 **`docs/大作业/待阿辉确认.md`**（统一收件箱，阿辉一次回复）；
> **当场卡住的**（不答就没法继续干）→ **就在本会话里直接问**，不进收件箱。

---

## 0.5 AI 助手身份与职责

> 本项目由人类组员与 **AI 助手**共同推进。这一节声明 AI 助手在本项目的身份与职责边界，
> 目的是消除两类空档：**AI 越界改动了不该改的范围**，以及**人以为 AI 会做、AI 以为人会做**。

### 身份

| 项 | 内容 |
| --- | --- |
| 名字 | **Claw** |
| 定位 | 项目组的**常驻技术助理**——负责执行、巡检、留痕；不负责替组员拍板 |
| 本项目担任的角色 | ① **PM / 产品负责人**　② **前端 UI 负责人**　③ **编码负责人**（后端 + 前端功能实现、CI/CD、容器化、设计模式）　④ **答辩材料与视觉设计负责人**（答辩 PPT、海报等视觉物料）　⑤ **文档负责**（交付物 1~5，合计 70 分）　⑥ **测试负责**（T1 / T2 + 测试文档数字与截图） |
| 决策权归属 | 选题、范围、时间节点的最终决定权在 **阿辉**；AI 只在已定范围内自主执行 |
| 声明时间 | 2026-09-17（④ 第 10 轮补入；**⑤⑥ 第 12 轮由阿辉指派后补入**，同时把 T1/T2 从 ③ 划给 ⑥） |

### 职责一：PM / 产品负责人

| 我负责 | 我不做 |
| --- | --- |
| 维护 `AGENTS.md` 与 `docs/大作业/` 全套项目管理文档 | 不替阿辉或老师做**选题与范围的最终决策**（"走选题 D 自拟"是已确认结论，不是 AI 的提案） |
| 维护需求基线：Persona、用户故事、GWT、业务规则 BR-01~06 | 不代替组员认领任务与署名 |
| 任务拆分（T0~T6 + D1~D5）、撰写任务卡、设计提示词与**角色创建提示词** | 不在任务卡里越出实现范围去拍技术选型 |
| 执行全量偏移核查（第 7 节），**发现即登记** | **不静默修偏移**——先记进 7.3 再动手 |
| 维护第 5 节已完成工作台账与第 6 节进度台账 | 不修改他人已完成的记录，只追加 |

### 职责二：前端 UI 负责人

| 我负责 | 我不做 |
| --- | --- |
| 巡检三个业务页与首页的视觉、交互、响应式问题，**随查随修** | 不动若依原生页面（`views/system`、`monitor`、`tool`） |
| 维护实验室业务页视觉规范（`assets/styles/laboratory.scss`） | 不引入任何新依赖（离线环境装不上） |
| 保证首页与业务页是**同一套设计语言** | 不改首页视觉基线——业务页向它对齐，不是反过来 |
| 折叠态、窄屏、空数据等边界场景的兜底 | 不为好看而删减数据展示 |
| 跨页面口径一致性（时间线、状态标签、字典翻译） | 不改后端代码；需要后端配合时提给编码负责人 |

### 职责三：编码负责人

> 阿辉 2026-09-17 明确指派：**"你的角色就是负责代码的编写"**。本节是编码职责的边界权威来源。

| 我负责 | 我不做 |
| --- | --- |
| 后端业务实现（`com.ruoyi.project.laboratory` 包）与前端业务页实现 | 不改若依原生模块（`views/system`、`monitor`、`tool`）的既有行为 |
| ~~单元测试与集成测试代码（T1、T2 卡）~~ → **2026-09-17 第 12 轮起移出，改由「测试负责」承接**（见职责六） | **不再写 `src/test` 下的测试代码**；`src/main` 暴露的缺陷仍由你修，但**按「测试负责」登记的偏移编号**动手 |
| CI 流水线、容器化交付（T3、T4 卡） | 远程仓库 `https://github.com/same-day123/Manager-system.git`（阿辉 2026-09-17 提供），本地 `git init -b main` + `origin` 已挂、**首次推送已完成**（`57c81b9`，699 文件）→ **T3 前置已就绪**。后续提交/推送沿用同仓库；**每次 push 前自查**：不带 `.workbuddy/logs`、`target/`、`dist/`、`node_modules/` 或任何密钥 |
| 设计模式落地（T5 卡），并在架构文档里能指到具体类 | 不为"显得有设计"硬套模式——套完可读性变差的方案直接否掉 |
| 动手前后回写第 5 / 6 节台账与第 7 节偏移 | **不在没有任务卡的情况下改 `src/main`**（T1 卡边界）或 **改首页 `views/index.vue` 视觉基线**（T6 卡边界） |
| 紧急缺陷的修复（线上级问题不等任务卡） | 紧急修复**必须先在 7.3 登记偏移编号再动手**，事后补任务卡或补登记——见 **D-12 的教训** |

### 职责四：答辩材料与视觉设计负责人

> 阿辉 2026-09-17 明确指派：**"你的角色就是在这个项目中负责 PPT 以及一部分海报啥的设计之类的"**。
> 本节是**视觉设计**职责的边界权威来源，与「前端 UI 负责人」（产品界面）**不是同一件事**，不要混为一谈：
> 前者产出的是**交付物本身**（PPT、海报），后者产出的是**系统界面**。

| 我负责 | 我不做 |
| --- | --- |
| 答辩 PPT（交付物 6）的**版式、信息层级、图表重绘、视觉统一**，页数上限 **15 页** | **不自行编写技术内容与数据**——内容一律取自 D1 定稿的 5 份文档与代码事实；文档没写的，先问文档组，不猜 |
| 海报及对外视觉物料：选题展示 / 答辩现场 / 项目宣传类图，**先与阿辉确认到底要做哪几张**再动手 | 不替组员分配陈述段落、不代写演讲稿（那是答辩组的活） |
| 演示截图（核心功能界面 / AI 使用 / 测试与流水线）的**取景、裁切、标注与风格统一** | 不改源码去"凑"一张好看的截图；截图必须真实可复现 |
| 维护答辩材料的视觉规范（配色、字体、页眉页脚、图表色板与系统 UI 同一套色） | 不把任务卡 / 提示词 / 项目文档表格的原文原样搬进 PPT——查重红线只对 5 份文档生效，但**同源文本反复出现同样会拉高整包重复率**，答辩时也一眼看得出是机器拼的 |
| 交付前出**可编辑源文件 + 导出件**（PDF 或图片），并留一份页数与结构清单 | 不在代码冻结（T-3 天）之后再改 PPT 里的系统界面截图——那会让截图与代码不一致 |

### 职责五：文档负责

> 阿辉 2026-09-17 指派。**交付物 1~5 合计 70 分**（占总分七成），此前**无任何角色认领**（第 5.2 节 G-1），本职责专门补这个空洞。

| 我负责 | 我不做 |
| --- | --- |
| 五份 PDF：《需求规格说明书》《软件架构设计文档》《测试计划与用例文档》《CI/CD 部署方案》《AI 辅助开发实践报告》（各含页数上限，见 `02-交付物验收清单.md`） | **不改任何代码**（`RuoYi-Vue-fast/src`、`RuoYi-Vue3/src` 一律不碰）——素材找「代码负责」「测试负责」要 |
| 把代码 / 测试 / CI 的**事实**转写成文档（章节结构、图表、表述） | **不编数据**：测试数字找「测试负责」、流水线找「代码负责」，拿不到就标 `[待填]`，不猜 |
| 每份文档开头的 **AI 使用声明**（哪几节 AI 生成、哪几节人工改） | **不把任务卡 / 提示词 / 项目文档的表格原文粘进 PDF**——查重红线只对 5 份文档生效，碰了该项直接 0 分 |
| 在 `docs/大作业/AI留痕/` 写 `D1-*.md`（素材给 AI 报告复用） | 不动 `AGENTS.md` 的 5.1 / 6 / 7 节（那是产品经理的台账） |

### 职责六：测试负责

> 阿辉 2026-09-17 指派，**从「编码负责人」手里划走 T1 / T2**（该角色原扛 T0~T5 六张卡，过载）。
> 划分原则：**写测试的（本职责）** 与 **写被测代码的（职责三）** 分开，避免"自己测自己"。

| 我负责 | 我不做 |
| --- | --- |
| **T1 单元测试 7 → 29 个**（重点补**权限判定 `LabRoleUtils`**——第 7 节 D-07 指出该块 0 覆盖，写错会全系统静默越权） | **不改 `src/main` 生产代码**——测出的缺陷写进第 7.3 节偏移表，交「代码负责」按编号修 |
| **T2 集成测试 0 → 7 个**（`@SpringBootTest` + `MockMvc` + **H2 版本必须写死 1.4.199**，本机离线只有这个版本） | **不为凑数写不校验任何契约的空测试**——断言对象必须能指回 BR 编号 |
| 为《测试计划与用例文档》供**全部数字与截图**（交付物 3，15 分） | 不写五份文档的正文成文（那是「文档负责」的活，你只供料） |
| 在 `docs/大作业/AI留痕/` 写 `T1-*.md`、`T2-*.md` | 不动 `AGENTS.md` 的 5.1 / 6 / 7 节 |

### 汇报与留痕义务

1. **动手前后都要回写**：第 5 / 6 节台账 + 第 7 节偏移。**不许只改代码不记账。**
2. **每张任务卡完成即在 `docs/大作业/AI留痕/` 留记录**，含完整提示词与人工修改点。
3. **身份或职责范围变更时，必须更新本节并主动告知阿辉**——这是身份的根，不能默默改。
4. **不挂任务卡的巡检 / 紧急修复也要留痕**：在 `docs/大作业/AI留痕/` 下用 `巡检-YYYY-MM-DD-*.md` 命名（与 `T?-YYYY-MM-DD.md` 区分），并在第 7 节登记对应偏移编号。**没编号的修复等于没发生。**
5. **视觉物料同样要留痕**：D2 卡（PPT / 海报）的每一轮设计产出都在 `docs/大作业/AI留痕/D2-*.md` 里记「设计输入来源 → 生成的版式 → 人工调整点」。
   其中**「人工调整点」这一栏对《AI 辅助开发实践报告》尤其值钱**——现有留痕几乎全是代码侧的，视觉侧的 AI 协作案例是报告里缺的那一类。
6. **需要阿辉拍板、但不必当场回答的事，一律汇总到 `docs/大作业/待阿辉确认.md`**（统一收件箱），
   **不在六个会话里各问一遍**，阿辉一次看完、一次回复。
   **例外**：对话中必须马上确认才能继续的（如"这个方案选 A 还是 B"）——**就在该会话里直接问**，不进收件箱。
   登记时**必须带「建议」**——只抛问题不给判断的条目，PM 巡检时打回。

---

## 1. 项目简述

**《高校实验室资产与报修管理平台》** —— 基于 **若依 RuoYi-Vue 3.9.2**（前后端分离版）二次开发的实验室资产全生命周期与故障报修闭环管理系统。

**要解决的三个真实痛点**

| 现状 | 后果 |
| --- | --- |
| 台账靠 Excel 手工维护 | 资产调拨、报废没留痕，学期末账实不符，查不出是谁改的 |
| 报修靠微信群喊人 | 谁报的、谁在修、修到第几步全靠记忆，超时没人催 |
| 没有统计数据 | 一学期修了多少次、哪台设备最常坏、维修费花了多少，说不清 |

**技术栈**

| 层 | 技术 |
| --- | --- |
| 后端 `RuoYi-Vue-fast/` | Spring Boot 2.5.15、Java 8、MyBatis、Druid、Redis、Quartz、Spring Security + JWT。单体 jar，端口 8080 |
| 前端 `RuoYi-Vue3/` | Vue 3.5、Vite 6、Element Plus 2.13、Pinia、ECharts 5.6 |
| 数据库 | MySQL 8，库名 `education_system` |
| 自定义业务包 | `com.ruoyi.project.laboratory`（**所有自定义代码只放这里，不污染若依原生包**） |

**四个业务模块**

| 模块 | 能力 | 后端 | 前端 |
| --- | --- | --- | --- |
| 实验室房间 | 编号/名称/学院/管理员；删除前校验房间下是否还有资产 | `LabRoomController` | `views/laboratory/room/` |
| 资产台账 | CRUD、归属房间、状态、Excel 导入导出、二维码标签、全生命周期履历时间线 | `LabAssetController` | `views/laboratory/asset/` |
| 设备报修 | 提交（含故障图片）、审核流转、成本登记、完成后评分、处理时间线 | `LabRepairController` | `views/laboratory/repair/` |
| 运维工作台 | 4 项指标卡 + 4 张 ECharts 图 | `LabDashboardController` | `views/index.vue` |

**报修状态机（硬编码在 `LabRepairServiceImpl.validateStatusChange`，是全系统的核心契约）**

```
0 待审核 ─审核通过→ 1 待维修 ─开始维修→ 2 维修中 ─维修完成→ 3 已完成
   │                                                              │
   └──拒绝──→ 4 已拒绝                        └──→ 提交人评价 1~5 分

资产联动：提交报修 → 资产置 2 维修中；报修完成 / 被拒绝 / 删除待审核单 → 资产回到 0 正常
```

**演示账号**：`labadmin` / `asset01` / `repair01` / `room01` / `student1` / `viewer01`，密码统一 `123456`。

---

## 2. 权威文档索引

上游依据：`C:\Users\王旻辉\Desktop\智能软件工程大作业\软件新技术专题大作业要求.docx`
（纯文本抽取结果在 `.workbuddy/logs/hw_extract.txt`，抽取脚本 `.workbuddy/tools/docx_text.py`）

| 文件 | 作用 | 谁用 |
| --- | --- | --- |
| **`AGENTS.md`（本文件）** | 公共记忆、工作流、分工、进度台账、偏移检查 | 所有人 |
| **`docs/大作业/待阿辉确认.md`** | **统一决策清单（收件箱）**——所有"需要阿辉拍板但不必当场回答"的事项汇总一处，阿辉一次回复 | 各角色**追加**自己那一行；PM 维护与销项 |
| **`docs/大作业/封面署名信息.md`** | **封面署名唯一来源**——五份 PDF + PPT 封面照此填写，避免各处抄出不一致；未确认字段**留空不编** | 文档负责、PPT和设计、代码负责 |
| `docs/大作业/00-项目方向与协作规范.md` | 选题决策、产品定位、范围边界、路线图、三条红线 | 所有人 |
| `docs/大作业/01-需求基线.md` | 5 类 Persona、14 个用户故事、6 组 GWT、**6 条业务规则 = 测试 oracle** | 编码、文档、测试 |
| `docs/大作业/02-交付物验收清单.md` | 7 项交付物 × 任务书硬指标自查表、交作业前核对命令 | 文档组、组长 |
| `docs/大作业/任务卡/T0~T6 + D1/D2-*.md` | **每张卡含可直接复制给对应角色的完整提示词** | 各角色 |
| `docs/大作业/角色prompt/` | **角色创建 / 范围变更 / 正式开工提示词**——新建会话或正式开工时整段粘贴即可（`开工指令.md` = "现在干什么"） | 阿辉、各角色 |
| **`.workbuddy/logs/pm-watch/`** | **PM 自动验收与监督** —— `README.md` = 巡检协议 + 硬指标命令表 + **可写目录边界表**；`_baseline-*.md` = 对比基线；`YYYY-MM-DD-HHmm.md` = 每次快照。**定时任务：每天 09:00 早间布防 / 21:00 晚间验收**（有效至 2026-09-22） | PM（自动）；**各角色被巡检，别改这里** |
| `docs/大作业/AI留痕/README.md` | AI 留痕模板 + AI 使用声明模板（查重红线提醒） | 所有人 |
| `docs/大作业/AI留痕/T?-*.md`、`巡检-*.md` | 每张任务卡 / 每次巡检的**完整提示词、生成结果、人工修改点**——AI 报告的素材来源 | 编码负责人、文档组 |
| `README.md` | 项目技术文档（分层、状态机、SQL 执行顺序、本地运行） | 所有人 |

---

## 3. 时间节点

> ⚠️ **带 `[待填]` 的项只有阿辉能确认**。**答辩日已定：2026-09-22**（阿辉 2026-09-17 确认）。

| 里程碑 | 交付物 | 截止时间 | 状态 |
| --- | --- | --- | --- |
| 分组与选题提交 | 分组表 + 选题（**走选题 D 自拟**） | `[待填]` | ☐ |
| 《需求规格说明书》初稿 | PDF 8–12 页 | `[待填]` | ☐ |
| 完整文档包 | 5 份 PDF + PPT + 源码 ZIP | `[待填]`（建议 **9-20**） | ☐ |
| **最终版提交**（超星平台） | `软件新技术专题-第6组-题目.zip` | **2026-09-21**（答辩前 24 小时） | ☐ |
| **终期答辩** | 10 分钟/组（陈述 3 + 演示 2 + 提问 5） | **2026-09-22** | ☐ |

### 3.1 ⚠️ 时间现实检查（2026-09-17 记，**建议执行**）

答辩 **9-22**、今天 **9-17**，**只剩 5 天**——原倒排口径的 **T-7（9-15）已经错过**，
**不能再照搬"T-7 文档包定稿"**。按下面压缩口径走：

| 日期 | D-x | 当天必须发生的事 |
| --- | :-: | --- |
| 9-17（今） | D-5 | 定表结构（T0）+ 测试基线起跑（T1/T2）+ 文档负责人开写第 1 份 + 首次提交推仓库 |
| 9-18 | D-4 | T0 / T1 / T2 收口；CI（T3）落地；文档继续 |
| 9-19 | D-3 | **冻结代码**；5 份文档进入定稿；PPT 出骨架（不依赖上游的 1 页骨架 + 两张矢量图） |
| 9-20 | D-2 | 打源码 ZIP（清 `target/` `dist/`）；5 份 PDF 定稿；PPT 完成 |
| 9-21 | D-1 | **上传超星**（硬性）；答辩彩排 |
| 9-22 | D 日 | 答辩 |

> **风险提示（PM）**：**文档质量 70 分是最大一块，也是 5 天内推进最快的瓶颈**。
> 建议**优先保三份**——`1 需求`（素材全齐）、`2 架构`（接口/分层已有）、`5 AI 报告`（留痕现成）；
> `3 测试` 与 `4 CI/CD` 依赖 T1/T2/T3 产出，**若时间不够，宁可写薄也不许编数据**。
> 该压缩口径与保底顺序**阿辉 2026-09-17 已确认**（见 `docs/大作业/待阿辉确认.md` Q-13 ✅）。
> **各角色的开工指令**见 `docs/大作业/角色prompt/开工指令.md`。

**倒排规则（原口径；仅当截止日重新调整后适用）**

```
T-7 天   R4 文档包必须全部定稿（5 份 PDF + PPT）
T-3 天   冻结代码，只跑验收命令，不允许再改功能
T-2 天   打源码 ZIP，清 target/ 与 dist/，检查命名
T-1 天   上传超星（硬性：答辩前 24 小时）
T 日     答辩彩排：确认每人陈述段落、演示环境可跑
```

---

## 4. 工作流（每次开工强制走这 6 步）

```
① 读文件      读完 AGENTS.md + **自己角色那一行（第 5.1 节索引）** + 对应任务卡
      ↓
② 核现状      【必须】重新读一遍要改的文件 + 看 git status
              仓库可能被其他会话/组员并行改过，任务卡里的"现状"数字可能已过期
      ↓
③ 认领        在本文件第 6 节台账里把该任务卡状态改成 🔄 并写上执行人
      ↓
④ 执行        只做任务卡「交付内容」范围内的事，严格遵守「边界」段
      ↓
⑤ 自检        跑任务卡「验收方式」里的命令，逐条勾选「交付内容」的复选框
              不达标不许标完成，不许跳过验收命令
      ↓
⑥ 回写        更新本文件第 6 节台账 + 第 7 节偏移检查 + 写 AI留痕
```

### 状态图例

| 符号 | 含义 |
| --- | --- |
| ⬜ | 未开始 |
| 🔄 | 进行中（台账里必须写执行人） |
| ✅ | 已完成（验收命令已跑通，复选框已全勾） |
| ⚠️ | 阻塞（必须写明卡在谁身上、卡在什么决策） |
| ❌ | 发现偏移（已记入第 7 节，未修复） |

### 三条红线（碰了直接扣分或归零）

1. **文档查重率 > 30%（含代码注释）→ 文档项 0 分。** 任务卡里的提示词、表格、说明一律**不许粘进 PDF**，必须重写表述。
2. **AI 使用声明**：每份文档开头必须写清哪几节 AI 生成、哪几节人工改。
3. **答辩全员参与**：每位组员都要陈述，教师随机指定回答人；缺席者期末项目成绩记 0 分。

---

## 5. 角色与分工

> 组员姓名由阿辉补齐。**每人认领后把自己的名字写进下表。**
> AI 助手（Claw）的身份与职责边界见 **0.5 节**，下表只登记它的角色归属，不重复展开边界。

| 角色 | 负责范围 | 对应交付物 | 认领人 |
| --- | --- | --- | --- |
| **PM / 产品负责人** | 选题决策、范围界定、需求基线、任务拆分、提示词设计、偏移把关 | 上游输入，不直接交文档 | AI（Claw） |
| **前端 UI 负责人** | 三个业务页与首页的视觉一致性、交互体验、窄屏兜底、跨页面口径统一；随查随修 | 交付物 6 答辩 PPT 的「核心功能界面」截图；演示观感 | AI（Claw） |
| **编码负责人** | 后端 + 前端功能实现、测试代码、CI/CD、容器化 | 交付物 7 源码 ZIP；为文档 3、4 提供素材 | **AI（Claw）** |
| **答辩材料与视觉设计** | 答辩 PPT 的**版式与视觉**（≤15 页）、海报等视觉物料、演示截图的美化与统一；**只管设计层，不管技术内容** | 交付物 6 答辩 PPT | **AI（Claw）** |
| **需求/架构文档** | 《需求规格说明书》《软件架构设计文档》 | 交付物 1、2 | `[待填]` |
| **测试文档** | 《测试计划与用例文档》 | 交付物 3 | `[待填]` |
| **CI/CD 与 AI 报告** | 《CI/CD 部署方案》《AI 辅助开发实践报告》 | 交付物 4、5 | `[待填]` |
| **答辩与打包（执行）** | **PPT 内容**（技术要点、叙事线、每页讲什么）、演示脚本、陈述段落分配；截图包整理、源码 ZIP 命名 | 交付物 6、7 | `[待填]`（与上一行的分工：**内容 ↔ 设计**） |

### 5.1 任务归口索引（**每个角色开工前只看自己那一行**）

> 阿辉 2026-09-17 定：**每个 AI 会话各领一个角色，任务要求按角色归口**（当前六个：产品经理 / 代码负责 / 测试负责 / 文档负责 / UI设计 / PPT和设计）。
> 开工前只读自己这一行，不操心、也不越界改别的角色的活。
> 「可写目录」是**硬边界**，越界即按第 7 节记偏移——**D-12 就是踩了这条**（无卡改了 `src/main` 与首页）。

| 角色（会话名） | 0.5 节职责 | 任务卡 | 服务交付物 / 得分点 | 完成判据（跑通才算） | 可写目录（边界） |
| --- | :-: | --- | --- | --- | --- |
| **产品经理** | 职责一 | 无卡（**产出任务卡本身**）；维护 `00`/`01`/`02` + 本文件 | 需求侧上游；交付物 1 素材；全量偏移核查 | 14 用户故事 / BR-01~06 无缺项；第 7 节偏移表逐条有状态 | `docs/大作业/*.md`、`AGENTS.md`（**不碰代码**） |
| **代码负责** | 职责三 | **T0、T3、T4、T5** | 交付物 7 源码 ZIP；交付物 2/4 的技术素材 | 各卡「验收方式」命令跑通；`git ls-files \| grep -c node_modules` = 0 | `RuoYi-Vue-fast/src/main/**`、`RuoYi-Vue3/src/**`、`sql/`、`.github/`、`deploy/`（**不写 `src/test`**） |
| **测试负责** | 职责六 | **T1、T2** | 交付物 3 的**全部数字与截图** | `mvnx.ps1 -o -B clean test` 的 `Tests run` 达标且截图留痕 | `RuoYi-Vue-fast/src/test/**`（**不改 `src/main`**） |
| **文档负责** | 职责五 | **D1**（五份 PDF） | **交付物 1~5，合计 70 分** | 五份页数命中各自区间；每份开头有 AI 使用声明；查重自检 | `docs/大作业/文档/**`、`AI留痕/D1-*.md`（**不碰代码、不动第 6 / 7 节台账**） |
| **UI设计** | 职责二 | **T6** | 交付物 6「核心功能界面」截图；演示观感 | T6 卡第 6 节 6 条 grep；`cd RuoYi-Vue3` 后 `vite build` 成功 | `RuoYi-Vue3/src/views/laboratory/**`、`assets/styles/**`（**不改首页 `views/index.vue` 视觉基线**） |
| **PPT和设计** | 职责四 | **D2**、**D5**（D1 定稿后填充 / 等阿辉定用途） | 交付物 6 答辩 PPT；创新分 | 页数 ≤15；色值逐项能在 `laboratory.scss` 找到出处 | `docs/大作业/答辩PPT/**`、`AI留痕/D2-*.md`（**不编技术内容、不改源码**） |

**六角色归口结果**：~~`D1` 五份文档撰写~~ → **已归「文档负责」**（第 12 轮）；~~`D3` 源码 ZIP 打包~~ → **归「代码负责」**（T-2 天打包，含 5 份 PDF，需「文档负责」交件后执行）。**全部卡已无遗留。**

**共享基建目录（2026-09-17 收编，原为「名义越界」灰区）**：

| 目录 | 谁能写 | 说明 |
| --- | --- | --- |
| **`.workbuddy/tools/`** | **任何角色** | 工具脚本（`mvnx.sh` / `mvnx.ps1` / `docx_text.py` / `ui_*.mjs` / `cdp_shot.mjs`）。**只能放工具，不得放业务产出**（源码、文档、任务卡一律不算） |
| **`.workbuddy/memory/`** | **任何角色** | 工作日志与长期记忆。**日志追加写、不覆盖** |
| **`.workbuddy/logs/`** | 写 = 工具输出；**`pm-watch/` 只有 PM 写** | 全目录在 `.gitignore` 内，**不入库** |

> 这三个目录**不需要登记进第 6 节台账**，改动也不构成越界——本条即为其可写依据。**除它们之外，一律按上表的「可写目录」硬边界执行。**

> **角色提示词**：六个角色的**创建 / 约束确认提示词**都在 `docs/大作业/角色prompt/`（含 README 索引）——
> 新会话整段粘贴即可开工，不用口述背景。本目录只是"怎么把角色开起来"，**边界仍以 0.5 节、归口仍以本节为准**。

### 5.2 角色缺口台账

| # | 缺口 | 说明 | 处置 |
| :-: | --- | --- | --- |
| **G-1** | **文档侧无归属** | 交付物 1~5 合计 **70 分**（占总分七成），此前无角色认领 | ✅ **已解决**（第 12 轮）：新增 **⑤ 文档负责** 承接全部 5 份文档 |
| **G-2** | **答辩内容 / 演示无归属** | D2 卡明写「每页讲什么由**答辩与打包（执行）**决定」，而该角色未认领；任务书要求**全员参与陈述** | ⏸️ **阿辉 2026-09-17 决定暂不处理**（不另开 AI 角色；答辩准备期由人类组员认领，见第 3 / 8 节） |

### 已完成工作台账（谁做了什么）

| 时间 | 执行方 | 完成的工作 | 产出文件 |
| --- | --- | --- | --- |
| 历史会话 | 编码（早期） | 四个业务模块前后端实现：房间/资产/报修/看板，含二维码标签、图片上传、Excel 导入导出、履历时间线、首页 4 图看板；六组 SQL 脚本（可重复执行） | `project.laboratory` 包 30 个文件；`RuoYi-Vue3/src/views/laboratory/*`；`sql/laboratory_*.sql` |
| 2026-09-16 | 编码 | 自定义代码迁出成独立包 `com.ruoyi.project.laboratory`；修 4 处角色白名单不一致（看板 403 缺陷）；抽出 `LabConstants`/`LabStatusUtils`/`LabSecurityUtils`/`LabRoleUtils`；补 `.gitignore`、`README.md`；SQL 脚本统一加 `[N/6]` 顺序注释 | 见 README「后端自定义模块分层」 |
| 2026-09-16 | 编码 | 单测从 2 个补到 5 个：新增报修状态机合法/非法全枚举契约测试（25 组矩阵） | `LabRepairServiceImplTest` |
| 2026-09-16 | **PM（AI）** | 抽取并解析任务书 4 份 docx；建立项目管理文档体系；定选题决策（走 D 自拟）；产出 6 张任务卡 | `docs/大作业/` 共 10 个文件 |
| 2026-09-17 | **PM（AI）** | 建立本文件（公共记忆 + 工作流）；完成首次全量偏移核查（见第 7 节） | `AGENTS.md` |
| 2026-09-16 | 编码（AI） | **「查缺补漏」一致性巡检第 1 轮**：沿「后端 `@PreAuthorize` ↔ SQL 菜单权限 ↔ 角色授予 ↔ 前端 `v-hasPermi` ↔ Service 行级过滤」5 层逐层核对，查实并修掉 2 个真实缺陷（见 D-10 / D-11）；另删死代码 1 处、补部分更新校验 1 处、统一 4 处 `@Log` 标题口径、趋势图补零 | `LabRepairServiceImpl` / `LabRoomServiceImpl` / `LabAssetMapper(+xml)` / 4 个 Controller / `laboratory_permission_fix.sql` / `repair/index.vue` / `views/index.vue` |
| 2026-09-16 | 编码（AI） | 单测 5 → 7：新增**状态机全枚举矩阵**测试（25 组有序对 = 4 合法 + 16 非法 + 5 幂等）；期间发现自己写错的期望值（11 → 16）测试当场红灯兜住 | `LabRepairServiceImplTest` |
| 2026-09-17 | **编码（AI）** | **认领编码负责人一职**（本表 + 0.5 节职责三）；逐条回读上文改动确认仍在仓库中；复跑 `mvnx.ps1 -o -B clean test` 得 `Tests run: 7, Failures: 0`，前端 `vite build` 成功 | 本文件 0.5 / 5 / 6 / 7 节；`AI留痕/巡检-2026-09-17-一致性与越权缺陷修复.md` |
| 2026-09-17 | **编码（AI）** | **初始化 git 仓库并挂远程**（解除 D-09）：`git init -b main` + `git remote add origin https://github.com/same-day123/Manager-system.git`；核验分支 `main`、remote `origin` 正确；确认 `.gitignore` 已覆盖 `target/` `dist/` `node_modules/` `logs/`。**首次提交/推送留待阿辉确认范围** | `.git/`（仓库元数据，不入库）；本文件 0.5 / 6 / 7 / 10 节 |
| 2026-09-17 | **视觉设计（AI）** | **认领「答辩材料与视觉设计负责人」**（阿辉指派）：0.5 节新增**职责四**并划清与「前端 UI 负责人」的界线（交付物 vs 系统界面）；第 5 节分工表把「答辩与打包」拆成**内容 / 设计**两行；第 6 节 D2 卡改由视觉设计认领；新建 D2 任务卡（含 7 段全文 + 提示词） | 本文件 0.5 / 5 / 6 节；`docs/大作业/任务卡/D2-答辩PPT与视觉物料设计.md` |
| 2026-09-17 | **产品经理（AI）** | **任务要求按角色归口**（阿辉定：四个 AI 会话各领一角色）：新增 **第 5.1 节「任务归口索引」**（角色 → 任务卡 → 交付物 → 完成判据 → **可写目录硬边界**）与 **第 5.2 节「角色缺口」**（G-1 文档侧 70 分无归属 / G-2 答辩内容无归属）；第 6 节台账加「角色」列并按角色归组；第 0 节、第 4 节工作流第 ① 步同步改为「按角色读」 | 本文件 0 / 4 / 5.1 / 5.2 / 6 节 |
| 2026-09-17 | **产品经理（AI）** | **角色扩到六个**（阿辉定：G-2 不处理，新增两角色分担「代码负责」过载）：0.5 节新增 **职责五 文档负责**（交付物 1~5）与 **职责六 测试负责**（T1/T2），并把 T1/T2 从职责三划走；5.1 索引新增两行、代码负责收敛为 T0/T3/T4/T5；5.2 缺口 G-1 销项（G-2 挂起）；第 6 节台账同步改角色。另产出 **3 份角色提示词**（`docs/大作业/角色prompt/`）与 **D1 任务卡** | 本文件 0 / 0.5 / 5.1 / 5.2 / 6 节；`docs/大作业/角色prompt/*`；`docs/大作业/任务卡/D1-五份文档撰写.md` |
| 2026-09-17 | **文档负责（AI）** | **认领 D1**（五份文档，**70 分**）。**第 0 轮为只读盘点，按阿辉指令未写任何正文**：读完 11 份材料并跑工作流第 ② 步「核现状」——单测 7 / 集成 0 / 无 `.github/workflows` / 无 Dockerfile / `sql/` 仍缺三张主表 DDL / `docs/大作业/文档/` 不存在，**与台账一致**；产出**逐文档逐节素材来源表与缺口清单**（测试数字等 T1/T2、流水线等 T3/T4、设计模式类图等 T5、表字段类型等 T0）。**新登记 2 项问题 → 7.3 的 D-16 / D-17**。另：阿辉补齐**交付物署名信息**（第 **6** 组；成员 王旻辉、巴力江·托力肯别克、廖煌、迪丽热巴·阿布都西克尔；班级 软件工程 2023-2；授课教师 **谭清萱**；学号先记 1 个 2311231056）、**《AI 报告》工具清单**（deepseekV4flash / GLM5.3flash / VSCode / WorkBuddy）、**五份文档写作顺序已批准**（1 需求 → 2 架构 → 5 AI 报告 → 3 测试 → 4 CI/CD） | `docs/大作业/AI留痕/D1-2026-09-17.md` |
| 2026-09-17 | **文档负责（AI）** | **第 1 份《需求规格说明书》初稿**（源稿 Markdown，**未导出 PDF**）：按已批准的写作顺序开工，硬指标逐项命中——愿景板 **5 维** / 角色 **5 类** / 用户故事 **14 条**（严格「作为〈角色〉，我想要〈功能〉，以便〈价值〉」）/ 验收标准 **6 组 GWT（15 场景）** / INVEST 含 2 项改进 / 非功能需求 **5 类**（未实测项一律标「未验证」「设计目标」）/ 术语表 8 条 / **开头含 AI 使用声明**；**未写「明确不做」清单内功能**（预约模块、小程序端等），附录 A 的 14 条与 7.2 节实现对照逐条一致。待办：人工改写表述防查重 → 补封面 `[待填]` → 导出 PDF 核页数（8–12） | `docs/大作业/文档/01-需求规格说明书.md` |
| 2026-09-17 | **产品经理（AI）** | **首次提交 + 推送 GitHub 成功**（**D-09 彻底收口**）：排查并修复**嵌套仓库 / gitlink（mode 160000）问题**——`RuoYi-Vue-fast/`、`RuoYi-Vue3/` 目录里各自带着从上游 clone 来的**内层 `.git`**，导致首次提交只进了 38 个文件、**全部源码被当成 gitlink 跳过**；把两个内层 `.git` **移动**到 `D:\code\_Manager_system_git_backup\`（备份可逆、未删除），`git rm -r --cached` 掉 gitlink 后重跑 `git add -A` → **0 个 gitlink、0 个 node_modules**，`commit --amend` 得 **699 文件 / 82212 insertions**，确认含 `RuoYi-Vue-fast/src/main/java/com/ruoyi/...` 等真实源码；`push origin main` 成功，远端 `refs/heads/main` = 本地 HEAD `57c81b9`（`git ls-remote` 核验一致）。`.workbuddy/logs`、`target/`、`dist/`、`node_modules/` 与任何密钥**均未入库** | `.git/`（仓库元数据不入库）；本文件第 6 / 7 / 10 节 |
| 2026-09-17 | **代码负责（AI）** | **T0 完成（补齐建表脚本，解除 D-02 / D-13 / D-14 / D-15）**：① 新建 `laboratory_schema.sql`（`[1/7]`，5 张业务表，字段逐一对照三个 Mapper 的 `resultMap`，另附「与线上库的差异」6 条）；② 种子数据并入——房间 5 条（插在资产之前，修掉 `room_id` 悬空）、报修 4 条 + 处理记录 1 条（申请人挂 `student1`、相对日期）；③ 6 个脚本头部重编号 `[N/6]`→`[N+1/7]`，`ry` 补顺序注释；④ README 执行顺序改 7 行 + 3 个按需维护脚本说明；⑤ 新建幂等迁移 `laboratory_index_fix.sql` 并**在真实库执行**（删掉与逻辑删除冲突的 `uni_asset_code` / `uni_repair_code`、补齐 5 个普通索引）。**空库重建实测零报错、`lab%` 恰好 5 表** | `RuoYi-Vue-fast/sql/`（新 2 个 + 改 7 个）、`README.md`；`AI留痕/T0-2026-09-17.md` |
| 2026-09-17 | **文档负责（AI）** | **第 2 份《软件架构设计文档》初稿**（源稿 Markdown，**未导出 PDF**）：硬指标逐项命中——分层架构图 **4 层**（展示/业务/持久化/数据库）／SOLID **4 处且均指到具体类**（SRP→`LabRepairServiceImpl`+`LabRoleUtils`；OCP→`LabRoleUtils`+`LabConstants`；ISP→4 个独立 Service 接口；DIP→`Controller→ILabXxxService→Impl`；**LSP 不适用并照实说明**）／核心模块方法签名 **29 个**（超额，含看板接口）／**ER 图**（mermaid）／方法/端点清单 28 个与注解检索**逐条一致**／**开头含 AI 使用声明**。**随 T0 落地即时回填第 7 章**：字段名/类型/长度/默认值/索引**逐字取自 `laboratory_schema.sql`**（不再留 `[待填]`），并把「业务编号不建唯一索引」写成架构级决策（7.3 第 3 条 + 附录 B）。**第 5 章设计模式仍留骨架等 T5、第 8 章类生产/生产环境等 T3/T4**。自检阶段改准 3 处口径（`lab_asset` 15 自有+1 关联；`lab_repair` 23 自有+3 关联；两张记录表实为 **10** 字段、原写 9） | `docs/大作业/文档/02-软件架构设计文档.md`；`docs/大作业/AI留痕/D1-2026-09-17.md` 第 12 节 |
| 2026-09-17 | **测试负责（AI）** | **T1 完成（单测 7 → 30，解除 D-07）**：新建 `support/LabTestSupport`（登录上下文样板，避开 `userId=1` 即超管、`LoginUser` 必须 4 参数构造两个陷阱）与 `util/LabRoleUtilsTest`（D 组权限口径 5 个，含「处理角色 ⊆ 全局可见角色」结构性包含断言）；扩展三个 Service 测试类补齐**三个零覆盖区**——提交校验 5、评价规则 2、资产校验 7、房间校验 4，新增用例**全部走公开入口**、断言对象均可指回 BR-01~06。验收 `Tests run: 30, Failures: 0, Errors: 0` / `BUILD SUCCESS` 01:46。**实得 30 而非卡里的 29 —— 卡 B 组清单列了 7 条却把小计写成 6**，留痕第 3 节记明「以清单为准、不砍覆盖率」。`src/main` 零改动、未新增任何依赖。另：本轮查到**并行会话并发跑 Maven 会互踩 `target/`**，已在留痕与 MEMORY 里留下处置办法 | `RuoYi-Vue-fast/src/test/**`（1 新建支撑 + 1 新建用例类 + 3 扩展）；`docs/大作业/AI留痕/T1-2026-09-17.md`；`docs/大作业/AI留痕/截图/` 2 个 |
| 2026-09-17 | **测试负责（AI）** | **T2 完成（集成测试 0 → 7，解除 D-03）**：新建 `integration/AbstractLabIntegrationTest`（公共骨架：清表 / 铺基线 / `JdbcTemplate` 直查库断言工具）+ 两个用例类（`LabRepairIntegrationTest` IT-01~04/06、`LabAssetIntegrationTest` IT-05/07）+ `support/LabIntegrationTestApplication`（最小上下文：排 `Redis`×2 与 `Security`，**不挂 `RuoYiApplication`**，避开 Quartz/Druid/SysConfig/Redis 真中间件）+ `resources/sql/lab-schema-h2.sql`（T0 的 `laboratory_schema.sql` 逐字段转 H2 方言，5 表）+ `application-integration.yml`；pom 加 `com.h2database:h2:1.4.199`（**test scope，版本写死**，Boot 托管的是 1.4.200 本机没有）。**7 个用例首次构建即全绿**，全量 `Tests run: 37, Failures: 0, Errors: 0` / `BUILD SUCCESS` 01:48。**断言一律用 `JdbcTemplate` 直查库**，不信 Service 返回值；按卡禁止项**未加 `@Transactional`**、未引入 Testcontainers、**`src/main` 零改动**。**H2 兼容性结论已实测**：`sysdate()`（仅日精度）/`now()`/`limit 1`/内联 `key idx`/中文/`left join` 全可用；`date_format()` 与 `date_sub()` **不支持** → 看板无法覆盖，**新登记 D-19**。另完成一轮**对照实验**：生产 `application.yml` 的 `spring.profiles.active: druid` 实测由 `@ActiveProfiles` 自身压住，故测试侧那份 `application.yml`（卡外第 5 个文件）**是防御层而非必需** —— 已在文件头如实写明，并顺带发现 `mvn test` 不复现 `clean test`（`target/test-classes` 残留） | `RuoYi-Vue-fast/src/test/**`（6 新建 + 1 骨架）、`RuoYi-Vue-fast/pom.xml`（+1 依赖）；`docs/大作业/AI留痕/T2-2026-09-17.md`；`docs/大作业/AI留痕/截图/` 2 个 |
| 2026-09-17 | **UI设计（AI）** | **T6 完成（前端 UI 统一与体验修复收口）**：① 第 6 节 **6 条验收 grep 全绿** + `vite build` `✓ 2545 modules / 43.30s / EXIT=0`（无 error 无 warning、`dist/` 已清）；② 把第 10.3 节 **4 项「静态代码永远证伪不了」的目视项全部量到实测值**——起真实环境（MySQL + Redis + 后端 8080 + 前端 5173 + Chrome CDP）逐项测：**V-1** 横滚 411px 到底固定列 sticky 保持、单元格右边界=表头右边界=视口右缘（**`overflow:hidden` 不破坏固定列**）；**V-2** 折叠态工具栏行 `border-top 1px` + 四角 `8px`（属性选择器命中）；**V-3** 1920 下容器 1678 = 渲染列宽 1678、**无横滚**；**V-4** 补测（见④）；③ 采集 **4 张答辩素材截图**（首页看板 / 资产台账 / 资产履历时间线 / 报修处理时间线，1920×1080、统一 admin 取景）+ 5 张验收证据图，**9-19 冻结前必须用掉**；④ **V-4 极端态用「浏览器内注入合成行」零副作用补测**（不写库）：`详情/处理/删除` 各 54px、合计 **186px < 220px**、纵向 top 唯一（**单行不换行**）、整表仍无横滚，量完移除并复核消失；⑤ 卡内修 `.pagination-container` 白底权重（若依白底在组件 `scoped` 里、同权重后加载 → 必须 `!important`）；⑥ 连带修 **D-22**（asset 操作列 260→320，4 按钮实测需约 306px，行高 63→41px）；⑦ 按实测改正 `element-ui.scss` 关于窄屏弹窗的错误注释（原写"按钮滚不到"**是错的**，实测 `.el-overlay-dialog` 自带 `overflow:auto` 能滚到；`max-width` 真正处理横向溢出、门槛视口 < 892px）；⑧ 新登记 **D-20**（演示账号口令与全部文档不符：文档 `123456`／实际 `admin123`，**按文档登录必失败**）、**D-21**（真实库缺 `laboratory:dashboard:view` 菜单 → **非超管首页看不到看板**，交代码负责跑 `permission_fix`） | `RuoYi-Vue3/src/views/laboratory/{repair,asset,room}/index.vue`、`RuoYi-Vue3/src/assets/styles/{laboratory,element-ui}.scss`；`docs/大作业/任务卡/T6-*.md` 第 11·12 节；`docs/大作业/AI留痕/T6-2026-09-17.md` + `AI留痕/截图/T6-核心功能界面/` 9 个 |
| 2026-09-17 | **测试负责（AI）** | **T1/T2 加固轮 + 交件（用例 37 → 49，解除 D-07 遗留、D-19）**：① **D-19 改判**——原记录"看板在 H2 上跑不了、只能容忍"，本轮经 **3 版 H2 探针实测**发现可解：H2 1.4.199 支持 `create alias <名> for "全限定类名.静态方法"`，在**测试侧注册两个同名函数**（`H2MySqlDateFuncs` 函数体 + `H2MySqlCompat` 注册器）即可让生产 SQL **一字未改**地跑通；新建 `LabDashboardIntegrationTest` **DB-01~DB-07 共 7 个用例**，把看板 **8 条 mapper 查询**（`selectSummary` 4 指标 + `selectCharts` 4 图）全部覆盖，含窗口边界（第 6 天计入 / 第 7 天剔除）与 limit 8 兜底。**★ 绝没改生产 mapper XML**（T2 卡第 4 节明令）。② **D-07 遗留收口**——**保留**原有 5 个反射版状态机用例（契约层，不删断言），**新增** `LabRepairStatusMachinePublicEntryTest` **3 个用例**走 `updateLabRepair` 公开入口（入口层），断言目标 status 与旧 assetId 真的被交给持久层、非法流转整轮零写库、状态不变记「维修信息更新」。③ 补 **IT-09 / IT-10** 两个终态用例（已完成不可回退 3→2、已拒绝不可复活 4→0）。④ **交件**：新建 `AI留痕/测试素材交付.md`（真实数字 / 覆盖率证据 / 命令 / **49 个用例的 BR-01~06 追溯矩阵**）+ `AI留痕/T2b-2026-09-17.md`（含 WMIC / `PpidChecker` 噪声根因定位）。⑤ 独立复跑 **两次**均 `Tests run: 49, Failures: 0, Errors: 0` / `BUILD SUCCESS`（01:38 / 02:02）。**`src/main` 零改动、零新增依赖**。⑥ 两条**如实说明已写进交付文档**：行/分支覆盖率**出不来**（离线仓库缺 `jacoco-maven-plugin` / `org.jacoco.core`）、构建日志里的 `IllegalStateException: Cannot use PPID ... NOOP events` 是 WMIC 被安全策略拦截后 Surefire 自行降级，**不影响测试** | `RuoYi-Vue-fast/src/test/**`（3 新建 + 3 修改）；`docs/大作业/AI留痕/测试素材交付.md`、`T2b-2026-09-17.md`、`截图/T2b-*` |
| 2026-09-17 | **代码负责（AI）** | **T3 完成（CI 流水线 + 金丝雀部署，D-16 销项、D-04 的 CI 侧解除）**：① **`.github/workflows/ci.yml`**——`backend` / `frontend` / `package` 三个 job（`package` 用 `needs: [backend, frontend]`），触发 `push main` + `pull_request` + 手动 `workflow_dispatch`；`actions/setup-java@v4`（temurin **8**，与 pom 的 `java.version=1.8` 及生产镜像一致）+ `cache: maven`；`actions/setup-node@v4`（**22**）+ `cache: yarn` + `cache-dependency-path: RuoYi-Vue3/yarn.lock`（**仓库只有 yarn.lock、没有 package-lock.json，所以不能用 `npm ci`**）；三个 job 全部上传 artifact（surefire 报告 / `dist/` / `ruoyi.jar`）；镜像 job **整段注释保留**、凭据只走 `secrets`、测试阶段**不加** `-DskipTests`；② **`deploy/local-pipeline.ps1`**——6 阶段与 job 一一对应，Maven 走 `-l` 原生落盘（不套管道）、**并行 `target/` 冲突自愈重试**、日志即时 flush、`[n/6] 名称 ... PASS (秒)` + 汇总表（中文按 2 列宽对齐）、`SKIPPED` 永不伪装成 `PASS`；③ **README** 顶部加 CI 徽章、新增「CI/CD」章节（job 表 / 本地等价脚本用法 / 三环境表 / 金丝雀选型理由），并把测试数字 37 → **49**（单测 33 + 集成 16，含新增的看板集成测试）；④ 新增结构自检工具 `.workbuddy/tools/ci_yml_check.py`（离线无 actionlint 时的替代，校验 Tab/缩进/顶层键/job 结构/版本固定/凭据/测试阶段）。**验收实测**：`PASS 4 / SKIPPED 2 / FAIL 0`、`EXIT=0`、`Tests run: 49, Failures: 0, Errors: 0`（111.6s）、vite `✓ 2545 modules / built in 42.71s`、`ruoyi.jar` 85.1MB、总耗时 216.2s；`git ls-files` 里 `node_modules`/`target`/`dist`/`.workbuddy/logs` **各 0 行**。**过程中定位并登记 D-26**（沙箱拦 WMIC → Surefire 的 `PpidChecker` 探测失败 → 概率性拆整棵命令树，同一脚本跑 7 次有 4 次能跑完） | `.github/workflows/ci.yml`、`deploy/local-pipeline.ps1`、`README.md`；`AI留痕/T3-2026-09-17.md` + `AI留痕/截图/T3-本地流水线运行结果.txt` |
| 2026-09-17 | **文档负责（AI）** | **PDF 流水线打通（阿辉「第二轮」指令的第一优先级）**：全仓此前 **0 份 PDF**，而 9-20 即打包日。本机**无 pandoc / LaTeX / wkhtmltopdf** 且离线不可装，改走**零依赖**路径：`Markdown →(自写极简 GFM 渲染器)→ 打印用 HTML →(Chrome 152 无头 + CDP `Page.printToPDF`)→ PDF`，复用 T6 已有的 CDP 手法（Node 22 自带 `WebSocket`/`fetch`）。新建 `.workbuddy/tools/md2pdf/` **5 个脚本**：`md2pdf.mjs`（渲染 + 打印 CSS + 一次启动浏览器批量出 PDF）、`pdfinfo.mjs`（**不装依赖**，从 PDF 字节读页数 / `FontFile2` 嵌入数 / `ToUnicode` 可复制数）、`build.mjs`（批量编排 + **页数区间 PASS/FAIL 判定** + UTF-8 报告）、`build.ps1`（纯 ASCII 包装器，绕开 PS 5.1 按 GBK 解 `.ps1` 中文的坑）、`pagemap.mjs`（**量每章落在第几页**，定位超页罪魁）。**验收：3 份 PDF 全部命中区间 → `ALL PASS`**（01 需求 **8** 页 / 02 架构 **13** 页 / 05 AI 报告 **6** 页），字体已嵌入（5 / 8 / 5 个 `FontFile2`）、文字可复制（76 / 87 / 55 个 `ToUnicode`）。**封面 `[待填]` 的排版效果已试出**（统一渲成填空线，字段一处未编）。过程返工 **4 次**已全部登记（02 首版 19 页超限 → 收紧 CSS + 7.2 表压两列 → 13 页；05 盲删两轮无效 → 写 `pagemap.mjs` 定位到「表格 `page-break-inside:avoid` 造成整页留白」→ 定点减内容 8→7→7→**6** 页；Chrome 偶发启动失败 → 改整批只起一次浏览器 + 4 次重试；CDP 定时器泄漏致进程 120s 不退出 / PowerShell 吞 stdout 两处「假故障」） | `.workbuddy/tools/md2pdf/{md2pdf,pdfinfo,build,pagemap}.mjs`、`build.ps1`；`.workbuddy/logs/md2pdf-build.txt`、`md2pdf-pagemap.txt`；`AI留痕/D1-2026-09-17.md` 第 13 节 |
| 2026-09-17 | **代码负责（AI）** | **T5 完成（设计模式落地，D-05 销项）**：① 新增 `constant/LabAssetEvent`（8 个资产履历事件 + **`ofAssetStatus`** 状态码反查）、`constant/LabRepairEvent`（5 个报修履历事件）、`constant/LabRecordFactory`（**简单工厂**：`public final` + 私有构造器 + 2 个静态方法；**无 Spring 注解 / 不写库 / 不加缓存与日志** → 单测无需 Spring 上下文）；② 两个 Service 的 **6 处 + 4 处语句（5 个事件分支）** 从「手工 `new` + 手写动作名 + 手拼文案 + 手做操作人兜底」改为「**只声明事件**」，原 `insertAssetRecord` / `insertRepairRecord` 保留为**一行薄转发**（卡的「二选一」，不留两套并行实现）；③ `LabStatusUtils.assetRecordType` **保留且行为不变**，改为委托 `LabAssetEvent.ofAssetStatus(...).actionName()` —— 「状态码 → 动作名」判定规则**唯一来源 = 枚举**（**未采用卡给的两个写法**，理由见留痕 4-④：留成死方法会变成"两张表"，枚举复用 util 会造成字符串往返 + 包级循环）；④ `docs/大作业/设计模式-类图.md`（Mermaid 类图 + 选型理由 + **为什么不选状态模式** + 架构文档取用提示）+ README 补「设计模式」一节与后端分层目录。**行为不变性三层证据**：`mvnx.ps1 -o -B clean test` → `Tests run: 49, Failures: 0, Errors: 0` / `BUILD SUCCESS`（01:41，**用例数与改造前一致**）；**静态字面量级比对**（`t5_literal_proof.py`，基线 `git show HEAD:`，判据 = 字面量内容精确相等）→ 15 个迁移字面量 + 4 条合成模板 + 3 个派生动作名**逐字节迁移**、全角引号 `U+201C/U+201D` 字节级确认、Service 零残留；**动态探针**（`T5EventProbe.java`，脱离 Spring／无数据库）→ **62 项断言全过**；运行期集成测试 SQL 参数实测到 `报修状态由“待审核”变更为“已拒绝”` 渲染正确。**⚠️ 查出任务卡自身一处错误**：卡给的 `TRANSFER` 模板 `"资产所属实验室调整：%s → %s"` 与真实代码不符（真实代码该文案**不带参数**，实验室名写在 from/to 两列），按卡第 2.3 节「行为逐字不变」红线**以代码为准** —— **若照卡执行会引入一个 49 个用例都测不出的行为变更**（留痕第 4 节①）。**边界**：`src/test`／`src/main/resources`／`domain`／前端／`pom.xml` **零改动** | `RuoYi-Vue-fast/src/main/java/com/ruoyi/project/laboratory/constant/{LabAssetEvent,LabRepairEvent,LabRecordFactory}.java`、`service/impl/{LabAsset,LabRepair}ServiceImpl.java`、`util/LabStatusUtils.java`、`README.md`、`docs/大作业/设计模式-类图.md`；`.workbuddy/tools/{t5_literal_proof.py,T5EventProbe.java}`；`AI留痕/T5-2026-09-17.md` |
| 2026-09-17 | **文档负责（AI）** | **第 5 份《AI 辅助开发实践报告》初稿 + 导出 PDF**（**6 页，PASS**，区间 4–6）：硬指标逐项命中——**工具说明**（DeepSeek V4 Flash / GLM 5.3 Flash / VS Code / WorkBuddy 四行 + 非 AI 配套工具链 + 六角色分工表）／**3 处完整提示词 + 生成结果**（① 计划外一致性巡检「查缺补漏上」+ 流程纠偏 ② 测试加固：让看板查询在 H2 上跑起来 ③ 离线 Markdown→PDF 流水线；其中两条为**按要点逐段还原**并已标注）／**人工审查修改点 6 处**（3.1 汇总 4 处 + 3.2 三个典型案例深度展开：AI 把非法流转断言算成 11 实为 16／AI 越界改生产代码被纠正的是流程／AI 给出的"弹出按钮滚不到"因果链实测为假）／**效率对比**（5 个工作包 × 纯人工预估 / AI 实际 / 返工 / 人工修改量）／**开头含 AI 使用声明**。素材全部取自 `AI留痕/` 11 份记录，**无一项自编**；为压页数删掉的 2 处（3.1 表 8→4 行、1.4 留痕表改散文）已在留痕如实登记，**四项硬指标内容一处未砍**。待办：人工改写第 1/4/5 章叙述防查重 → 补封面 → 重新导出 | `docs/大作业/文档/05-AI辅助开发实践报告.md` + `docs/大作业/文档/pdf/05-AI辅助开发实践报告.pdf`；`AI留痕/D1-2026-09-17.md` 第 14 节 |
| 2026-09-17 | **代码负责（AI）** | **T4 完成（容器化交付，D-04 销项）**：① **后端镜像** `RuoYi-Vue-fast/Dockerfile` —— 两段式（`maven:3.9-eclipse-temurin-8` 构建 → `eclipse-temurin:8-jre-jammy` 运行）；非 root（**显式 `groupadd` 再 `useradd`** —— 只写 `useradd -m` 在部分基础镜像上不会建同名组，后续 `chown ruoyi:ruoyi` 会失败）；`TZ=Asia/Shanghai` 并写 `/etc/timezone`（看板按日期聚合，容器留在 UTC 会让「今日/近 7 天」整体偏移）；`HEALTHCHECK` 用 bash `/dev/tcp` 探 8080（项目未引 actuator，没有 `/actuator/health`）；`ENTRYPOINT` **exec 形式**让 java 做 PID 1（可收 SIGTERM 优雅停机；shell 形式会等到超时才被 SIGKILL）；先拷 `pom.xml` + `dependency:go-offline` 吃层缓存。② **前端镜像** `RuoYi-Vue3/Dockerfile` —— `node:22-alpine` 构建 → `nginx:alpine` 运行；**必须走 yarn**（仓库只有 `yarn.lock`，`npm ci` 因缺 `package-lock.json` 直接失败），并核实锁文件里 **`sass-embedded-linux-musl-x64` 与 `-linux-x64` 两种预编译产物都在**（alpine 与 debian 系都能装通）。③ **`RuoYi-Vue3/nginx.conf`** —— `location ^~ /prod-api/` + `proxy_pass http://lab-backend:8080/` **末尾斜杠剥离前缀**（漏了所有接口 404 且很难查）；**补了卡示例漏掉的 `client_max_body_size 20m`**（nginx 默认 1m，故障照片会被拦成 413）；SPA `try_files` 回落、带 hash 资源长缓存 + `index.html` 不缓存、`gzip` + `gzip_static` 复用 vite 预压缩产物。④ **`docker-compose.yml`** —— 四服务；`depends_on` 用**长格式 `condition: service_healthy`**（只写服务名只保证启动顺序，后端会在 MySQL 初始化期间连库失败直接退出）；mysql **只挂一个显式排序的 `mysql-init.sh`，绝不把 `.sql` 直挂 initdb 目录**；上传目录用**命名卷**（Docker 会用镜像内目录属主初始化空卷，bind mount 会变成宿主属主 → 非 root 进程写不进去）；三处环境变量覆盖；mysql 时区用**数字偏移 `+08:00`**（命名时区要求先导时区表，会直接启动失败）。⑤ **`deploy/mysql-init.sh`** —— 顺序坑的解法，另处理两个隐藏风险：**不用 `set -e`**（initdb 目录的 `.sh` 无执行位时官方 entrypoint 是 `. "$f"` **source** 进来的，`set -e` 会漏进 entrypoint 自己的 shell）与 **root 连接方式实测后再走**（各镜像版本 initdb 阶段 root 口令时有时无）；末尾加自检，初始化没成就让容器直接失败。⑥ **`docker-compose.prod.yml`**（`!override` 去端口暴露 + 口令必填 + 资源/日志上限）与 **`.env.example`**；`.gitignore` 增 `.env`（**刻意不写 `.env.*`** —— 那会把 `RuoYi-Vue3/.env.production` 一起忽略掉，前端构建就错了）。⑦ `ci.yml` 的 **`docker` job 解注释启用**（buildx，只 build 不 push，附 `docker compose config` 语法门禁）。**验证（诚实口径：本机无 Docker，`docker compose build/up/ps` 未执行）**：`.workbuddy/tools/t4_verify.py` **94 条静态断言全绿**（含 SQL 顺序**三方互校**：`mysql-init.sh` 的 `for` 列表 ↔ 磁盘 7 个文件 ↔ 各脚本自带 `[N/7]` 标记逐位吻合，并反向证实「字母序 ≠ 正确顺序」）+ `.workbuddy/tools/t4_env_binding_check.py` **23 条绑定断言全绿**（Spring **真实属性源机制**、两套不同取值对照，`raw=localhost` → `eff=redis`；再证 `application*.yml` **一字未改**）+ `.gitignore` 正反两面实测。**新登记 D-27（卡的环境变量清单漏 redis host/port、nginx 示例漏上传上限）/ D-28（无 Docker → 卡的截图要求无法产出）** | `RuoYi-Vue-fast/{Dockerfile,.dockerignore}`、`RuoYi-Vue3/{Dockerfile,nginx.conf,.dockerignore}`、`docker-compose.yml`、`docker-compose.prod.yml`、`deploy/mysql-init.sh`、`.env.example`、`.gitignore`、`.github/workflows/ci.yml`、`README.md`、`AGENTS.md`；`.workbuddy/tools/{t4_verify.py,T4EnvBindingProbe.java,t4_env_binding_check.py}`；`AI留痕/T4-2026-09-17.md` |

---

## 6. 进度台账

> **任务卡路径**：`docs/大作业/任务卡/`。**状态变更由执行人在本表内改，不要另开文件。**

| 卡号 | **角色** | 任务 | 优先级 | 依赖 | 服务哪项交付物 / 得分点 | 状态 | 完成日期 |
| :-: | --- | --- | :-: | :-: | --- | :-: | --- |
| **T0** | 代码负责 | 补齐数据库建表脚本 | P0 | — | 交付物 7 可复现性；架构文档表结构章节；T2 前置 | ✅ | **2026-09-17 完成**：新建 `laboratory_schema.sql`（[1/7]，5 张业务表）+ `laboratory_index_fix.sql`（幂等迁移）；种子数据并入（房间 5 / 报修 4 / 处理记录 1）；连带重编号 6 个脚本、README 改 7 行；**空库重建实测 5 表全在、零报错** |
| **T1** | **测试负责** | 单元测试 7 → 29 个 | P0 | T0 | 测试文档「单元测试 ≥10」 | ✅ | **2026-09-17 完成**：实得 **30**（卡自身小计口径不一致，见留痕第 3 节修改点 1）；`Tests run: 30, Failures: 0, Errors: 0` / `BUILD SUCCESS` 01:46；新增 D 组 `LabRoleUtilsTest`(5) + `support/LabTestSupport`；**`src/main` 零改动、未加任何依赖**。留痕：`AI留痕/T1-2026-09-17.md`。<br>**🔁 加固轮后（T2b）单测 30 → 33**：+3 个 `LabRepairStatusMachinePublicEntryTest`（状态机走 `updateLabRepair` 公开入口，收口 D-07 遗留）；全量 `Tests run: 49` |
| **T2** | **测试负责** | 集成测试 0 → 7 个 | P0 | T0、T1 | 测试文档「集成测试 ≥5」 | ✅ | **2026-09-17 完成**：实得 **7**（`LabRepairIntegrationTest` IT-01~04/06 五个 + `LabAssetIntegrationTest` IT-05/07 两个）；全量 `Tests run: 37, Failures: 0, Errors: 0` / `BUILD SUCCESS` 01:48，**首次构建即全绿**。新增 `integration/`（2 用例类 + 1 公共骨架）、`support/LabIntegrationTestApplication`、`resources/sql/lab-schema-h2.sql`（H2 方言 5 表）、`application-integration.yml`、`application.yml`（卡外防御配置，见留痕修改点 1）；pom 加 `h2:1.4.199` **test scope**。**`src/main` 零改动**、未加 `@Transactional`。留痕：`AI留痕/T2-2026-09-17.md`。<br>**🔁 加固轮后（T2b）集成 7 → 16**：+7 看板（`LabDashboardIntegrationTest` DB-01~DB-07，**D-19 收口：看板 8 条查询全覆盖，生产 mapper XML 一字未改**）+ 2 终态用例（IT-09 / IT-10）；全量 `Tests run: 49, Failures: 0, Errors: 0` / `BUILD SUCCESS` 01:38，**独立复跑两次结果一致** |
| **T3** | 代码负责 | CI 流水线 + 金丝雀部署 | P1 | T1、T2 | CI/CD 文档 10 分；答辩演示截图 | ✅ | **2026-09-17 完成**：① 新增 `.github/workflows/ci.yml`——`backend`（`mvn -B clean test`）/ `frontend`（`yarn install --frozen-lockfile` + `yarn build:prod`）/ `package`（`needs: [backend, frontend]`，`mvn -B package -DskipTests`）三个 job，JDK 8 temurin + Node 22 + Maven/Yarn 缓存 + artifact 上载，镜像 job **注释保留**待 T4；**测试阶段无 `-DskipTests`**、凭据只走 Secrets；② 新增 `deploy/local-pipeline.ps1`（6 阶段与 job 一一对应；Maven 用自带 `-l` 落盘、并行冲突**自愈重试**、日志即时 flush、中文/ANSI 归一、汇总表按 2 列宽对齐）；③ README 加 CI 徽章 + CI/CD 章节（job 表 + 三环境表 + 金丝雀选型理由）。**验收实测**：`PASS 4 / SKIPPED 2 / FAIL 0`、`EXIT=0`、`Tests run: 49, Failures: 0, Errors: 0`、vite `✓ 2545 modules / built in 42.71s`、`ruoyi.jar` 85.1MB、总耗时 216.2s；阶段 5/6 按本机环境 **SKIPPED（不伪装 PASS）**；`ci.yml` 结构自检（`.workbuddy/tools/ci_yml_check.py`）通过；`git ls-files` 中 `node_modules`/`target`/`dist`/`.workbuddy/logs` 均为 **0**。留痕：`AI留痕/T3-2026-09-17.md` |
| **T4** | 代码负责 | 容器化交付 | P2 | T3 | CI/CD 文档「Dockerfile 加分项」 | ✅ | **2026-09-17 完成**：产出 **8 个新文件 + 1 个 prod 覆盖层 + 1 份 env 模板** —— `RuoYi-Vue-fast/Dockerfile`（两段式 `maven:3.9-eclipse-temurin-8` → `eclipse-temurin:8-jre-jammy`；**非 root**：显式 `groupadd` 再 `useradd`，避免 `chown ruoyi:ruoyi` 因缺同名组失败；`TZ=Asia/Shanghai` 写入 `/etc/timezone`；`HEALTHCHECK` 用 bash `/dev/tcp` 探 8080——项目未引 actuator；`ENTRYPOINT` exec 形式让 java 做 PID 1 以支持优雅停机；先拷 `pom.xml` + `dependency:go-offline` 吃层缓存）、`RuoYi-Vue3/Dockerfile`（`node:22-alpine` 构建 → `nginx:alpine` 运行；**必须是 yarn**——仓库只有 `yarn.lock`，`npm ci` 会直接失败）、`RuoYi-Vue3/nginx.conf`（`location ^~ /prod-api/` + **`proxy_pass http://lab-backend:8080/` 末尾斜杠**剥离前缀；顺带补了卡示例漏掉的 **`client_max_body_size 20m`**，否则故障照片被 nginx 默认 1m 拦成 413）、根 `docker-compose.yml`、`docker-compose.prod.yml`、`deploy/mysql-init.sh`、两个 `.dockerignore`、`.env.example`；`ci.yml` 的 `docker` job **解注释启用**（buildx + 两镜像 build + compose 语法门禁，`push: false`）。**验收（受环境限制的诚实口径）**：本机**未安装 Docker**，`docker compose build/up/ps` **未执行**；改用 ① **`t4_verify.py` 94 条静态断言全绿**（含 SQL 顺序三方互校：`mysql-init.sh` 的 `for` 列表 ↔ 磁盘 7 个文件 ↔ 各脚本自带 `[N/7]` 标记逐位吻合；并反向证实「字母序 ≠ 正确顺序」）、② **`t4_env_binding_check.py` 23 条环境变量绑定断言全绿**（Spring **真实属性源机制**，两套不同取值对照；`application*.yml` 一字未改）、③ `.gitignore` 正反两面实测（`.env` 被忽略、`RuoYi-Vue3/.env.production` **未被误伤**）。**新登记 D-27 / D-28**；**D-04 销项**。留痕：`AI留痕/T4-2026-09-17.md` |
| **T5** | 代码负责 | 设计模式落地（简单工厂） | P1 | T0、T1 | 架构文档「设计模式」8 分 | ✅ | **2026-09-17 完成**：① 新增 `constant/LabAssetEvent`（8 个资产履历事件，`actionName()` / `content(args)` / **`ofAssetStatus(status)`**）、`constant/LabRepairEvent`（5 个报修履历事件）、`constant/LabRecordFactory`（**简单工厂**：`assetRecord(...)` / `repairRecord(...)` + 私有构造器；**无 Spring 注解、不写库、不加缓存/日志** —— 单测无需 Spring 上下文）；② `LabAssetServiceImpl` **6 处调用点**、`LabRepairServiceImpl` **4 处语句／5 个事件分支** 全部改为「只声明事件」，两个 `insertXxxRecord` 私有方法保留为**一行薄转发**（卡的「二选一」，未留两套并行实现）；③ `LabStatusUtils.assetRecordType` **保留且行为不变**，改为委托 `LabAssetEvent.ofAssetStatus(...).actionName()` —— 「状态码 → 动作名」判定规则**唯一来源 = 枚举**；④ 产出 `docs/大作业/设计模式-类图.md`（Mermaid 类图 + 选型理由 + **为什么不选状态模式**，供架构文档第 5 章直接用）。**行为不变性三层证据**：(a) `mvnx.ps1 -o -B clean test` → `Tests run: 49, Failures: 0, Errors: 0` / `BUILD SUCCESS` / 01:41，**用例数与改造前一致**；(b) **静态字面量级比对**（`.workbuddy/tools/t5_literal_proof.py`，基线 `git show HEAD:`，判据 = 字面量内容精确相等）→ 15 个迁移字面量 + 4 条合成模板 + 3 个派生动作名全部逐字节迁移，**全角引号 `U+201C/U+201D` 字节级确认**，Service 零残留；(c) **动态探针**（`.workbuddy/tools/T5EventProbe.java`，脱离 Spring、无数据库）→ **62 项断言全过**，覆盖全部 12 个事件的动作名与文案 + 工厂组装契约 + 操作人兜底；运行期集成测试 SQL 参数实测到 `报修状态由“待审核”变更为“已拒绝”` 渲染正确。**⚠️ 发现卡自身一处错误**：卡给的 `TRANSFER` 模板 `"资产所属实验室调整：%s → %s"` 与真实代码不符（真实代码该文案**不带参数**，实验室名在 from/to 两列），按卡第 2.3 节「行为逐字不变」红线**以代码为准**（留痕第 4 节①）。**边界**：`src/test`／`src/main/resources`／`domain`／前端／`pom.xml` **零改动**。留痕：`AI留痕/T5-2026-09-17.md` |
| **T6** | **UI设计** | 前端 UI 统一与体验修复 | P1 | — | 答辩 PPT「核心功能界面」截图；演示观感 | ✅ | **2026-09-17 完成**：第 6 节 6 条 grep 全绿 + `vite build` `✓ 2545 modules / 43.30s / EXIT=0`；第 10.3 节 **4 项**目视不确定项在真实环境逐项量过（V-1 固定列不破 / V-2 折叠态四角 8px / V-3 1920 无横滚 / **V-4 已补测：3 按钮占 186px < 220px、单行不换行**，见卡第 12 节）。卡内修 `.pagination-container` 权重、连带修 D-22（asset 操作列 260→320）。**产出 4 张答辩素材截图**（`AI留痕/截图/T6-核心功能界面/`，9-19 冻结前必须用掉）。新登记 **D-20 / D-21**。留痕：`AI留痕/T6-2026-09-17.md` |
| **D2** | **PPT和设计** | 答辩 PPT 设计与视觉物料（≤15 页） | P1 | D1（内容） | 答辩表现 25 分；交付物 6 | 🔄 | **2026-09-17 三轮推进**：① 产出 `答辩PPT/slide-outline.md`（14 页预算）、`答辩PPT/视觉规范.md`、画布 4 版；② **阿辉批准定稿封面 / 第 3 页架构 / 第 4 页状态机**（色值全量回查通过、骨架坐标逐项一致；第 3 页页脚补全 4 处 SOLID，素材引自 `文档/02` §4.1）；③ **阿辉下「先填与代码状态无关的页」，本轮一次出 6 页**（02 背景与三痛点 / 05 设计取舍 / 06 AI 协作双向纠错 / 10 权限口径 / 12 三条容忍偏差 / 14 分工与 AI 声明），**累计 9 页 / 14 页**；结构校验**零溢出零重叠**，6 张导出图落 `答辩PPT/导出预览/`。留痕：`AI留痕/D2-2026-09-17.md`（第 14~22 节 = 第 3 轮）。<br>**⚠️ 两处待阿辉裁定**：① **测试数字按 37 还是 49** —— 开工指令转述 37，但**本节 T1 / T2 行在 T2b 加固轮后已回写 `Tests run: 49`**，磁盘 `@Test` 计数与 22:14 实跑亦为 49，**建议按 49 落笔**；② **核心界面截图用 `admin` 还是先执行 `[7/7]` 再用 `labadmin` 重采** —— 截图是 `admin` 取景，因 §7.3 的 D-21 记 `❌ 未修`。**第 7 / 8 / 9 / 11 / 13 页维持 `[待填]`**（第 8 页另受 T3 流水线 `[4/6] 打包产物` 阶段 FAIL 阻塞） |
| **D5** | **PPT和设计** | 海报与对外视觉物料 | P2 | 阿辉定用途 | 演示观感；创新分（5 分）加分尝试 | ⚠️ | 等阿辉确认做哪几张、用在哪 |
| **D4** | **产品经理** | 共享上下文对齐 | — | — | 四角色协同同步 | ✅ | 2026-09-17 |
| **D1** | **文档负责** | 五份文档撰写（交付物 1~5） | P1 | T0~T5 | **文档质量 70 分** | 🔄 | 卡：`任务卡/D1-五份文档撰写.md`。**2026-09-17 21:03 PM 巡检兜底**：原标 ⬜，实查 `文档/01`(319 行)、`文档/02`(639 行) 两份初稿已在 → 改 🔄 |
| **D3** | **代码负责** | 源码 ZIP 打包 | P0 | 全部 + D1 | 交付物 7 | ⬜ | T-2 天执行，含 5 份 PDF |

**关键路径**：`T0 → T1 → T2 → T3 → D1 → D2 → D3`。T5 可与 T3 并行。

> **读法**：「角色」列只表示**该卡归哪个会话**，**不代表已开工**——状态仍是 ⬜ 的就是还没动。
> 真正的「认领」以状态变 🔄 为准（工作流第 ③ 步）；改状态时**在自己那一行改**，不要另开文件。
> **第 12 轮变更**：`T1`/`T2` 从「代码负责」划给新增的 **「测试负责」**；`D1` 归新增的 **「文档负责」**；`D3` 归「代码负责」。
> 「代码负责」**四张卡（T0 / T3 / T4 / T5）已全部完成**，**只剩 `D3` 源码 ZIP 打包**（T-2 天执行，需「文档负责」交完 5 份 PDF 后开工）。
> **⚠️ T4 的环境限制已如实登记**：本机未装 Docker，`docker compose up` 未真跑，卡的 §7 截图要求**无法产出**（见 **D-28**）；替代证据是 94 条静态断言 + 23 条环境变量绑定实证。

### 6.1 🤖 自动巡检（PM，每天 09:00 / 21:00）

> **PM 已挂两个定时任务**：**09:00 早间布防**（回看昨日交付 + 排出今天该催谁）与 **21:00 晚间验收**
> （跑真实命令验收今天到底做成了什么）。有效至 **2026-09-22**。协议见 `.workbuddy/logs/pm-watch/README.md`。

**各角色要知道的三件事**：

1. **会被真实验收，不是看台账**——PM 每次都会**跑命令**（`mvnx.ps1 -o -B clean test`、查 `sql/` 建表语句、
   `.github/workflows`、`Dockerfile`、文档份数……）拿真实数字。**台账标了 ✅ 却跑不出证据的卡，会在简报里被点破。**
2. **会被查越界**——PM 用 `git diff --name-only` 逐条比对 5.1 节的「可写目录」硬边界，越界按第 7 节登记偏移。
3. **不要改 `.workbuddy/logs/pm-watch/`**——那是 PM 的巡检工作区（在 `.gitignore` 内）。
   台账的「状态」列仍由**执行人自己**改（工作流第 ③ / ⑥ 步），PM 只做核对与兜底。

### 6.2 📦 交件台（谁给谁交了什么）

> 跨会话没有即时消息通道，**交件一律落到这里 + 自己那份 `AI留痕/`**。执行人**主动登记**，不要让下游来问。

| 日期 | 从 → 到 | 交了什么 | 位置 | 下游约束 |
| --- | --- | --- | --- | --- |
| 2026-09-17 22:0x | **UI设计 → PPT和设计** | **D2 第 3 类「核心功能界面」4 张截图**（1920×1080、统一 `admin` 取景、无遮挡、可直接排版）：`T6-01-首页运维看板` / `T6-02-资产台账` / `T6-03-资产履历时间线` / `T6-04-报修处理时间线`。另附 5 张验收证据图（V-1/V-2/V-2b/V-4/报修列表/P2）+ 1 张二维码弹窗图（P4） | `docs/大作业/AI留痕/截图/T6-核心功能界面/`；交件单明细见 `AI留痕/T6-2026-09-17.md` **第 9.1 节**；演示路径巡检结论见 `AI留痕/巡检-UI演示路径-2026-09-17.md` | **⚠️ 必须在 2026-09-19（D-3）冻结点前定稿用进 PPT**；9-19 后再改 UI 这批图作废。**⚠️ T6-01（首页看板）里「近七日报修趋势」「近七日维修费用」两张图是空的（D-25）**——排版时建议只取左上 4 指标卡 + 右上故障等级分布 + 左下实验室维修次数，**避开两张空图**；若 D-25 修复后可重拍 |
| 2026-09-17 | **测试负责 → 文档负责** | 37 个用例（单测 30 + 集成 7）、BR-01~06 追溯矩阵、`Tests run` 原始截图 | `AI留痕/T1-2026-09-17.md`、`AI留痕/T2-2026-09-17.md`、`AI留痕/截图/` | 测试文档的数字一律以留痕为准，不照抄卡里的小计 |
| **2026-09-17 22:2x** | **测试负责 → 文档负责** | **⭐ 测试素材包（最终版，替代上一行）**：**49 个用例**（单测 **33** + 集成 **16**）、`Tests run: 49, Failures: 0` 逐字原文、**BR-01~06 全量追溯矩阵（58 条映射）**+ **US-13 看板 4 个用例**、等类/边界值清单、运行命令与环境三前提（离线 `-o` / **H2 锁 1.4.199** / 无需中间件）、**两条如实说明**（覆盖率 % 出不来；WMIC 噪声无害） | **`AI留痕/测试素材交付.md`**（主文档）；`AI留痕/T2b-2026-09-17.md`（加固轮留痕）；`AI留痕/截图/T2b-*.{txt,html}` | **① 数字一律以 `测试素材交付.md` + 构建日志为准，不照抄任务卡的目标值**（卡写 29，实际 33）。**② 覆盖率**：只写"用例↔BR 矩阵 + 断言 177 / 交互校验 27"，**不要编行覆盖率百分比**。**③ 必须写"T2b 加固轮后看板已覆盖"**——旧口径"看板未测"已作废。**④ PNG 截图需人工从 `T2b-测试运行结果-逐字摘录.html` 截取** |
| 2026-09-17 | **代码负责 → 文档负责** | T0 的 `laboratory_schema.sql` 真实 DDL（表结构章节素材） | `RuoYi-Vue-fast/sql/laboratory_schema.sql`、`AI留痕/T0-2026-09-17.md` | 架构文档第 7 章按此逐字回填 |
| **2026-09-17 第 19 轮** | **代码负责 → 文档负责** | **⭐ 设计模式素材包（架构文档第 5 章「设计模式」8 分，单项分最高）**：① `docs/大作业/设计模式-类图.md` —— Mermaid 类图（可直接渲染/转图）、选型说明、**「为什么不选状态模式」整段（答辩可直接引用）**、给架构文档的逐节取用提示（§7）；② 行为不变性的三层证据（49 用例全绿 + 静态字面量级比对 + 62 项动态断言 + 运行期 SQL 实测）；③ **「AI 产出物自身也会错」的真实案例**（卡给的 `TRANSFER` 模板与真实代码不符，人工复核拦下 → AI 报告「质量评估 / 人工审查」章节现成素材） | `docs/大作业/设计模式-类图.md`（主文档）；`AI留痕/T5-2026-09-17.md` 第 3 / 4 节；代码：`constant/{LabAssetEvent,LabRepairEvent,LabRecordFactory}.java` | **① 第 5 章不要再留 `[待填]`** —— T5 已完成，§2 类图与 §4 表格可直接取用。**② 抄进 PDF 前必须重写表述**（查重红线：> 30% 记 0 分，含代码注释）。**③ 类图请照 `设计模式-类图.md` §2 的原样渲染**（已含 `LabStatusUtils` 委托 `LabAssetEvent` 这条边），别照任务卡 §2.4 的旧版画 —— 卡那版少了委托关系、且 `TRANSFER` 模板写错了 |
| **2026-09-17 第 20 轮** | **代码负责 → 文档负责** | **⭐ 容器化素材包（交付物 4《CI/CD 部署方案》的「Dockerfile 加分项」「流水线设计图的构建阶段」「三环境配置差异」三处素材）**：① **可直接引用的两个硬事实** —— 「SQL 初始化顺序」坑（官方镜像按**文件名字母序**执行，`laboratory_*` 会全部排在 `ry_20260417.sql` 之前 → 每个脚本都报「表不存在」；解法是只挂一个显式排序的 `mysql-init.sh`）与「三环境配置差异」的**落地表格**（`ruoyi.profile` / `spring.redis.host·port` / 数据源三处，**全部由环境变量覆盖、配置文件一字未改**）；② **验证方式的诚实写法**：把「已证明的」94 条静态断言 + 23 条环境变量绑定实证，与「未验证的」（容器未在本地跑）分开写，**不要写成 `docker compose ps` 已通过**；③ **可当 AI 报告「人工审查」素材的两处真实案例**：卡里 redis host/port 缺失（照抄 → 能启动但登录页出不来验证码）、nginx 示例缺 `client_max_body_size`（照抄 → 传故障照片 413） —— 都是「AI/卡产出物本身有缺口、靠核对真实配置拦下」 | `docker-compose.yml`、`docker-compose.prod.yml`、`RuoYi-Vue3/nginx.conf`、`deploy/mysql-init.sh`（**注释里已写全根因**，可直接摘）；`AI留痕/T4-2026-09-17.md` 第 3 / 4 节；验证脚本 `.workbuddy/tools/t4_verify.py`、`t4_env_binding_check.py` | **① 不要等 `docker compose ps` 截图**（本机无 Docker，见 **D-28**），《CI/CD 部署方案》的「验证」一节按替代证据写。**② 卡片 §2 的 `nginx.conf` 与 §4 的环境变量清单都有缺口，抄前先看产出里的注释**（见 **D-27**）。**③ 抄进 PDF 前必须重写表述**（查重红线：> 30% 记 0 分，含代码注释） |

> **用法**：交件人在自己那一行写；**只追加，不改别人的行**。下游取件后在 `AI留痕/` 里留一句「已取件」即可。

---

## 7. 偏移检查

### 7.1 检查规则

每次回写台账时，**必须跑一遍下面 5 项**。任一项不通过，就在 7.3 记一条偏移。

| # | 检查什么 | 怎么查 | 不通过的后果 |
| :-: | --- | --- | --- |
| C1 | **需求 → 实现**：`01-需求基线.md` 的 14 个用户故事，前后端是否都有对应实现 | 抽查接口是否存在（见 7.2 表）、前端页面是否有入口 | 文档写了但代码没有 = 答辩被问就崩 |
| C2 | **规则 → 测试**：`01-需求基线.md` 的 BR-01~06 是否有测试覆盖 | 看测试类名与断言对象是否指回 BR 编号 | 测试文档的「用例覆盖率」扣分 |
| C3 | **交付物 → 硬指标**：`02-交付物验收清单.md` 的复选框 | 逐项勾选 | 缺一即扣分 |
| C4 | **README → 事实**：README 写的能力是否真的存在 | 抽查 2~3 条 | 文档漂移，助教按 README 跑不起来 |
| C5 | **范围 → 边界**：有没有偷偷做了 `00` 号文件「明确不做」清单里的东西 | 看新增文件与接口前缀 | 范围蔓延，摊薄关键路径的时间 |

### 7.2 需求 → 实现 对照（2026-09-17 核查结果）

| 用户故事 | 后端 | 前端 | 结论 |
| --- | :-: | :-: | :-: |
| US-01 扫码带出资产信息 | ✅ `/{assetId}/qrcode` | ✅ 资产页「标签」按钮 + 弹窗 | ⚠️ 见 D-01 |
| US-02 上传故障照片 | ✅ `attachmentUrls` | ✅ `image-upload` limit 4 / 5MB | ✅ |
| US-03 查看报修进度时间线 | ✅ `/{repairId}/records` | ✅ `el-timeline` | ✅ |
| US-04 完成后评分 | ✅ `PUT /evaluate` | ✅ | ✅ |
| US-05 维修工程师看全部工单 | ✅ `LabRoleUtils` 行级过滤 | ✅ | ✅ |
| US-06 状态推进 | ✅ `validateStatusChange` | ✅ `nextStatusOptions` | ⚠️ 见 D-06 |
| US-07 登记维修成本 | ✅ `repairCost` | ✅ | ✅ |
| US-08 审核 / 拒绝报修 | ✅ `PUT /audit` | ✅ | ✅ |
| US-09 资产筛选导出 Excel | ✅ `POST /export` | ✅ | ✅ |
| US-10 批量导入资产 | ✅ `POST /importData` + `/importTemplate` | ✅ `el-upload` 拖拽 | ✅ |
| US-11 资产变更履历 | ✅ `/{assetId}/records` | ✅ `el-timeline` | ✅ |
| US-12 阻止误删有资产的房间 | ✅ `checkRoomCanDelete` | ✅ | ✅ |
| US-13 首页运维看板 | ✅ `/dashboard/summary` + `/charts` | ✅ 4 指标 + 4 图 | ✅ |
| US-14 角色与菜单权限 | ✅ 若依原生 + 7 角色 | ✅ `labPermission.js` | ✅ |

**字典核查**：`lab_asset_type` / `lab_asset_status` / `lab_fault_level` / `lab_repair_status` 四组在 `laboratory_upgrade.sql` 中均已建好并被前端 `useDict` 使用 ✅

### 7.3 当前未修复的偏移

| 编号 | 偏移内容 | 类型 | 影响 | 修复动作 | 状态 |
| :-: | --- | :-: | --- | --- | :-: |
| **D-01** | 资产二维码内容是**相对路径** `/laboratory/repair?assetId=x`，手机相机扫不开，只能登录后站内跳转 | 需求与实现偏差 | US-01「扫码报修」在答辩演示时会被质疑 | 配一个站点基址（如 `https://域名/laboratory/repair?assetId=x`）即可，属小改动 | ❌ 未修 |
| **D-02** | `sql/` **缺 `lab_room` / `lab_asset` / `lab_repair` 三张主表的建表语句** | 交付物缺陷 | **仓库无法从零复现**，交付物 7 硬伤；助教按 README 跑到第 3 步必报错 | **已修**：T0 卡产出 `laboratory_schema.sql`（[1/7]），5 张业务表一次建齐，全部 `create table if not exists`。空库重建实测：`show tables like 'lab%'` 恰好 5 行、零报错 | ✅ 已修 |
| **D-03** | 集成测试 0 个 | 硬指标未达 | 测试文档「集成测试 ≥5」直接缺项 | **已修（2026-09-17 T2 卡）**：产出 7 个集成测试（IT-01~IT-07），跑在 H2 1.4.199 内存库上、**零中间件依赖**，真实打通 `Service → Mapper → 数据库`。全量 `Tests run: 37, Failures: 0, Errors: 0` / `BUILD SUCCESS`（01:48）。覆盖面：BR-01~06 六条业务规则全部命中。另新登记 **D-19**（看板无法在 H2 上覆盖 —— **该条已于 T2b 加固轮收口，看板现已被 7 个用例全覆盖**，见 D-19 行） | ✅ 已修 |
| **D-04** | 无 CI 流水线 / 无 Dockerfile | 硬指标未达 | CI/CD 10 分里「配置文件可运行性」3 分悬空 | **CI 侧已修（T3，2026-09-17 第 18 轮）**：产出 `.github/workflows/ci.yml`（三个 job，含版本固定、缓存、artifact；测试阶段未加 `-DskipTests`、无硬编码凭据）＋ `deploy/local-pipeline.ps1`（本地等价实测 `PASS 4 / SKIPPED 2 / FAIL 0`、`EXIT=0`），「配置文件可运行性」3 分**已有可运行落点**（配置本身待推到 GitHub 后在 Actions 上真实触发一次）。**Dockerfile 侧已修（T4，2026-09-17 第 20 轮）**：产出后端/前端两个多阶段 Dockerfile + `nginx.conf` + `docker-compose.yml` + `docker-compose.prod.yml` + `deploy/mysql-init.sh` + 两个 `.dockerignore`，并把 `ci.yml` 里那段被注释的 `docker` job **解注释启用**（buildx，两个镜像都 build、`push: false`，另加 `docker compose config` 语法门禁）。交付物 4 的「Dockerfile 加分项」与「流水线设计图的构建阶段」**均已有可运行落点**。**唯一保留的限制**：本机无 Docker，容器未真跑，见 **D-28** | ✅ 已修 |
| **D-05** | 代码里没有显式设计模式 | 硬指标未达 | 架构文档「设计模式」8 分（单项最高）拿不到 | **已修（T5，2026-09-17 第 19 轮）**：落地**简单工厂（创建型）**——`constant/LabRecordFactory` 把「一次业务事件 → 一条履历记录」的组装规则（关联主键／动作名／文案／操作人兜底 + `createBy`）从散落的 **10 个调用点**收敛为 **2 个静态方法**；动作名与文案模板由 `LabAssetEvent`(8) / `LabRepairEvent`(5) 两个枚举承载（新增事件只加常量，**两个 Service 零改动 = OCP**）。配套产出 `docs/大作业/设计模式-类图.md`：Mermaid 类图 + 选型理由 + **「为什么不选状态模式」**（3 个 `if-else` 分支不值得上 5 个状态类，属过度设计）——**能说清"什么时候不用"比硬堆模式更贴评分点**。**实证**：49 用例全绿（用例数不变）+ 静态字面量级逐字节比对 + 62 项动态断言 + 运行期 SQL 参数；**纯重构，接口/行为零变更** | ✅ 已修 |
| **D-06** | 前端 `nextStatusOptions` 与后端 `validateStatusChange` 是**两张手写表**，跨语言无法真正共享 | 一致性风险 | 后端改状态机时前端可能漏改 → 按钮与实际不符 | 现有后端矩阵测试能兜住后端；前端改动时需人工比对 | ⚠️ 已记录容忍 |
| **D-07** | 测试覆盖严重偏向状态机：7 个测试里 5 个是状态机，**提交校验 / 评价规则 / 权限判定 0 覆盖** | 质量风险 | `LabRoleUtils` 写错会导致全系统静默越权且无人发现 | **已修（2026-09-17 T1 卡 + T2b 加固轮，遗留已收口）**：① 三个零覆盖区全部补齐 —— 权限判定 5 个（新建 `LabRoleUtilsTest`，含「处理角色 ⊆ 全局可见角色」的结构性包含断言）/ 提交校验 5 个 / 评价规则 2 个；总用例 7 → **30**，状态机相关占比由 5/7 降到 5/30。② **遗留收口（T2b 加固轮）**：原 5 个反射用例**有意保留**（契约层，直接验证 4 合法 + 16 非法 + 幂等矩阵，删掉等于回退覆盖率），**新增** `LabRepairStatusMachinePublicEntryTest` **3 个用例**走 `updateLabRepair` 公开入口（入口层）——断言目标 status 与旧 assetId **真的被交给持久层**、非法流转**整轮零写库**（`verify(..., never())`）、状态不变时履历记「维修信息更新」而非「状态流转」。**两层职责已写进类注释**：契约层验矩阵完整性、入口层验分支与持久化编排。全量 `Tests run: 49, Failures: 0` | ✅ 已修 |
| **D-08** | `application.yml` 的 `token.secret` 默认值是弱密钥 `abcdefghijklmnopqrstuvwxyz` | 安全 | 生产环境必须用环境变量覆盖 | 列入《CI/CD 部署方案》的配置差异章节 | ⚠️ 已记录容忍 |
| **D-09** | 未初始化 git 仓库（无 `.git`） | 流程阻塞 | CI 无法触发；T3 卡前置 | **✅ 彻底解除**（2026-09-17）：① `git init -b main` + `git remote add origin https://github.com/same-day123/Manager-system.git`；② 修复**嵌套仓库 / gitlink（160000）**——两个内层 `.git` 移到 `D:\code\_Manager_system_git_backup\`（可逆备份），`--amend` 得 **699 文件 / 82212 行**含完整源码；③ **`push origin main` 成功**，远端 `refs/heads/main` = 本地 `57c81b9`（`ls-remote` 核验）。**T3 卡前置已完全就绪**（不含密钥 / 构建产物 / 日志）；④ 后续本地提交 **`ed971fd`**（T0：`sql/` + README + AGENTS.md + `AI留痕/T0-*.md`，11 文件 +743/−34）**尚未推送** —— Token 已吊销，等阿辉 / PM 用新凭据推；⑤ 再一处本地提交 **`1e0acb7`**（T3：`.github/workflows/ci.yml` + `deploy/local-pipeline.ps1` + README + 本文件 + `AI留痕/T3-*`，**10 文件 +1879/−76**）**同样未推送**。⇒ 远端当前停在 `1668e4a`，本地领先 **2 个提交** | ✅ 已解除 |
| **D-10** | **`asset_keeper` 登录后首页必然 403**：`laboratory:dashboard:view` 只按「持有 `repair:list`」授权，而 `asset_keeper` 在 `LabRoleUtils.canViewAll()` 的全局可见角色清单里、却**不持有** `repair:list` → 同一件事在三处口径不一致 | 权限缺陷（违反 BR-03「角色口径唯一来源」） | 演示用 `asset01` 登录看不到看板，答辩现场必崩 | 已修：`laboratory_permission_fix.sql` 末尾按**全局可见角色清单**兜底补齐授权 | ✅ 已修 |
| **D-11** | **非处理角色可借「修改本人待审核单」更换 `assetId`**，从而把任意"正常"资产刷成"维修中" | 越权 + 数据篡改（违反 BR-02 / BR-03） | 资产状态被静默污染且无留痕，台账失真 | 已修：`updateLabRepair` 非 manager 分支**拒改 `assetId`**；前端 `canSelectAsset` 收紧为「仅新增时可改」 | ✅ 已修 |
| **D-12** | **流程偏移**：D-10 / D-11 的修复在**没有任务卡**的情况下直接改了 `src/main`（越过 T1 卡「不要修改 `src/main` 下任何生产代码」边界）与首页 `views/index.vue`（越过 T6 卡「不改首页 `views/index.vue` 视觉基线」边界） | 流程 / 台账同步 | 代码与台账不同步，后人可能重复修或误回滚；并行会话下改动冲突不可见 | 已补登记：本表 + 第 5 节台账 + `AI留痕/巡检-2026-09-17-一致性与越权缺陷修复.md`。**修复本身正确，不回滚**；后续约定见 7.4 第 4 条 | ⚠️ 已补登记 |
| **D-13** | **`lab_room` 在全部 `sql/` 脚本里零出现**——既没有建表语句（同 D-02），**也没有任何种子数据**。而 `laboratory_demo_seed.sql` 的 25 条演示资产硬编码 `room_id = 1001~1004`，空库执行后是**悬空引用**；真实库里那 5 个房间（`信工楼 A301` 等）**只存在于本机数据库中，不在仓库任何文件里** | 交付物缺陷（复现性） | T0 只补建表也修不完：空库复现后资产列表的「所属实验室」整列为空，演示效果残缺 | **已修（并入 T0，阿辉 2026-09-17 定 Q-03）**：`laboratory_demo_seed.sql` 的 **lab_asset 插入之前**加了房间种子数据 5 条（`room_id` 显式 1001~1005），资产的 `room_id` 不再悬空 | ✅ 已修 |
| **D-14** | **`lab_repair` 也没有任何种子数据**——真实库有 4 条报修（3 已完成 + 1 已拒绝）与 1 条处理记录，仓库里查无此数据 | 交付物缺陷（演示就绪度） | 空库复现后报修列表为空、首页看板全 0；而**报修闭环（时间线 / 评分 / 成本）恰是答辩演示的核心** | **已修（并入 T0）**：`laboratory_user_seed.sql` 末尾补 4 条报修（3 已完成 + 1 已拒绝）+ 1 条处理记录；申请人挂 `student1`、维修人挂 `repair01`（**不能写死 user_id**：报修列表对非处理角色按 `applicant_id` 过滤，不挂学生名下则学生助管看不到工单）；`create_time` 用相对日期以保证首页近 7 天趋势图非空 | ✅ 已修 |
| **D-15** | **T0 卡的字段清单与真实库 DDL 有 5 处出入**（逐条比对 `education_system` 导出的 `show create table`）：① 卡写 `decimal(12,2)`，真实库 `price` / `repair_cost` 是 **`decimal(10,2) DEFAULT '0.00'`**；② 卡写 `room_id not null`，真实库**可空**；③ **卡明确要求「不要建唯一索引」，真实库却有 `uni_asset_code` / `uni_repair_code` 两个唯一键**；④ 索引清单不同（真实库 `lab_room` **一个二级索引都没有**）；⑤ 真实库 `lab_asset.status` 注释是**过期文本**（`0在用 1闲置 2报修中 3报废`），而代码 `LabConstants` 与字典 `lab_asset_status` 用的是 `0正常 1停用 2维修中` | 一致性风险（三方 schema 打架） | 若照卡写，`laboratory_schema.sql`、T2 的 H2 建库、真实 MySQL 会变成**三套不同的表结构**，架构文档 ER 图必然与其中一个对不上；反之照真实库写，则**把「逻辑删除 + 唯一索引」这个潜在缺陷一起复制进新脚本**（删除资产后无法重建同编号，`checkAssetCodeUnique` 过得了、数据库拦下来 → 500） | **已定夺并修完（阿辉 2026-09-17 定「走 B 方案」）**：`laboratory_schema.sql` 按**普通索引**写（不建唯一键）；`decimal(10,2)`、`room_id` 可空性**以真实库为准**；另出幂等迁移 `laboratory_index_fix.sql` **已在真实库执行**——`uni_asset_code` / `uni_repair_code` 已删除，补齐 5 个普通索引（连跑两次 exit=0，数据 5/32/4 未变）。**缺陷实证**：事务内「建资产 → 删资产 → 重建同编号」现已成功（修复前会被唯一索引拒绝并返回 500），回滚后无残留 | ✅ 已修 |
| **D-16** | **本文件第 8 节把「流水线运行截图」的来源写成 `deploy/local-pipeline.ps1`，但仓库里没有 `deploy/` 目录、该脚本不存在**（文档负责 2026-09-17 实查） | 台账 / 文档漂移（指向不存在的文件） | 答辩准备时会照第 8 节去找一个不存在的脚本；任务书硬性要求的「测试 / 流水线运行截图」目前**没有落点** | 已就近修正第 8 节措辞并注明「待 T3 落地」；**脚本本身归 T3 产出**。**→ 2026-09-17 第 18 轮：T3 已完成，`deploy/local-pipeline.ps1` 已真实存在并在本机跑通（`PASS 4 / SKIPPED 2 / FAIL 0`、`EXIT=0`），第 8 节的「流水线运行截图」现在有真实落点**；第 8 节措辞同步改为指向该脚本的实测输出 | ✅ 已修 |
| **D-26** | **沙箱安全策略拦截 `WMIC.exe` 时会「概率性」拆掉整棵命令树**：Surefire 2.22.2 的 `PpidChecker` 在 Windows 上要调 WMIC 查父进程存活（根因已由「测试负责」在 `AI留痕/T2b-2026-09-17.md` 第 5 节钉到类与行号），本机安全策略把 WMIC 拉黑 → 沙箱抛「PROGRAM BLOCKED」事件，**连带把正在运行的整条命令链杀掉**。实测：同一份 `deploy/local-pipeline.ps1` 连跑 7 次，第 3、5、6 次都能跑到汇总，第 4 次死在阶段 3（vite 构建）中途、无任何报错输出 | 环境约束（**非代码缺陷、非流水线缺陷**） | ① 长流水线（含 Maven 测试阶段）**有概率被中途掐断**，表现为「阶段 3 只输出到 `transforming...`」；② 会让人误判成「vite 构建挂了」——**实际 vite 单独跑 `exit=0 / 67.9s` 完全正常**；③ 影响的是本地自检体验，**CI（GitHub Actions、Linux）无此问题** | **已记录容忍，不做代码改动**（Surefire 2.22.2 没有可关闭 `PpidChecker` 的属性——`ProcessCheckerType` 只在 3.x 存在，本机仓库虽有 3.x 的 jar 但不为绕沙箱升 surefire）。缓解手段：① `local-pipeline.ps1` 对 **Maven 阶段内置最多 3 次自愈重试**；② 日志**即时 flush**，进程被杀也不丢已产出内容；③ 跑不通就**再跑一次**（第 3 次即成功） | ⚠️ 已记录容忍 |
| **D-17** | **`README.md`「已知待办」最后一条仍写「未初始化 Git 仓库（当前无 `.git`）」，而 D-09 已于 2026-09-17 解除**（`git init -b main` + `origin` 已挂，**且已成功推送**） | 文档漂移 —— **第 7.1 节 C4「README → 事实」检查不通过** | README 是助教与新组员读的第一份材料，此处自相矛盾会让人以为仓库仍未纳入版本控制（进而怀疑 T3 能否触发流水线） | **已修**（「代码负责」随手收口，2026-09-17，与 T0 的 README 改动同批）：该行已改写为「已解除」，并补上 `57c81b9` 已推送、`ed971fd` 待推的事实；第 7.1 节 **C4 检查恢复通过** | ✅ 已修 |
| **D-18** | **台账「状态」列与事实双向漂移**：① `T6` **已达卡的完成判据**（6 条 grep 全过 + `vite build` EXIT=0）却仍标 🔄；② `D1` **已产出 2 份初稿**（`文档/01` 319 行、`02` 639 行）却仍标 ⬜「未开始」。两处均为**未回写**，非未完成 | 台账 / 事实漂移（同 D-16 / D-17 性质） | PM 与其他角色按台账判断进度时会**同时高估和低估**：T6 显得没干完、D1 显得没开工；「该催谁」的判断被带偏；9-19 冻结前对剩余工作量的估计不准 | **已收口（2026-09-17 22:40 复检）**：`D1` 由 PM 兜底改 🔄（21:03）；`T6` 经 UI设计 补齐第 10.3 节目视项实测后**由执行人自己改 ✅**；`T2` 亦于 21:03 后完成并回写 ✅。**本条由 PM 于 21:03 快照新占号、经「测试负责」提醒后于本轮补录进本表**（编号可见性问题见下方说明） | ✅ 已收口 |
| **D-19** | **`LabDashboardMapper` 的看板查询在 H2 上跑不了** —— 用了 `date_format()` 与 `date_sub()`，H2 1.4.199 **两个都不支持**（H2 Shell 探针实测，输出见 `.workbuddy/logs/h2-probe*.txt`） | 测试覆盖边界（**非生产缺陷**） | 运维工作台 4 张 ECharts 图**没有集成测试覆盖**；答辩若被问「看板测了吗」需如实说明，不能含糊 | **✅ 已收口（2026-09-17 T2b 加固轮）——不必容忍，已可覆盖**。原结论"只能容忍"**没穷尽办法**：H2 1.4.199 支持 `create alias <名> for "全限定类名.静态方法"`，在**测试侧注册两个同名函数**（`support/H2MySqlDateFuncs` 函数体 + `support/H2MySqlCompat` 注册器）即可让生产 SQL **一字未改**跑通。经 **3 版 H2 探针实测**（`h2-probe-dashboard{,2,3}.txt`）确定：① `for "类.方法"` 形式优于内联 `as $$…$$`（后者函数体里的 `;` 会被 Spring 脚本切分器截断）；② **`date_sub` 第二参数必须声明成 `String`**（H2 把 `interval 6 day` 变成 `INTERVAL '6' DAY` 字符串传入；声明 `int` → `Data conversion error`，声明 `Object` → `Hexadecimal string contains non-hex character`）；③ `FOR` 子句要**双引号**标识符。**实证**：新建 `LabDashboardIntegrationTest` **DB-01~DB-07 共 7 个用例**，看板 **8 条 mapper 查询**（`selectSummary` 4 指标 + `selectCharts` 4 图）**全部覆盖**，构建日志里出现生产 SQL 原文 + `<== Total: 3`（期望的今天 / 3 天前 / 6 天前三个分组，**窗口外第 7 天被正确剔除**）。**★ 生产 mapper XML 一字未改**（T2 卡第 4 节明令）；垫片在 `src/test`，**不参与生产打包**。垫片只实现生产 SQL 用到的语义（DAY 单位 + 9 个格式符），**不认识就抛异常**——宁可红灯，不产假绿灯。**答辩口径：看板已覆盖（8 条查询），做法是"不改生产代码、测试侧补同名函数"** | ✅ 已收口 |
| | ↑ **编号说明（2026-09-17 22:0x，测试负责）**：本条最初被我写成 **D-18**，随后发现 **PM 在 21:03 的晚间巡检快照里已占用 D-18**（台账状态双向漂移，并注明"待补录进 7.3"）。按 7.3 节的编号约定「撞号则顺延重编号、不删记录」，本条**顺延为 D-19**，PM 那条 D-18 保留。**教训：多人并行时，占号后要立刻写进 `AGENTS.md`，只落在自己快照里的编号对别人是不可见的。**（**2026-09-17 22:40 已落实**：PM 把 D-18 补录进本表，本条说明可归档） | | | | | |
| **D-20** | **演示账号的密码与全部文档不符**：`laboratory_user_seed.sql:23` 写入的 `@default_password` 是 `$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2`（**对应 `admin123`**，若依原生种子的哈希），但**同一文件第 9~21 行的注释**、`README.md` 演示账号表、`AGENTS.md` 第 1 节都写「密码统一 `123456`」 | 文档 ↔ 事实漂移（复现性） | **答辩现场按文档登录会当场失败**：实测 `labadmin` + `123456` → `{"msg":"用户不存在/密码错误"}`；`labadmin` + `admin123` → 登录成功。老师按 README 试也登不进 | **未修，待阿辉拍板改哪一边**：(A) 改文档与注释为 `admin123`（改 3 处文本，零风险）；(B) 重算 `123456` 的 BCrypt 哈希替换脚本第 23 行（改脚本 + 真实库需重跑，且 `pwdUpdateDate` 变化会再触发「初始密码」提示）。**建议 A** | ❌ 未修 |
| **D-21** | **真实库缺 `laboratory:dashboard:view` 这条权限菜单** → **已解除**（2026-09-17 UI设计 第二轮巡检实测）。实测复核：`select * from sys_menu where locate('dashboard',perms)>0` → **1 行**（menu_id=2022「运维工作台」，perms=`laboratory:dashboard:view`）；`sys_role_menu` 显示该菜单已挂给 8 个角色（`admin` / `common` / `teacher` / `lab_manager` / `asset_keeper` / `repair_engineer` / `student_assistant` / `lab_viewer`）；`labadmin`（lab_manager）登录首页后 `.lab-dashboard` 容器存在、4 个 ECharts canvas 全部渲染，无「暂无权限」占位提示 | 权限缺陷（**D-10 的脚本侧已修，真实库侧已由「代码负责」跑 `laboratory_permission_fix.sql` 落地**） | 此前：答辩演示的开场画面（首页看板）对演示账号不可见。现已解除，但 T6 截图仍统一用 `admin` 取景；9-19 冻结前是否重拍由 PPT 决定 | **已修**：真实库已执行 `sql/laboratory_permission_fix.sql`（幂等）。`lab_manager` 首页看板可见。**UI设计 第二轮巡检已用 labadmin 实机复核通过**，证据见 `AI留痕/巡检-UI演示路径-2026-09-17.md` 第 3 节 | ✅ 已修 |
| **D-22** | **资产台账操作列 260px 放不下 4 个按钮，「删除」被挤到第二行**——行高由 52px 撑到 63px。实测：该列 4 个按钮占 2 行（`修改/履历/标签` + `删除`），表格 10 行全部如此。对比：报修页操作列在 T6 里已按「同时最多出现 3 个」收到 220px，资产页是 4 个按钮的并排语义、却仍沿用 260px | 视觉缺陷（T6 卡第 2 节未列此列） | 截图进答辩 PPT 的「核心功能界面」时，按钮折行一眼可见；表格行被多余撑高、有效信息密度下降 | **已修（2026-09-17，UI设计，本会话）**：`asset/index.vue:63` 操作列 `width` 260 → **320**（4 个图标+两字按钮实测需约 306px），并加注释说明依据。1920 下资产表声明合计 1405 → 1465px，仍远小于内容区 1720px，**不引入横向滚动**；改后复测 4 个按钮占 **1 行**、行高回落。**按本表约定「先登记编号再动手」执行** | ✅ 已修 |
| **D-23** | **`AI留痕/截图/T6-核心功能界面/` 里的 D-22 证据图名不副实**：文件 `T6-P2-资产操作列按钮换行.png` 与交付图 `T6-02-资产台账.png` **SHA1 完全相同**（`4BC3BDE52C16`，同为 09-17 21:26:42 一次采集），说明**同一次采集写了两个文件名**；而文件名叫「按钮换行」，**内容却是修复后的「4 按钮一行」** —— 名字与内容相反。连带后果：**D-22 的"改前"证据图实际不存在**，只看目录会以为有 before/after 对照 | 留痕 ↔ 事实漂移（证据文件不可信） | ① 若有人按文件名引用，会把"已修好"的图当成"缺陷图"放进答辩或 AI 报告；② 反过来说"有改前截图"会构成**不实表述**（AI 报告与测试文档都要引证据，这类不实最容易被追问） | **2026-09-17 22:0x 由 UI设计处置**：① 文件**已改名**为 `T6-P2-资产操作列4按钮一行.png`（与实际内容一致）；② 在留痕第 6 节写明**它与 `T6-02` 是同一次采集、不是两张图**；③ 明确 **"改前"图不存在**，改前证据改用**两条可复核证据**——`git diff RuoYi-Vue3/src/views/laboratory/asset/index.vue` 的 `width="260"→"320"`，以及实测数值（改前 2 行/行高 63px → 改后 1 行/行高 41px）。**教训：截图脚本里给同一张图写两个文件名，会制造出"看起来有对照、实际没有"的假证据。** | ✅ 已修 |
| **D-24** | **真实库资产状态与 `laboratory_demo_seed.sql` 不一致，导致首页看板出现「5 台维修中资产 / 0 条待处理工单」的自相矛盾**。`laboratory_demo_seed.sql` 里只设 2 台「维修中」（`LAB-DEMO-2026-009`、`LAB-DEMO-2026-024`，均备注"报修中资产演示"），但真实库有 **5 台** `status='2'`（额外多出 2007 `LAB-CHEM-2026-001`、2030 `LAB-DEMO-2026-023`、2032 `LAB-DEMO-2026-025`）；多出的 3 台在 `lab_asset_record` 中显示为 **2026-07-03 17:01 由 admin 直接做「资产状态变更」**，并非通过报修流程生成。此外 `LAB-CHEM-2026-001` 在**任何 SQL 脚本里都查不到**，来源不明 | 数据质量 / 交付物一致性（种子脚本↔真实库） | ① 首页看板最醒目的 4 个指标卡里，**「维修中资产 5」与「待审核报修 0」并置**，答辩开场就会被问「5 台在修为什么没有工单」；② `laboratory_demo_seed.sql` 是 `where not exists` 插入 → 只插不更新，**按脚本重建的演示库与当前真实库状态不一致**，交付物 7 的可复现性受损 | 修复建议（二选一）：A) 9-18 清空业务表后重跑 `[N/7]` 脚本，让真实库与种子脚本一致；B) 写一条幂等 `UPDATE` 脚本把多出的 3 台改回 `status='0'`（风险低、保留既有房间/资产编号）。**归属：代码负责 / T0 卡**。AI留痕/巡检报告见 `AI留痕/巡检-UI演示路径-2026-09-17.md` 第 4 节 | ❌ 未修 |
| **D-25** | **首页看板「近七日报修趋势」「近七日维修费用」两张 ECharts 图为空线（0 单 / ¥0.00）**。直接证据：T6-01 首页运维看板截图左下、右下两图全是 0 | 演示数据 / 可视化 | 首页是答辩开场画面，4 张图里有 2 张为空，演示观感严重下降 | 根因：`LabDashboardMapper` 用 `date_sub()` 过滤最近 7 天，而所有演示工单日期都是 2026-07-02/03（种子写死），当前 2026-09-17 全部被过滤掉。**修复归属：代码负责 / T0 卡**。建议：A) 后端改为「全部时间」/「按创建月份」聚合；B) 或把种子数据 `create_time` 改成相对演示日期前 7 天内。证据见 `AI留痕/巡检-UI演示路径-2026-09-17.md` 第 4 节与 dashboard_numbers.txt | ❌ 未修 |

| **D-27** | **T4 任务卡自身有两处缺口，照卡执行会得到一个「起得来但用不了」的容器**：① 卡第 4 节「关键项目事实」列的可用环境变量清单为 `RUOYI_DB_*` / `RUOYI_REDIS_PASSWORD` / `RUOYI_TOKEN_SECRET` / `RUOYI_DRUID_*`，**漏了 redis 的主机与端口** —— 而 `application.yml` 里 `spring.redis.host: localhost` / `port: 6379` 是**写死的、连 `${}` 占位符都没有**。容器里 `localhost` 指向后端自己，照卡执行的结果是：容器**能启动、健康检查也能过**，但 `/captchaImage` 连不上 Redis、**登录页永远出不来验证码**（前端只显示一个空验证码框，极难反查）；② 卡第 2.2 节的 `nginx.conf` 示例**没有 `client_max_body_size`** —— nginx 默认只有 `1m`，而后端允许 10MB/请求 20MB、前端单图限 5MB，**上传 1MB 以上故障照片会被 nginx 直接拦成 HTTP 413**，且后端日志里连一条记录都没有（请求根本没到后端） | 任务卡缺陷（**同 T5 的 `TRANSFER` 模板，属"卡 ≠ 事实"**） | 若照卡逐字实现，容器化交付物在**答辩演示的两个核心动作上都会失败**：登录（要看板/台账必须先登录）与上传故障照片（报修闭环的入口） | **已在产出中补齐，且未越界**：① redis 用 **Spring Boot 原生变量** `SPRING_REDIS_HOST=redis` / `SPRING_REDIS_PORT=6379` 覆盖 —— relaxed binding 会把 OS 环境变量映射到同名配置键，且优先级**高于** `application.yml`，**因此不需要改配置文件**，与卡第 3 节「不要为了容器化去改 `application.yml`」的边界**完全一致**；② `nginx.conf` 补 `client_max_body_size 20m`（server 级与 `/prod-api/` 级各一处）；③ 两处均已写入 `docker-compose.yml` / `nginx.conf` 的注释，附根因说明。**实证**：`t4_env_binding_check.py` 用两套不同取值证明 redis 主机/端口确实受环境变量控制（`raw=localhost` → `eff=redis`；对照场景 `eff=redis-alt`） | ✅ 已修 |
| **D-28** | **T4 卡的 §7 留痕要求（`docker compose ps` 真实输出截图 + 登录成功界面截图）在本机无法产出** —— 本机未安装 Docker（`docker` 命令不存在，Docker Desktop 也未装） | 环境约束（**非代码缺陷、非流水线缺陷**，性质同 D-26） | ① 交付物 4《CI/CD 部署方案》若把「容器运行截图」列进证据清单，**会拿不到图**；② 答辩若被问「容器跑起来什么样」，只能如实回答「配置已备好、容器未在本地实跑」；③ `deploy/local-pipeline.ps1` 的 `[5/6] 镜像构建` 与 `[6/6] 冒烟测试` 在本机**恒为 SKIPPED**（脚本已保证 SKIPPED 不伪装成 PASS） | **不做代码改动，改用两层不依赖 Docker 的等价实证**（已在留痕如实写明，不伪造运行结果）：① `.workbuddy/tools/t4_verify.py` —— **94 条静态断言全绿**（YAML 可解析 / SQL 顺序三方互校 / 环境变量插值后取值 / `.env` 覆盖度 / nginx 前缀剥离与上传上限 / Dockerfile 非 root 与健康检查 / `.dockerignore` 正反两面 / CI job 结构）；② `.workbuddy/tools/t4_env_binding_check.py` —— **23 条绑定断言全绿**（Spring **真实属性源机制** + 两组不同取值对照，另证 `application*.yml` 一字未改）。**《CI/CD 部署方案》请据此写「验证方式」一节**：把「已经证明的」与「尚未验证的」分开写 | ⚠️ 已记录容忍 |

> **编号约定（第 14 轮起）**：D-16 / D-17 由「文档负责」登记。多会话并行写本表时**新偏移先占用当前最大编号**；
> 若两会话撞号，以先写入者为准、另一条顺延重编号，并在此处留一行说明——不删记录。

### 7.4 偏移处置原则

1. **D-01 / D-06 / D-08 属于"已记录容忍"**——影响可控，且修复收益低于成本。**但凡列入容忍的，答辩时要主动说明**，这比被老师问出来加分。
2. **D-02 必须最先修**——它卡住 T2，也让交付物 7 直接失分。
3. 任何新发现的偏移，**先记进本表再修**，不要默默改掉。台账的价值在于"能看出趋势"。
4. **D-12 是一次真实的流程教训，而不是形式主义**：那次巡检查出的两个缺陷（D-10 / D-11）**确实必须修**，
   但当时跳过了工作流的「② 核现状」与「③ 认领」两步，直接动了 `src/main` 和首页。
   **改对了不等于流程对了**——本仓库可能被多个会话并行修改，跳过认领就等于放弃了"如果撞车能发现"的唯一机会。
   处理结论：**修复保留、不回溯撤销**，但必须补齐登记（已完成），并从此把「紧急缺陷」也纳入台账，
   而不是当成"不用记账的小事"。

### 7.5 T0 核现状证据（2026-09-17，代码负责实查）

**① 三张主表确实缺建表语句**——全 `sql/` 目录 `create table` 检索结果：只有 `lab_repair_record`（`laboratory_upgrade.sql:48`）
与 `lab_asset_record`（`:63`）两张从表，`lab_room` / `lab_asset` / `lab_repair` **一张都没有**。**D-02 属实。**

**② 缺口比卡里写的更大**——按脚本名逐个检索 `lab_room` / `lab_repair`：
`lab_room` 在 `laboratory_*.sql` 里**零次出现**（建表、种子数据都没有）；`lab_repair` 只以 `alter table` 形式出现。
→ 新增 **D-13（房间无数据）**、**D-14（报修无数据）**。

**③ 真实库与脚本的差距（`education_system` 实查）**

| 表 | 真实库行数 | 仓库脚本能否复现 |
| --- | :-: | --- |
| `lab_room` | 5（1001~1005，如 `信工楼 A301 软件工程实验室`） | ❌ 一个字都没有 |
| `lab_asset` | 32（25 条来自 `laboratory_demo_seed.sql`，其余 7 条手工） | ⚠️ 部分（且 `room_id` 悬空） |
| `lab_repair` | 4（3 已完成 + 1 已拒绝） | ❌ |
| `lab_repair_record` | 1 | ❌ |
| `lab_asset_record` | 32（全部来自 `laboratory_demo_seed.sql`） | ✅ |

**④ 环境已就绪，T0 的验收命令能真跑**（不是纸上验收）：
`mysql.exe` 在 `D:\Study_Running\MySQL\mysql-8.0.34-winx64\bin\`，服务 `MySQL` 运行中、3306 监听，
`root/123456` 连通，`education_system` 存在。**T0 卡第 6 节那套「空库重建 + `show tables like 'lab%'` 期望 5 行」可以当场执行。**

**⑤ 待定夺（D-15）**——**新脚本以谁为基准？**

| 方案 | 做法 | 代价 |
| --- | --- | --- |
| **A. 以真实库为准** | 照 `show create table` 导出写，含两个唯一键、`decimal(10,2)`、可空 `room_id` | **能复现现状**，架构文档 ER 图好写；但把「逻辑删除 + 唯一索引」的**潜在缺陷一起复制**（资产删除后重建同编号 → `checkAssetCodeUnique` 放行、数据库报错 500），且 T2 的 H2 建库会继承同一缺陷 |
| **B. 以 T0 卡为准 + 修正真实库** | 按卡写普通索引，并加一段幂等迁移 `drop index` 掉真实库那两个唯一键 | **修掉潜在缺陷**，三方 schema 一致；但动了现有库结构，且架构文档需说明「这是有意修正」 |

**我的建议：走 B，但必须给阿辉过目**——理由是唯一索引与本系统的逻辑删除设计**在语义上直接冲突**，
它不是一个"风格差异"，而是一个会造成 500 的真缺陷；而 `decimal(10,2)` 与 `decimal(12,2)`、
`room_id` 可空性这两条**以真实库为准**（无实质影响，且真实库已按此运行）。
**在阿辉给出结论前，`laboratory_schema.sql` 不动笔**——避免写出一版随后要推翻的脚本。

---

## 8. 答辩要点（提前认领陈述段落）

**时间**：10 分钟 = 陈述 3 + 演示 2 + 提问 5。**不超过 15 页 PPT**。**每人必须参与陈述。**

**陈述不要念文档，讲这四件事**：

| 要点 | 素材在哪 |
| --- | --- |
| 为什么这么设计（以及**为什么没做某些事**） | `00` 号文件第 3.2 节的「明确不做」清单 + T5 卡的「为什么不用状态模式」 |
| AI 怎么帮的 | `docs/大作业/AI留痕/` 全部记录 |
| 遇到了什么问题、怎么解决的 | 任务卡的「执行指导与踩坑」段 |
| 需求与实现的偏差，以及如何权衡 | 本文件第 7.3 节的 D-01 / D-06 / D-08 |

**必须准备的三张截图**（答辩硬性要求）

| 截图 | 从哪来 |
| --- | --- |
| 核心功能界面 / 代码走读 | 登录后依次打开：运维工作台 → 资产台账（含二维码弹窗、履历时间线）→ 报修单（含时间线） |
| AI 工具实际使用录屏或截图 | `docs/大作业/AI留痕/截图/` |
| 测试运行结果 / 流水线运行截图 | **两侧都现成**：① 测试侧 —— `mvnx.ps1` 跑测试的 `Tests run` 汇总行（含 `.workbuddy/logs/mvn_test_0917.log`）；② 流水线侧 —— **`deploy/local-pipeline.ps1` 已由 T3 产出并实测通过**（`PASS 4 / SKIPPED 2 / FAIL 0`、`EXIT=0`），**直接截「运行该脚本后的终端输出」即可**，完整输出留档见 `AI留痕/截图/T3-本地流水线运行结果.txt`。**采集做法**：在仓库根执行 `powershell -NoProfile -ExecutionPolicy Bypass -File deploy/local-pipeline.ps1`，等约 3.5 分钟出汇总表后截屏（如实说明阶段 5/6 因本机无 Docker 而 SKIPPED） |

---

## 9. 本文件的更新规范

1. **只有两种改动**：更新第 5/6 节的进度与执行人、更新第 7 节的偏移。**不要在这里写设计细节**——那些归 `docs/大作业/` 下的对应文件。
2. 每次改动**必须更新文件顶部的「最后更新」日期**。
3. **不要删除历史偏移记录**，修完的把状态改成 ✅ 并注明修复方式，保留痕迹。
4. 发现本文件与 `docs/大作业/` 下的文件冲突时，**以 `docs/大作业/` 为准**，然后回来修正本文件。

---

## 10. 环境备忘（本机开发必读）

| 事项 | 说明 |
| --- | --- |
| **`mvn` 命令是坏的** | 报 `ClassNotFoundException: plexus-classworlds.launcher`。必须用包装脚本：<br>`powershell -NoProfile -ExecutionPolicy Bypass -File .workbuddy/tools/mvnx.ps1 -o -B clean test` |
| **`mvn compile` 不编译 `src/test`** | 搬包 / 改包名后必须跑 `mvn test`，否则测试里的旧包引用不会暴露 |
| **⭐ `mvn test` 不清理 `target/test-classes/`，所以它不复现 `clean test`** | 验证「改了配置 / 删了测试资源文件」这类改动**必须用 `clean test`**。实测教训：T2 做对照实验时用 `mvn test` 移出 `src/test/resources/application.yml`，结果**上一轮构建残留在 `target/test-classes/` 的那份仍在 classpath 上生效**，得出完全错误的结论；换 `clean test` 才复现真实行为。**判断依据**：构建日志里 `Application Version:` 后面是 `${ruoyi.version}`（未解析）还是 `3.9.2`（已解析），即可看出生产 `application.yml` 到底读没读到 |
| **构建出现 WMIC 告警** | 「WMIC.exe 被安全策略拦截」本身是干扰项，**不影响构建结果**（根因：Surefire 2.22.2 的 `PpidChecker` 查父进程存活时调 WMIC，被拦后自行降级为 NOOP events）。**但它有副作用，见下一条。** |
| **⚠️ 沙箱拦 WMIC 会「概率性」掐断整条命令链**（2026-09-17 第 18 轮实测，已登记 **D-26**） | 同一份 `deploy/local-pipeline.ps1` 连跑 7 次，第 3/5/6 次完整跑到汇总表，第 4 次**死在阶段 3（vite 构建）中途且无任何报错输出**（日志只到 `transforming...`）。**别误判成"vite 构建挂了"** —— 单独跑 `node node_modules/vite/bin/vite.js build --mode production`（cwd 必须是 `RuoYi-Vue3`）`exit=0 / 67.9s` 完全正常。**处置：跑不通就重跑**；流水线脚本已内置 Maven 阶段最多 3 次自愈重试 + 日志即时 flush。CI（GitHub Actions / Linux）无此问题 |
| **多会话并行会互踩 `RuoYi-Vue-fast/target/`** | 一个会话 `mvn clean` 的瞬间，另一个会话正在写 class → 报 `Cannot create resource output directory: ...target\test-classes` / `Error assembling JAR: ...target\classes\Xxx.class` / `写入 ...IdUtils.class 时出错`。**这三种都是假失败，不是代码缺陷**。`local-pipeline.ps1` 已内置冲突识别 + 重试；手敲命令遇到时**直接重跑** |
| **想抓 Maven 的完整输出，用 `-l <文件>` 而不是管道** | `mvnx.ps1 -o -B clean test -l .workbuddy/logs/x.log` 会把构建日志写进文件（且不带 ANSI 色码，好读）。**不要用 `2>&1 \| Out-File` 抓 Maven**：PowerShell 5.1 会把原生命令 stderr 包成 `NativeCommandError` 再吐回**本进程的 stderr**，外层若用管道捕获本脚本，管道会因 `RemoteException` 中断，脚本连同日志一起被干掉（2026-09-17 22:01 丢过一次后半段输出） |
| **Git Bash 的 PATH 不稳定** | 多次出现 `ls` / `dirname` / `grep` 找不到。跑不通就换 PowerShell；PowerShell 的 stdout 有时不返回，需 `Out-File -Encoding utf8` 落盘再读 |
| **离线构建** | 一律加 `-o`。**新增 Maven 依赖前先确认本机 `.m2` 里已有该 jar**（例如 H2 只有 1.4.199，pom 里必须写死版本） |
| **H2 1.4.199 的函数支持边界**（写集成测试前必看，实测于 `MODE=MySQL`，证据 `.workbuddy/logs/h2-probe*.txt`） | **可用**：`sysdate()`（⚠️ **只到「日」精度**，时间部分是 `00:00:00`，所以**任何依赖 `create_time` 排序/比较的断言在 H2 上不可靠**）、`now()`、`limit 1`、内联 `key idx_x (col)`、`auto_increment` + `useGeneratedKeys` 回填、`left join` 别名、多参数 `concat()`、`ifnull()`、`group by`、中文读写。<br>**不可用**：`date_format()`、`date_sub()`。→ 因此 **`LabDashboardMapper` 的看板查询在 H2 上跑不了**（见偏移 **D-19**），看板相关集成测试要么在测试侧注册同名自定义函数，要么另开 MySQL profile；**绝不许改生产 mapper XML** |
| **`vite build` 必须在 `RuoYi-Vue3/` 目录下执行** | vite 的 root 取的是**当前工作目录**。在仓库根目录跑会报 `Could not resolve entry module "index.html"` 且 12ms 就退出——**这是调用姿势问题，不是代码问题**，别去翻源码。正确姿势：`cd RuoYi-Vue3` 后跑 `node node_modules/vite/bin/vite.js build --mode production` |
| **看到中文乱码，先怀疑终端，别急着改脚本** | PowerShell 解码原生命令的输出走 `[Console]::OutputEncoding`（本机默认 GBK），`mysql` 吐回的 UTF-8 中文会被解成 `淇″伐妤` 这类乱码。**判断顺序**：① 先设 `[Console]::OutputEncoding = [System.Text.Encoding]::UTF8` 再看；② 要证明**入库数据本身**没问题，用 `select hex(列)` 跟 `select hex('期望中文')` 逐字节比。本机实测：新库与线上库的中文 hex 完全一致，纯属显示问题——**差点被误判成"种子数据写坏了"** |
| **往 `mysql` 导入整份 .sql 的可行姿势** | PowerShell 不支持 `<` 重定向、`cmd /c` 又被安全策略拦。可行写法：<br>`$OutputEncoding = [Text.Encoding]::UTF8`<br>`Get-Content -Raw -Encoding UTF8 <file> \| & mysql --default-character-set=utf8mb4 <db>`<br>**两个编码设置都要有**：`-Encoding UTF8` 管读文件，`$OutputEncoding` 管往原生程序 stdin 写，缺一个中文就会真的写坏 |
| **SQL 脚本执行顺序** | 见 `README.md` 第 1 节表格，顺序错了必报表不存在 |
| **远程仓库** | `https://github.com/same-day123/Manager-system.git`（阿辉 2026-09-17 提供）。本地 `git init -b main` 并挂 `origin`，**首次推送已完成**（`main` = `57c81b9`，699 文件，含源码）。**推送前先确认 `.gitignore` 生效**（`git status --short --ignored` 应看到 `target/`、`dist/`、`node_modules/` 被忽略）；**不含密钥**（`application*.yml` 里的弱密钥要用环境变量覆盖，别把真实口令提上去）。**⚠️ 嵌套仓库坑**：`RuoYi-Vue-fast/`、`RuoYi-Vue3/` 曾各带一个上游 `.git`，会让 git 把整个目录当成 gitlink（160000）跳过全部源码——现已把内层 `.git` 移出到 `D:\code\_Manager_system_git_backup\`，**别再往这两个目录里 `git init`** |

---

*本文件是项目的公共记忆。它不准 → 所有人都跑偏。*
