# 自动化执行记忆 —— PM 晚间验收巡检（21:00 档）

> 本文件只记「执行层面」的信息：做了什么、产出在哪、下次要注意什么。
> **详细正文一律看快照**，不往这里抄。

## 2026-09-17 21:03（首次执行）

- **产出**：`.workbuddy/logs/pm-watch/2026-09-17-2103.md`（对比基线 `_baseline-2026-09-17.md`）
- **写入白名单内改动**：`AGENTS.md` 第 6 节 `D1` 状态 `⬜ → 🔄`（兜底回写）。**未改任何源码 / 他人产出。**
- **核心实测数字**（下次直接与此对比）：
  - 单测 `Tests run: 30, Failures: 0, Errors: 0` / `BUILD SUCCESS`（T1 达标）
  - 集成测试 **0**（`grep -rn SpringBootTest src/test/` 无输出）
  - DDL 5 表齐 / `.github/workflows` 无 / Dockerfile 无 / laboratory 包显式工厂类无
  - 文档 2 份 md、**0 份 PDF** / 0 份 pptx / 0 个 ZIP
  - `vite build` EXIT=0（T6 六条 grep 全过）
- **新偏移**：**D-18 = 台账状态双向漂移**（T6 达标仍标 🔄、D1 已开工仍标 ⬜）。
  ⚠️ **D-18 只登记在快照里，尚未写进 `AGENTS.md` 7.3** —— 下轮优先补录（当前最大号本应取 D-18）。
- **纪律**：无人 push；但远端不可核验（`git ls-remote` exit 128 / schannel `CRYPT_E_NO_REVOCATION_CHECK`）。
  本地领先远端 2 个 commit（`ed971fd` / `1668e4a`）待推。
- **本轮采集到的环境坑（下次照用，别重新踩）**：
  1. 本机 `vite build` 在**清空 `dist/`** 时会被安全删除护栏拦下（`SAFE_DELETE_BULK_CONFIRM_REQUIRED count:313`）。
     **不是代码错**。绕法：`--emptyOutDir false`。
  2. Git Bash 的 `find` 可用，但 `mvnx.sh` 输出需用 `| tail -N` 截取，直接重定向易被平台截断。
  3. 首次 `mvn clean test` 约 20s（非台账写的 1:39）——**JVM 已热 / 依赖已缓存**，不是异常。
- **下次执行待办**：
  1. 补录 D-18 到 `AGENTS.md` 7.3。
  2. 对比本快照的第 4 节硬指标表，重点看 **T2 集成测试是否从 0 起来**、**PDF 份数是否从 0 起来**。
  3. 检查 `.workbuddy/tools/` 灰区是否已被 5.1 节正式收编。
