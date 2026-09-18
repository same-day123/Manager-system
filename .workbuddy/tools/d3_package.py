# -*- coding: utf-8 -*-
"""
D3 源码 ZIP 打包（代码负责，9-20 执行，现在只备脚本）

设计原则：
  - 用 `git archive` 只导出「已跟踪文件」，天然排除 target/、dist/、node_modules/、
    .workbuddy/logs/ 等构建产物与运行日志 —— **不需要真的去删任何东西**，
    避开「安全删除护栏」对 target/dist 的风险，也保证 ZIP 与仓库一致。
  - 5 份 PDF 必须齐全才打包（归「文档负责」，当前 3/5）；缺任何一份立即报错、不产出残缺包。
  - 排除 .workbuddy/tools/ 下的巡检/探针临时脚本（内部调试产物，不进交付物）。

命名（唯一来源 = docs/大作业/封面署名信息.md 第四节）：
  软件新技术专题-第6组-高校实验室资产与报修管理平台.zip
  题目 = 「高校实验室资产与报修管理平台」；组号 = 第 6 组。

用法（在仓库根目录跑）：
  python .workbuddy/tools/d3_package.py            # 校验 + 打包到仓库根
  python .workbuddy/tools/d3_package.py --dry-run  # 只校验，不产出 ZIP
  python .workbuddy/tools/d3_package.py --out <路径>  # 打包到指定路径（测试用）
"""
import os
import re
import sys
import subprocess

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

ZIP_NAME = "软件新技术专题-第6组-高校实验室资产与报修管理平台.zip"

# 五份交付文档 PDF（唯一来源 = AGENTS.md 交付物 1~5）
# 注意：03/04 的真实文件名用下划线「CI_CD」而非连字符，与 md 源文件名一致。
PDF_REQUIRED = [
    "docs/大作业/文档/pdf/01-需求规格说明书.pdf",
    "docs/大作业/文档/pdf/02-软件架构设计文档.pdf",
    "docs/大作业/文档/pdf/03-测试计划与用例文档.pdf",
    "docs/大作业/文档/pdf/04-CI_CD部署方案.pdf",
    "docs/大作业/文档/pdf/05-AI辅助开发实践报告.pdf",
]

# .workbuddy/tools/ 下不进入交付物 ZIP 的临时调试脚本（巡检/探针类）
EXCLUDE_PREFIXES = (
    ".workbuddy/tools/patrol_",
    ".workbuddy/tools/ui_probe",
    ".workbuddy/tools/ui_login",
    ".workbuddy/tools/ui_captcha",
    ".workbuddy/tools/ui_shots",
    ".workbuddy/tools/cdp_shot",
    ".workbuddy/tools/h2probe/",
    ".workbuddy/tools/T5EventProbe",
)

def run(cmd):
    # 注意：Windows 下 git 输出含中文文件名时是 GBK/系统编码，不能用 encoding="utf-8"
    # 硬解，否则 UnicodeDecodeError。这里取 bytes 后用 utf-8 回退 gbk 解码。
    r = subprocess.run(cmd, cwd=ROOT, capture_output=True)
    def _dec(b):
        if not b:
            return ""
        for enc in ("utf-8", "gbk", "utf-8-sig"):
            try:
                return b.decode(enc)
            except UnicodeDecodeError:
                continue
        return b.decode("utf-8", "replace")
    r.stdout = _dec(r.stdout)
    r.stderr = _dec(r.stderr)
    return r


def git_tracked():
    """返回仓库所有已跟踪文件路径（相对 ROOT，正斜杠）。"""
    r = run(["git", "ls-files"])
    if r.returncode != 0:
        sys.exit(f"[FATAL] git ls-files 失败：{r.stderr.strip()}")
    return [l for l in r.stdout.splitlines() if l.strip()]


def git_is_dirty():
    """工作区是否有未提交改动（打包前提醒，不强制阻断）。"""
    r = run(["git", "status", "--porcelain"])
    return bool(r.stdout.strip())


def main():
    dry = "--dry-run" in sys.argv
    out_override = None
    if "--out" in sys.argv:
        i = sys.argv.index("--out")
        if i + 1 >= len(sys.argv):
            sys.exit("[FATAL] --out 需要跟一个路径参数")
        out_override = sys.argv[i + 1]

    print("=" * 60)
    print("D3 源码 ZIP 打包 · 预检")
    print("=" * 60)

    # 0. 是否在仓库根
    if not os.path.isfile(os.path.join(ROOT, "AGENTS.md")):
        sys.exit("[FATAL] 不在仓库根目录（找不到 AGENTS.md），脚本只能在仓库根跑")

    # 1. 五份 PDF 齐全性（D3 的硬前提，缺一份就不打包）
    print("\n[1] 交付 PDF 齐全性（需 5/5）")
    missing = []
    for p in PDF_REQUIRED:
        full = os.path.join(ROOT, p)
        ok = os.path.isfile(full)
        print(f"    {'OK ' if ok else 'MISS'}  {p}")
        if not ok:
            missing.append(p)
    if missing:
        print(f"\n[BLOCKED] 缺 {len(missing)} 份 PDF，今天不是打包日，停止。")
        print("    缺失清单：")
        for p in missing:
            print("      - " + p)
        print("    等「文档负责」交齐后再跑本脚本。")
        sys.exit(2)

    # 2. 排除清单落定
    exclude = list(EXCLUDE_PREFIXES)
    tracked = git_tracked()
    to_include = [p for p in tracked if not any(p.startswith(x) for x in exclude)]
    excluded_count = len(tracked) - len(to_include)

    # 3. 安全检查：ZIP 里绝不允许出现这些
    print("\n[2] 内容安全检查（目标：全部 0）")
    checks = {
        "target/":        [p for p in to_include if "/target/" in p],
        "dist/":          [p for p in to_include if "/dist/" in p],
        "node_modules/":  [p for p in to_include if "node_modules" in p],
        ".workbuddy/logs/": [p for p in to_include if p.startswith(".workbuddy/logs/")],
        "根 .env 文件":    [p for p in to_include if re.match(r"^(\.env|.*/\.env)$", p)],
    }
    for label, hits in checks.items():
        flag = "OK " if not hits else "BAD"
        print(f"    {flag}  {label}: {len(hits)} 个")
        if hits:
            for h in hits[:5]:
                print(f"          - {h}")

    # 4. 规模概览
    print("\n[3] 内容规模")
    print(f"    仓库已跟踪 {len(tracked)} 个文件")
    print(f"    排除临时脚本 {excluded_count} 个，打包 {len(to_include)} 个")
    # 关键路径是否都在
    must_have = [
        "RuoYi-Vue-fast/pom.xml",
        "RuoYi-Vue3/package.json",
        "RuoYi-Vue-fast/sql/laboratory_schema.sql",
        "README.md",
        ".github/workflows/ci.yml",
        "docker-compose.yml",
        "RuoYi-Vue-fast/Dockerfile",
        "RuoYi-Vue3/nginx.conf",
    ]
    print("\n[4] 关键文件存在性")
    allok = True
    for m in must_have:
        ok = m in to_include
        allok = allok and ok
        print(f"    {'OK ' if ok else 'MISS'}  {m}")
    if not allok:
        sys.exit("[FATAL] 关键文件缺失，请先核对仓库状态")

    # 5. 未提交改动提醒
    if git_is_dirty():
        print("\n[WARN] 工作区有未提交改动。打包的是「已提交版本」（git archive 语义），")
        print("       未提交的改动不会进 ZIP。若这些改动要交付，请先 commit。")

    if dry:
        print("\n[DONE] dry-run 完成，未产出 ZIP。")
        return

    # 6. 出包：`git archive --format=zip` 直接产出 zip（只含已跟踪文件），
    #    再由 Python 用 zipfile 读进来、过滤掉排除项后重写。
    #    全程不依赖系统 tar、不落临时文件树，Windows / Linux / macOS 行为一致。
    import subprocess as sp
    import zipfile
    import io

    out_zip = out_override or os.path.join(ROOT, ZIP_NAME)
    if os.path.exists(out_zip):
        os.remove(out_zip)

    # 6.1 让 git 把 zip 写到 stdout，我们直接收字节（避开 git 写中文路径的坑）
    p = sp.Popen(["git", "archive", "--format=zip", "HEAD"],
                 cwd=ROOT, stdout=sp.PIPE, stderr=sp.PIPE)
    raw, err = p.communicate()
    if p.returncode != 0:
        sys.exit(f"[FATAL] git archive 失败：{err.decode('utf-8','replace').strip()}")

    # 6.2 读进内存，过滤排除项后重写
    src = zipfile.ZipFile(io.BytesIO(raw), "r")
    removed = 0
    n = 0
    with zipfile.ZipFile(out_zip, "w", zipfile.ZIP_DEFLATED) as dst:
        for info in src.infolist():
            name = info.filename
            if name.endswith("/"):
                continue  # 目录条目，zipfile 重写时不需要显式保留
            if any(name.startswith(x) for x in exclude):
                removed += 1
                continue
            if name in PDF_REQUIRED:
                continue  # PDF 统一在 6.3 从工作区写，避免与 git 历史版本重复
            dst.writestr(info, src.read(info.filename))
            n += 1
        # 6.3 关键：5 份 PDF 是「文档负责」的活跃产出，其中 03/04 尚未 git 跟踪、
        #       01/02/05 已跟踪但工作区是定稿后的新版本（git archive 只给旧版）。
        #       所以 PDF 一律【从工作区读最新文件】写入 ZIP，保证交付的是定稿，
        #       且只出现一次（6.2 已跳过 git 里的 PDF）。
        pdf_written = 0
        for pdf in PDF_REQUIRED:
            fp = os.path.join(ROOT, pdf.replace("/", os.sep))
            with open(fp, "rb") as fh:
                dst.writestr(pdf, fh.read())
            pdf_written += 1
            n += 1
    src.close()
    size = os.path.getsize(out_zip) / 1024 / 1024
    print("\n" + "=" * 60)
    print(f"[OK] 打包完成：{os.path.basename(out_zip)}")
    print(f"     文件数 {n}，大小 {size:.2f} MB，已排除临时脚本 {removed} 个")
    print(f"     PDF 已从工作区取定稿版写入 {pdf_written} 份")
    print(f"     输出路径：{out_zip}")
    print("=" * 60)


if __name__ == "__main__":
    main()
