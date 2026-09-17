# T6 前端 UI 统一与体验修复

| 项 | 值 |
| --- | --- |
| **优先级** | P1 |
| **依赖** | 无（纯前端，不动后端） |
| **阻塞** | 交付物 6 答辩 PPT 的「核心功能界面」演示截图质量；答辩演示 2 分钟环节的观感 |
| **产出** | 1 个全局样式文件 + 3 个业务页面的模板调整 + 1 条 Element 覆盖样式 |

---

## 1. 目标

答辩要现场演示界面（占 2 分钟，且有硬性截图要求）。当前前端在**功能上完整**，但视觉与体验上有三类问题会在演示时被直接看到。本卡不新增功能，只做**设计收口 + 体验修复**。

### 1.1 问题一：三个业务页挂了「死类名」，与首页视觉割裂

`room/index.vue`、`asset/index.vue`、`repair/index.vue` 的根节点都是：

```html
<div class="app-container laboratory-page">
```

但全项目搜索 `laboratory-page`，**只有这 3 处使用，0 处定义**（2026-09-17 核查）。

后果：业务页完全停留在若依默认样式——`.app-container { padding: 20px }`、白底、表格直接贴边、搜索表单与工具栏之间没有视觉分隔。而首页 `views/index.vue` 已经是另一套设计语言（`.lab-dashboard`：`#f4f7fb` 浅灰底 + 白色卡片 + `8px` 圆角 + `#d9e2ef` 描边 + hover 位移）。

**首页和业务页像是两个系统**，这在答辩演示「首页看板 → 资产台账 → 报修单」的连续动线里会非常明显。

### 1.2 问题二：报修表格列宽合计 1975px，操作列占 330px

`repair/index.vue` 表格现有 13 列，宽度合计：

```
55 + 190 + 140 + 170 + 160 + 110 + 120 + 110 + 140 + 100 + 170 + 180 + 330 = 1975px
```

而 1920 屏的内容区实际可用宽度约 1670px，1366 屏只有约 1116px。**任何屏幕都必然横向滚动**，且固定在右侧的操作列独占 330px，等于常态遮挡内容区近 1/3 宽度。

更关键的是**列与列之间存在大量低价值空间**：

| 现状 | 问题 |
| --- | --- |
| 「资产编号」+「资产名称」两列共 310px | 同一条记录的两种标识，拆两列浪费横向空间 |
| 「申请人」+「联系电话」两列共 250px | 电话号码是次要信息，占满一整列的宽度只为偶尔看一眼 |
| 「操作」330px | 按 5 个按钮预留，但按钮受权限与状态互斥控制，**实际最多同时出现 3 个** |

### 1.3 问题三：跨页面不一致与窄屏溢出

| # | 问题 | 位置 |
| --- | --- | --- |
| a | 报修详情弹窗缺少单号 / 申请人 / 提交时间，点开详情反而看不出这是哪一单 | `repair/index.vue` |
| b | 资产履历时间线显示原始码「`0 → 2`」，而同项目的报修时间线显示「`正常 → 维修中`」，两条时间线口径不一致 | `asset/index.vue` |
| c | 资产类型下拉带 `allow-create`，能输入「投影仪」这类字典外的值，该值在表格里过 `<dict-tag>` 渲染不出来（退化成无颜色的裸文本） | `asset/index.vue` |
| d | 房间表格操作列没有 `fixed="right"`，资产/报修都有 | `room/index.vue` |
| e | 所有 `el-dialog` 都是写死的 px 宽度（420 / 520 / 560 / 620 / 720 / 760 / 860），窗口缩到一半时弹窗超出视口，**底部「确定/取消」按钮无法滚动到** | 三页共 7 个弹窗 |

---

## 2. 交付内容（验收标准）

### 2.1 新增全局样式 `RuoYi-Vue3/src/assets/styles/laboratory.scss`

把首页 `.lab-dashboard` 已经验证过的色板抽成变量，复用给业务页，让两处不再靠人工对齐：

```scss
$lab-page-bg : #f4f7fb;   // 页面底色
$lab-card-bg : #ffffff;   // 卡片底色
$lab-border  : #d9e2ef;   // 描边
$lab-text    : #172033;   // 主文字
$lab-text-sub: #667085;   // 次要文字
$lab-radius  : 8px;       // 圆角
```

`.laboratory-page` 的实现要点：

- [ ] 页面底色 `#f4f7fb`，与首页一致；`min-height: calc(100vh - 84px)`（84 = 顶部导航 50 + 标签栏 34）
- [ ] **搜索表单区与工具栏行拼成一张卡片**：搜索区上圆角、工具行下圆角，中间共用描边（`border-bottom: 0` + `border-top: 0` 对接）
- [ ] 处理**搜索区被折叠**的情况：`showSearch` 关掉时 `el-form` 是 `v-show="false"`（内联 `display: none`），此时工具栏行要自己撑起完整圆角，否则卡片顶部会露出两条直角边
- [ ] 表格独立成第二张卡片（`margin-top: 14px` 与上方分离），`border-radius: 8px` + `overflow: hidden`（截图表头的直角才会被圆角裁掉）
- [ ] 分页区背景改透明（若依 `.pagination-container` 自带白底，会在灰底上形成一条突兀白条）
- [ ] 提供表格内双行信息单元样式 `.cell-stack / .cell-main / .cell-sub`（供 2.2 的列合并使用）

### 2.2 修改 `repair/index.vue`

- [ ] `el-table` 列重构，宽度合计从 **1975px 降到 1535px**：

| 列 | 改前 | 改后 | 说明 |
| --- | --- | --- | --- |
| selection | 55 | 55 | — |
| 报修单号 | 190 | **160** | 单号形如 `RP202609170001`，160 足够 |
| 资产编号 + 资产名称 | 140 + 170 | **合并为「报修资产」200** | 双行：名称在上、编号在下 |
| 实验室 | 160 | **140** | — |
| 故障等级 | 110 | 110 | 字典标签，保留 |
| 处理状态 | 120 | 120 | 核心列，保留 |
| 申请人 + 联系电话 | 110 + 140 | **合并为「申请人」130** | 双行：姓名在上、电话在下 |
| 评分 | 100 | **80** | 普通文本，不需 100 |
| 完成时间 | 170 | **160** | — |
| 创建时间 | 180 | **160** | — |
| 操作 | 330 | **220** | 见下方说明 |

- [ ] 操作列宽度的依据写进代码注释：按钮受权限与状态互斥控制（`canStudentEdit` / `canAudit` / `canEvaluate` / `canDelete`），**实测同时最多出现 3 个**，330px 是按 5 个预留的过度设计
- [ ] 详情弹窗顶部新增 `.repair-meta` 摘要条，显示**报修单号 / 申请人 / 提交时间**（`v-if="form.repairId"`，新增时不显示）

### 2.3 修改 `asset/index.vue`

- [ ] 履历时间线的 `fromValue → toValue` 走字典翻译，与报修时间线口径统一
- [ ] 移除资产类型下拉的 `allow-create`（保留 `filterable`），杜绝字典外取值

### 2.4 修改 `room/index.vue`

- [ ] 操作列补 `fixed="right"`

### 2.5 修改 `RuoYi-Vue3/src/assets/styles/element-ui.scss`

- [ ] 给 `.el-dialog` 补 `max-width: calc(100vw - 32px)`，解决 1.3-e 的窄屏截断。**只在视口宽度不足时生效**，正常宽度下观感不变

---

## 3. 边界（**不要做**）

- **不要**改任何后端代码、接口、字段、字典数据。本卡是**纯前端**任务。
- **不要**动 `src/views/system/*`、`monitor/*`、`tool/*` 等若依原生页面（那是脚手架自带页面，不在交付范围内，改了白白增加查重与回归风险）。
- **不要**引入任何新的 npm 依赖（UI 库、图标库、CSS 框架都不行）。**离线环境，装不上。**
- **不要**改首页 `views/index.vue` 的视觉。它是已经定稿的设计基线，本卡是让业务页**向它对齐**，不是反过来。
- **不要**做深色模式适配。若依的 `dark/css-vars.css` 虽已引入，但项目实际只在浅色下演示；首页也没有做深色适配，只改业务页会造成新的割裂。**若要支持深色，应当作为独立任务统一处理首页与业务页。**
- **不要**为了压缩列宽而删掉任何数据字段的展示。合并列是「换个位置显示」，不是「不显示」。
- **不要**改 `.fixed-width` 那条失效的若依遗留样式（见第 5 节），它影响所有原生页面，超出本卡范围。

---

## 4. 提示词（复制给前端 Agent）

```
你是本项目的前端开发。项目是若依 RuoYi-Vue 3.9.2 二次开发的
「高校实验室资产与报修管理平台」，工作目录 D:\code\Manager_system，
前端在 RuoYi-Vue3（Vue 3.5 + Vite 6 + Element Plus 2.13）。
本次任务【只改前端样式与模板，不碰后端、不新增依赖】。

【任务背景】
实验室模块的三个业务页分别是
  RuoYi-Vue3/src/views/laboratory/room/index.vue
  RuoYi-Vue3/src/views/laboratory/asset/index.vue
  RuoYi-Vue3/src/views/laboratory/repair/index.vue
它们的根节点都写了 class="app-container laboratory-page"，
但是全项目搜索 laboratory-page 只有这 3 处使用、0 处定义 —— 也就是说
这个类名是个空壳，三个页面实际用的是若依默认样式（白底 + padding 20px + 表格贴边）。
而首页 RuoYi-Vue3/src/views/index.vue 已经是一套卡片化设计：
浅灰底 #f4f7fb、白色卡片、8px 圆角、#d9e2ef 描边、#172033 主文字、#667085 次要文字。
首页和业务页观感割裂，这是第一个要修的问题。

【第一步：先读这几个文件，不要凭印象改】
1) RuoYi-Vue3/src/assets/styles/index.scss   —— 看 @use 的引入顺序和 .app-container 的定义
2) RuoYi-Vue3/src/assets/styles/element-ui.scss —— 已有的 Element 覆盖样式，.el-dialog 规则就在里面
3) RuoYi-Vue3/src/views/index.vue            —— 抄它的色板变量，业务页要和它一致
4) 上面三个 laboratory 页面                  —— 逐个看根节点和 el-table

【第二步：新建 RuoYi-Vue3/src/assets/styles/laboratory.scss，并在 index.scss 里 @use 它】
在 index.scss 里，@use 语句必须都在文件最前面，加在 @use './ruoyi.scss'; 之后。

laboratory.scss 开头用 SCSS 变量沉淀色板（值必须和首页 index.vue 里的硬编码值完全一致）：
  $lab-page-bg: #f4f7fb;
  $lab-card-bg: #ffffff;
  $lab-border: #d9e2ef;
  $lab-text: #172033;
  $lab-text-sub: #667085;
  $lab-radius: 8px;

.laboratory-page 要实现：
  - 页面底色 $lab-page-bg；min-height: calc(100vh - 84px)，84 是顶部导航 50 + 标签栏 34，
    加一行注释说明这个来历，别让别人以为是魔法数字
  - 「搜索区 + 工具栏行」拼成一张卡片：
      搜索区 .el-form  →  padding: 16px 16px 0; margin-bottom: 0 !important;
                           border: 1px solid $lab-border; border-bottom: 0;
                           border-radius: 8px 8px 0 0; background: $lab-card-bg;
      工具行 .el-row.mb8 → padding: 12px 16px; margin-bottom: 0 !important;
                           border: 1px solid $lab-border; border-top: 0;
                           border-radius: 0 0 8px 8px; background: $lab-card-bg;
  【关键细节】搜索区可以被折叠（showSearch=false，v-show 写的是内联 display:none）。
    折叠后工具行就成了卡片顶部，如果还用「下圆角」会露出两条直角边。
    必须补一条：
      .laboratory-page > .el-form[style*="display: none"] + .el-row.mb8 {
        border-top: 1px solid $lab-border; border-radius: 8px;
      }
    注意 v-show=false 时 el-form 仍在 DOM 里（还是前一个兄弟节点），
    所以相邻兄弟选择器 + 这个属性选择器是可行的，不要试图用 :first-child 之类的思路绕。
  - 表格独立第二张卡：margin-top: 14px; border: 1px solid $lab-border;
    border-radius: 8px; overflow: hidden; background: $lab-card-bg;
    （overflow:hidden 是为了让截图表头的直角背景被圆角裁掉，别删）
  - .pagination-container 背景设透明（若依给它加了白底，在灰底页面上会是一条突兀白条）
  - 提供表格内双行信息单元（下面第三步要用）：
      .cell-stack { display: flex; flex-direction: column; gap: 2px; line-height: 1.4; }
      .cell-stack .cell-main { color: $lab-text; }
      .cell-stack .cell-sub  { color: $lab-text-sub; font-size: 12px; }

【第三步：改 repair/index.vue —— 表格列瘦身】
现状 13 列宽度合计 1975px（55+190+140+170+160+110+120+110+140+100+170+180+330），
1920 屏内容区可用宽约 1670px、1366 屏约 1116px，必然横向滚动，
而且右侧固定操作列独占 330px。请改成下面这套，合计 1535px：

  1) selection                         width=55    不变
  2) 「报修单号」prop=repairCode        min-width=160   （原 190，单号 16 字符够放）
  3) 【新列】「报修资产」min-width=200，用 .cell-stack 双行：
       上行 scope.row.assetName、下行 scope.row.assetCode
       原「资产编号」140 + 「资产名称」170 两列删掉
  4) 「实验室」prop=roomName            min-width=140   （原 160）
  5) 「故障等级」dict-tag               width=110    不变
  6) 「处理状态」dict-tag               width=120    不变
  7) 【新列】「申请人」min-width=130，用 .cell-stack 双行：
       上行 scope.row.applicantName、下行 scope.row.applicantPhone
       原「申请人」110 + 「联系电话」140 两列删掉
  8) 「评分」width=80（原 100），文案 `rating ? rating + " 分" : "-"`
  9) 「完成时间」width=160（原 170）
  10)「创建时间」width=160（原 180）
  11)「操作」width=220 fixed="right"（原 330）

  空值一律显示 "-"，沿用页面里已有的写法（`|| "-"`），不要留下空白单元格。
  操作列宽度改成 220 的依据要在代码里写一行注释：
    这几个按钮受权限和状态互斥控制，同时最多出现 3 个（student 在待审核态：
    详情/修改/删除；manager 在待审核态：详情/处理/删除；其他状态更少），
    原来的 330px 是按 5 个按钮预留的，属于过度预留。

【第四步：repair/index.vue 详情弹窗补摘要条】
弹窗现在点开后看不出这是哪一单。在 <el-form ref="repairRef" ...> 之前插入：
  <div v-if="form.repairId" class="repair-meta">
    <span><em>报修单号</em>{{ form.repairCode || "-" }}</span>
    <span><em>申请人</em>{{ form.applicantName || "-" }}</span>
    <span><em>提交时间</em>{{ form.createTime ? parseTime(form.createTime) : "-" }}</span>
  </div>
（form.createTime 后端 BaseEntity 已有，表格里已经在用 parseTime 渲染它）
样式写在 <style scoped> 里：横向 flex、浅蓝底 #f8fbff、1px #d9e2ef 描边、
8px 圆角、内边距 10px 14px、下方留 14px 间距；em 用 #667085 小字，
和值之间留 6px 间距，不要大写加粗。

【第五步：asset/index.vue 两处修正】
1) 履历时间线现在直接显示原始码，如「0 → 2」，而报修页的时间线显示的是
   「正常 → 维修中」，两条时间线口径不一致。改法：
   后端 LabStatusUtils.assetRecordType(status) 只会返回「启用」「停用」「维修」三种
   recordType，只有这三种的 fromValue/toValue 存的是资产状态码（0正常/1停用/2维修中）；
   其他 recordType（入库 / 报废 / 调拨 / 资料修改）存的是资产编号或实验室名称，不能过字典。
   所以在 <script setup> 里加一个常量和方法：
     // 与后端 LabStatusUtils.assetRecordType 的返回口径一一对应，
     // 只有这三种履历的 from/to 是资产状态码，其余存的是编号或房间名。
     // 后端改这个方法时，这里必须同步改。
     const ASSET_STATUS_RECORD_TYPES = ["启用", "停用", "维修"]
     function recordValueLabel(record, value) {
       if (!value) return "-"
       if (!ASSET_STATUS_RECORD_TYPES.includes(record.recordType)) return value
       return selectDictLabel(lab_asset_status.value, value) || value
     }
   模板里原来写的是 `{{ item.fromValue || "-" }} → {{ item.toValue || "-" }}`，
   改成 `{{ recordValueLabel(item, item.fromValue) }} → {{ recordValueLabel(item, item.toValue) }}`。
   【注意】fillable 的 show-overflow-tooltip 等既有属性一个都不要动。
   【注意】不要改后端 LabStatusUtils。

2) 资产类型下拉（el-select v-model="form.assetType"）现在带 filterable allow-create，
   允许用户输入字典以外的值，一旦存进去，表格里的 <dict-tag> 渲染不出来（会退化成
   没有颜色的裸文本）。请去掉 allow-create，保留 filterable。

【第六步：room/index.vue】
操作列的 el-table-column 补 fixed="right"（asset 和 repair 都有，room 漏了）。

【第七步：element-ui.scss 加一条窄屏保护】
文件里已有一个 .el-dialog 规则块（transform: none; left: 0; position: relative; margin: 0 auto;），
在同一个块里补一行 max-width: calc(100vw - 32px); 并写注释说明原因：
三个实验室页面共 7 个 el-dialog 宽度都写死了 420~860px，浏览器窗口缩到一半时
弹窗会超出视口，底部的确定/取消按钮既看不到也滚不到；
max-width 只在视口不足时生效，正常宽度下观感完全不变。

【验证（必须真跑）】
本机 Git Bash 的 PATH 经常损坏（ls/wc 都 command not found），请直接使用：
  node RuoYi-Vue3/node_modules/vite/bin/vite.js build --mode production
注意工作目录要在 RuoYi-Vue3 下，构建产物在 RuoYi-Vue3/dist，验证完请删掉 dist。
要求：构建成功、无 error；SCSS 不能有编译警告（@use 顺序错误会直接报错）。

【不要做的事】
- 不改任何后端代码、接口、数据库字段、字典数据
- 不动 src/views/system、monitor、tool 等若依原生页面
- 不引入任何新的 npm 依赖（离线环境装不上）
- 不改 views/index.vue 的视觉（它是设计基线）
- 不做深色模式适配
- 不为了省列宽而删掉数据展示（合并列是换位置显示，不是不显示）
- 不改 element-ui.scss 里 .fixed-width 那条规则（它用的是已废弃的 el-button--mini，
  在 Element Plus 下本来就不生效，但影响面覆盖所有原生页面，超出本次范围）

汇报：改动文件清单 + 报修表格列宽的前后合计 + vite build 的真实输出 + 有没有遇到 SCSS 编译问题。
```

---

## 5. 执行指导与踩坑

| 坑 | 说明 |
| --- | --- |
| **`@use` 必须在文件顶部** | SCSS 的 `@use` 要求出现在所有其它规则之前，写在中间会直接编译失败。`index.scss` 已有的 6 行 `@use` 之后追加即可。 |
| **`v-show` 折叠后卡片会露直角** | 这是本卡最容易漏的一处。`v-show="false"` 的元素**仍在 DOM 里**，仍是前一个兄弟节点，所以 `+` 选择器照样匹配——`el-rows` 会误认为上方还有搜索区，于是不给自己加圆角。必须叠加 `[style*="display: none"]` 属性选择器来识别。 |
| **`el-table` 圆角必须配 `overflow: hidden`** | 表头有自己的背景色（`--el-table-header-bg-color`），是个矩形，只给 `.el-table` 加 `border-radius` 的话，表头的直角会从圆角外面露出来。 |
| **`.pagination-container` 自带白底** | 若依给它设了 `background: #fff`。在 `#f4f7fb` 的页面底色上会变成一条突兀的白条，必须置为透明。 |
| **表格单元格的 `:deep` 陷阱** | `.cell-stack` 用在 `el-table-column` 的插槽里，属于子组件渲染的内容。**写在页面 `<style scoped>` 里会被 scoped 属性隔离掉**，所以这几个类名必须放进全局 `laboratory.scss`。 |
| **合并列不能丢数据** | 「资产编号+名称」合并后，`assetCode` 变成第二行小字。必须确认 `scope.row.assetCode` 在列表接口里确实返回（`LabRepairMapper` 的列表 SQL 有 join `lab_asset`），否则第二行永远是 `-`。 |
| **操作列宽度的算法** | 不要按「按钮总数」算，要按「**同时最多可见的按钮数**」算。`repair/index.vue` 里 5 个按钮受 `canStudentEdit` / `canAudit` / `canEvaluate` / `canDelete` 四个条件与 `v-hasPermi` 双重约束，实测任意角色任意状态最多 3 个。 |
| **`.fixed-width` 是失效的死样式** | `element-ui.scss` 里的 `.fixed-width .el-button--mini { width: 60px }` 用的是 **Element Plus 已废弃的 `el-button--mini`**（EP 里只有 `large/default/small`）。三个页面的操作列都写了 `class-name="small-padding fixed-width"`，但实际只有 `small-padding` 生效。**这是若依 Vue3 迁移时的遗留问题，本卡不修**（影响所有原生页面），但要记在案，免得以后有人以为是自己的样式没生效。 |
| **构建产物要清** | `RuoYi-Vue3/dist` 已被 `.gitignore` 忽略，但打源码 ZIP 前要确认它不在包内，否则包体白白变大。 |
| **不要顺手改首页** | 首页是已经定稿的视觉基线，业务页向它对齐。改首页会让已有的演示截图和 PPT 素材全部作废。 |

---

## 6. 验收方式

```bash
# 1. 类名不再悬空：laboratory-page 有定义
grep -rn "laboratory-page" RuoYi-Vue3/src/assets/styles/
# 期望：至少 1 处命中（laboratory.scss 里的定义）

# 2. 三个业务页仍然引用该类名
grep -rln "laboratory-page" RuoYi-Vue3/src/views/laboratory/
# 期望：3 个文件

# 3. 报修表格列宽已收敛（人工核对：合计应为 1535px）
grep -n "min-width=\|width=" RuoYi-Vue3/src/views/laboratory/repair/index.vue | head -20

# 4. 字典外取值已封堵
grep -n "allow-create" RuoYi-Vue3/src/views/laboratory/asset/index.vue
# 期望：无输出

# 5. 三个页面操作列都固定右侧
grep -c "fixed=\"right\"" RuoYi-Vue3/src/views/laboratory/*/index.vue
# 期望：room 1 / asset 1 / repair 1

# 6. 窄屏保护已加
grep -n "max-width" RuoYi-Vue3/src/assets/styles/element-ui.scss
# 期望：命中 calc(100vw - 32px)

# 7. 前端构建通过（本机 Git Bash 的 PATH 不可靠，直接用 node 调 vite）
cd RuoYi-Vue3 && node node_modules/vite/bin/vite.js build --mode production
```

**通过标准**：构建成功无 error；上列 6 条 grep 全部符合期望；浏览器里目视确认三个业务页与首页为同一套视觉、报修表格在 1920 宽下不再横向滚动。

---

## 7. 留痕要求

在 `docs/大作业/AI留痕/` 下记一份 T6 记录（按 `AI留痕/README.md` 的模板），**必须包含**：

- 完整提示词（本卡第 4 节原文）
- 报修表格列宽改造前后的**合计数字对比**（1975 → 1535），这是 AI 报告里「效率对比」的现成素材
- 改动文件清单与行数变化
- **人工修改点**（AI 报告素材，建议从这里挑）：
  - AI 大概率会漏掉「搜索区折叠后工具栏行露直角」这个边界情况 → 人工在浏览器里折叠搜索区发现了，补上属性选择器 → **这是很典型的「AI 只改了正常路径、没覆盖边界态」案例**
  - AI 可能为了压缩列宽直接把「联系电话」列删掉（属于丢数据）→ 人工改成合并列 → 说明「压缩」和「删减」的边界
- 抄送答辩组：改造后的界面截图可直接用于答辩 PPT「核心功能界面演示」那一页

---

## 8. 执行进度（截至 2026-09-17 18:30 ｜ **未完成**）

> 状态：🔄 进行中。**仓库当前处于中间态**：模板改动已落，但配套的样式文件还没建。
> 这不会报错（缺样式只是观感退化，不会崩），但本卡在样式文件补齐前**不算完成**。

### 已改动（4 个文件）

| 文件 | 改了什么 |
| --- | --- |
| `views/laboratory/repair/index.vue` | 表格列 1975 → 1535px（合并出「报修资产」「申请人」两列、操作列 330 → 220）；弹窗新增 `.repair-meta` 摘要条与配套 scoped 样式 |
| `views/laboratory/asset/index.vue` | 资产类型下拉去掉 `allow-create`；履历时间线接入 `recordValueLabel()` 做状态字典翻译 |
| `views/laboratory/room/index.vue` | 操作列补 `fixed="right"` |
| `assets/styles/element-ui.scss` | `.el-dialog` 补 `max-width: calc(100vw - 32px)` |

### 未完成（**本卡的交付核心，下一轮第一件事**）

1. **`assets/styles/laboratory.scss` 尚未创建** —— `laboratory-page` 目前**仍是死类名**，第 2.1 节的全部要求都没落地。
2. **`assets/styles/index.scss` 还没 `@use` 它**。
3. **`.cell-stack` / `.cell-main` / `.cell-sub` 已出现在 `repair/index.vue` 的模板里，但全项目没有定义**
   —— 当前表现为「资产名称 + 编号」「申请人 + 电话」两组信息字号与颜色完全相同，**没有主次层次**。
   数据没丢、功能正常，只是合并列的设计意图还没兑现。
4. 第 6 节的 7 条验收 grep 全部未跑；前端构建未验证。

### 下一轮开工前的提醒

- 先看 `git status`（本仓库会被其他会话并行修改，任务卡里的现状数字可能已过期）。
- 三个业务页的 `laboratory-page` 类名**不要删**，样式文件一建就自动生效。
- 样式建完必须跑构建：`cd RuoYi-Vue3 && node node_modules/vite/bin/vite.js build --mode production`。

---

## 9. 2026-09-17 18:50 复核（视觉设计负责人）

> 本节由**答辩材料与视觉设计负责人**在核对 PPT 视觉规范时顺带复核，**不是 T6 的验收结论**。

**发现：第 8 节声明的「未完成」三项，代码侧已被并行会话补齐。**

| 第 8 节的原结论 | 2026-09-17 18:50 复核结果 |
| --- | --- |
| `assets/styles/laboratory.scss` 未创建 | **已存在**（98 行），且第 2.1 节要求全部落地：色板变量 6 个、搜索区+工具行拼卡、折叠态属性选择器、表格独立卡 + `overflow: hidden`、分页容器透明、`.cell-stack` 三件套 |
| `index.scss` 还没 `@use` 它 | **已有** `index.scss` 第 7 行 `@use './laboratory.scss';`（`@use` 顺序正确，排在 `ruoyi.scss` 之后） |
| `.cell-stack` 全项目无定义 | **已定义**（`laboratory.scss` 第 83–97 行） |

**因此请注意两点：**

1. **不要再新建 `laboratory.scss`**——会覆盖掉已落地的实现。
2. **T6 仍不算完成**：第 6 节的 7 条验收 grep 与前端构建**本会话未执行**，
   `dist/` 产物未核对。请由编码负责人跑完验收再改第 6 节台账状态。

**顺带产出**：本次复核把色板抄进了 D2 卡（PPT 视觉规范直接复用这套值），
避免 PPT 与系统界面出现两套色。

---

## 10. 2026-09-17 20:09 验收复核（UI设计）

> 本节是 **T6 的首次真实验收**：第 6 节的 6 条 grep 与前端构建**本会话全部跑通**。
> 第 8 节声明的"未完成三项"已被并行会话补齐（见第 9 节），本节不重复。

### 10.1 验收命令的实际结果（不是复述卡片）

| # | 验收项 | 实际结果 | 判定 |
| :-: | --- | --- | :-: |
| 1 | `laboratory-page` 在 `assets/styles/` 有定义 | 7 处命中：`laboratory.scss` 6 条规则 + 1 处注释 | ✅ |
| 2 | 三个业务页仍引用该类名 | room / asset / repair 共 3 个文件 | ✅ |
| 3 | 报修表格列宽 | 逐列相加 = 55+160+200+140+110+120+130+80+160+160+220 = **1535px** | ✅ |
| 4 | `allow-create` 已封堵 | `views/laboratory/` 下搜 `allow-create` / `allowCreate` **无匹配** | ✅ |
| 5 | 三页操作列固定右侧 | room 1 / asset 1 / repair 1 | ✅ |
| 6 | 窄屏 `max-width` 保护 | `element-ui.scss:58` = `max-width: calc(100vw - 32px)` | ✅ |
| 7 | 前端构建 | `✓ 2545 modules transformed` / `✓ built in 30.82s` / **EXIT=0**，无 error 无 warn | ✅ |

补充核验（不在 6 条内，但属于完成判据）：

- `index.scss:7` = `@use './laboratory.scss';`，位置在所有规则之前（SCSS 的硬要求）✅
- `repair/index.vue:103-108` 的 `.repair-meta` 摘要条已落地 ✅
- `asset/index.vue:191` 模板已调用 `recordValueLabel()`，函数与 `ASSET_STATUS_RECORD_TYPES` 定义在 `:403-410` ✅
- **合并列的数据源已确认**（第 5 节「合并列不能丢数据」那条踩坑的静态验证）：
  `LabRepairMapper.xml` 的 `selectLabRepairList` include 了 `selectLabRepairVo`，
  其中 `:37` 有 `a.asset_code`（`left join lab_asset`）、`:38` 有 `r.applicant_phone`
  → 双行信息的第二行**不会恒为 `-`** ✅
- 构建产物 `dist/` 顶层为 `static/ favicon.ico index.html index.html.gz`，**无异常嵌套**，已按第 5 节要求清理 ✅

### 10.2 本轮修掉的一处缺陷（T6 卡内，非新偏移）

**`.pagination-container` 的透明背景此前实际不生效。**

原实现：

```scss
.laboratory-page .pagination-container { background: transparent; }
```

若依那条白底**不在全局样式里**，而在 `components/Pagination/index.vue` 的 `<style scoped>` 中，
编译后形如 `.pagination-container[data-v-*]` —— **与本条权重完全相同**（都是两个选择器）。
组件样式随异步路由 chunk 后加载，同权重下后者胜 → **白条依旧残留，2.1 节该复选框此前并未真正达成。**

**修法**：加 `!important` 并写明原因，已随本轮构建验证。

> **这条是第 8 节那种"文件存在 ≠ 交付达成"的又一例**：样式规则的**权重与加载顺序**
> 决定了它是否真的生效，只看文件里有那行代码是不够的。

### 10.3 仍未收口 / 必须目视确认的 4 项

第 6 节的**通过标准最后一条是「浏览器里目视确认」**，本会话**未执行**
（需 dev server + 后端 8080 + MySQL + Redis）。以下 4 项**静态代码无法证伪**：

| # | 待确认项 | 为什么静态看不出来 |
| :-: | --- | --- |
| **V-1** | 固定列在横向滚动时是否正常跟随 | `laboratory.scss` 给 `> .el-table` 加了 `overflow: hidden`。**推导结论是安全的**：EP 的 `position: sticky` 取**最近**的可滚动祖先，而 `.el-scrollbar__wrap` 就在单元格的直系祖先链上、比 `.el-table` 更近。但 EP 2.13 的实际 DOM 结构未经实机确认 |
| **V-2** | 搜索区折叠后工具栏行的圆角 | `[style*="display: none"]` 依赖 EP 把 `v-show` 写成内联 `display: none;`。这正是第 5 节点名的"最容易漏的一处"，**假设尚未验证** |
| **V-3** | 11 列在 1920 宽下不再横滚 | 5 列用 `min-width`、6 列用 `width`，EP 在 `table-layout="fixed"` 下如何分配剩余空间需目视 |
| **V-4** | 操作列 220px 是否放得下 3 个带图标按钮 | `class-name="small-padding fixed-width"` 里的 `fixed-width` **在 EP 下已失效**（第 5 节），按钮宽度实际不受该样式约束 |

> **V-1 与 V-4 是本轮最大的不确定性**：一个关系到"固定列还能不能用"，一个关系到"操作按钮会不会换行"，
> 两者都是答辩演示时会被当场看见的问题。**在目视确认前，T6 不应标 ✅。**

### 10.4 需要「代码负责」配合的事项

**本轮的结论：无必须项。**

- 合并列所需字段（`assetCode` / `applicantPhone`）已确认由后端列表接口返回，**不需要改接口或 SQL**。
- 若 V-1 在目视中被证实影响了固定列，**修法也在前端侧**（去掉 `overflow: hidden`，或改为只给表头首尾单元格做圆角），不需要后端参与。
