# D3 源码 ZIP 打包清单（代码负责 · 9-20 执行）

> 本文件是 D3 打包的**唯一执行依据**，与 `.workbuddy/tools/d3_package.py`（打包脚本）配套。
> 今天（2026-09-18）只备脚本与清单，**不动包**；9-20 一键出包。

## 一、交付物与命名（唯一来源）

| 项 | 值 | 来源 |
| --- | --- | --- |
| ZIP 文件名 | **`软件新技术专题-第6组-高校实验室资产与报修管理平台.zip`** | `docs/大作业/封面署名信息.md` 第四节 |
| 项目名称 | 高校实验室资产与报修管理平台 | 同上 |
| 组号 | 第 6 组 | 同上 |
| 硬指标 | 前后端源码 + SQL 脚本 + 配置文件；按 README 可复现 | `docs/大作业/02-交付物验收清单.md` 交付物 7 |

## 二、ZIP 里「有什么 / 不有什么」

### 会打包（`git archive HEAD` 的已跟踪文件，含以下关键路径）

| 路径 | 内容 |
| --- | --- |
| `RuoYi-Vue-fast/` | 后端源码（`src/main/**` + `pom.xml` + `sql/*.sql` + `Dockerfile`） |
| `RuoYi-Vue3/` | 前端源码（`src/**` + `package.json` + `yarn.lock` + `.env.*` + `nginx.conf` + `Dockerfile`） |
| `README.md` / `AGENTS.md` | 项目说明与协作记忆 |
| `.github/workflows/ci.yml` | CI 流水线 |
| `docker-compose.yml` / `docker-compose.prod.yml` / `.env.example` / `deploy/` | 容器化交付 |
| `docs/大作业/` 下**除文档/截图/留痕外的**项目管理与任务卡 | 需求基线、任务卡、设计模式类图等 |
| 5 份交付 PDF | **从工作区读定稿版**（见下） |

### 明确排除（脚本自动挡，无需手删）

| 排除项 | 为什么 |
| --- | --- |
| `target/` `dist/` `node_modules/` `.workbuddy/logs/` | 构建产物与运行日志，`.gitignore` 已挡 |
| `根目录 .env` | 敏感配置 |
| `.workbuddy/tools/` 下的 `patrol_*` `ui_probe*` `ui_login` `ui_captcha` `ui_shots` `cdp_shot` `h2probe/` `T5EventProbe` | 巡检/探针临时调试脚本，内部产物 |

> **不需要真的去删 target/dist**：脚本用 `git archive` 只导出已跟踪文件，天然排除构建产物，
> 避开「安全删除护栏」对 target/dist 的风险，也保证 ZIP 与仓库一致。

## 三、5 份 PDF 的特别处理（关键，务必读）

PDF 是「文档负责」的**活跃产出**，不能从 git 历史取：

- 现状（2026-09-18）：`01/02/05` 已跟踪但工作区是**定稿后新版**；`03/04` **尚未 git 跟踪**（`??` 状态）。
- 所以脚本在 `git archive` 之后，**统一从工作区读这 5 份 PDF 覆盖进 ZIP**（跳过 git 里的同名条目，避免重复）。
- 这保证交付的是**定稿版**，而不是 git 里的旧版。

## 四、9-20 打包操作步骤

```bash
cd D:/code/Manager_system

# 1. 确认 5 份 PDF 齐全（脚本会自己查，缺一份就报错不打包）
python .workbuddy/tools/d3_package.py --dry-run

# 2. 一键出包（产出到仓库根）
python .workbuddy/tools/d3_package.py

# 3. 核对产物
ls -lh "软件新技术专题-第6组-高校实验室资产与报修管理平台.zip"
```

产出后人工核对三件事：

1. ZIP 大小合理（约 8~9 MB），**不是几百 MB**（若异常大，说明混进了依赖/构建产物，停下查）。
2. 解压后 `README.md` 在最上层、`RuoYi-Vue-fast/`、`RuoYi-Vue3/` 都在。
3. 5 份 PDF 都在 `docs/大作业/文档/pdf/` 下且能打开。

## 五、打包前必须做的一件事（⚠️ 容易漏）

**先把「文档负责」的最新 PDF 与「代码负责」自己的提交都 commit**，否则 `git archive` 拿到的源码是旧版。

- 代码侧：T0/T3/T4/T5 + 本轮 D-24/D-25 校准，**当前有 8 个 commit 未推送**（`57c81b9..HEAD`）。
- 文档侧：03/04 两份 PDF 及源 md 尚未 commit（`??` 状态），是「文档负责」的边界，**我只提示、不代提交**。

## 六、边界自检

| 项 | 结论 |
| --- | --- |
| `src/main/**`、`src/test/**` | 本轮**零改动**（打包不动源码） |
| `docs/大作业/文档/**` | 只**读** 5 份 PDF，不写不改（归文档负责） |
| 前端 `src/` | **冻结期不动**（截图是答辩 PPT 素材） |
| 产出 | 脚本 + 清单，无 ZIP（今天不打包） |
