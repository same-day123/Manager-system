# Manager_system · 项目长期记忆

> **公共入口 = 仓库根 `AGENTS.md`**（每轮头部有详版纪要）——接手先读它。
> **本机环境／构建／截图／git 踩坑 = 同级 `ENV_PITFALLS.md`**。
> 本文件只留代码约定与状态锚点。Claw 担六角色（PM/前端UI/编码/测试/文档/PPT和设计），边界唯一权威 = `AGENTS.md` 0.5 节；决策权归阿辉。

## 栈与数据
- 若依 RuoYi-Vue 3.9.2 二开：实验室资产台账 + 报修闭环；自定义码只在 `com.ruoyi.project.laboratory`。
- 后端 Boot 2.5.15 / Java 8 / MyBatis / Redis / JWT（8080）；前端 Vue 3.5 / Vite 6 / EP 2.13（dev 80）。MySQL 8 `education_system`（root/123456）+ Redis 必启。
- 演示账号 labadmin 等 6 个，**真实口令 `admin123`**（D-20；文档写 123456 是错的）。远程 `github.com/same-day123/Manager-system`；**PAT 已吊销：只 commit 禁 push**。

## 代码约定（细则看 AGENTS.md 与任务卡）
1. 角色口径三处同步：`LabRoleUtils` / `utils/labPermission.js` / `laboratory_permission_fix.sql` 末尾（D-10）。
2. 状态机 0→1→2→3、0→4；非法流转抛 `ServiceException`；null/同状态放行。联动：报修提交→资产 2，完成/拒绝/删待审核→回 0。
3. 逻辑删除 `del_flag='2'`，查询带 `'0'`；业务编号只建普通索引不建唯一索引（唯一性走 `checkXxxUnique`）。
4. SQL 按 `[N/7]` 顺序可重复执行；按需：`index_fix` / `cleanup` / `demo_refresh`（不参与顺序）。
5. 履历写入单点 `LabRecordFactory` + 事件枚举；文案含全角引号/分号，整段复制别手打。
6. 卡的示例 ≠ 事实：动手前 `git show HEAD:` 核现状；纯重构验收 = 静态逐字节 + 动态探针两层。
7. 容器化：配置覆盖全走环境变量（`RUOYI_PROFILE`/`SPRING_REDIS_HOST,PORT`/`RUOYI_DB_*`）；SQL 初始化只走 `deploy/mysql-init.sh` 显式顺序；nginx 末尾斜杠删不得 + `client_max_body_size 20m`；`.gitignore` 禁写 `.env.*`。无 Docker 验证 = `t4_verify.py` 94 断言 + `t4_env_binding_check.py` 23 断言。
8. 测试：H2 锁 1.4.199；验收一律 `clean test`；覆盖率 % 出不来（缺 JaCoCo），只写矩阵 + 断言 177 / verify 27；WMIC 噪音不影响测试（D-26）。

## 状态锚点（权威 = AGENTS.md §6/§7）
- T0~T5 已通；测试基线 **49 全绿**（33 单元 + 16 集成）。
- D1：**5 份 PDF 已出齐**（D3 打包真跑验证过）；PDF 流水线 = `.workbuddy/tools/md2pdf/`。压页数：表格 `page-break-inside:avoid` 会整张跳页致估页低估 1 页；收紧 CSS 上下限一起盯。
- **D2 答辩 PPT：14/14 页出稿 + 实体文件**（`docs/大作业/答辩PPT/`）。**交付 = `答辩PPT-v2.pptx`**（v1 文本层有导出缺陷仅留档）+ `答辩PPT-v1.pdf` + 导出预览 14 图。**⚠️ Ardot pptx 导出会剥文本首尾 CJK/全角串**（类 = U+3000-303F / 4E00-9FFF / F900-FAFF / FF00-FFEF；`·`、`①` 不剥）→ 修复脚本 `.workbuddy/tools/d2_pptx_repair.py`（画布真值回填 297 run）；PNG/PDF 不受影响。剩：第 9 页截图位替换（T6-01 已重拍）、分工认领 Q-08。**答辩截图须 9-19 冻结前用掉**。
- D-24/D-25 已收口（`demo_refresh.sql` 已执行；**答辩前 1 天再跑一次校准**）。
- D3：打包脚本 `d3_package.py` 已就绪并真跑验证；**9-20 出包前先 commit 全部改动再执行**。
- 容忍偏移（答辩主动说明）：D-01 / D-06 / D-08（+ D-26 / D-28 已上 PPT 第 12 页）。
