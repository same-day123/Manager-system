#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""ci.yml 结构自检（本机离线、无 PyYAML / actionlint 时的替代手段）

不是完整 YAML 解析器，只做「结构 + 缩进」层面的静态自检，覆盖最容易出错、
也最容易被 GitHub 拒绝的几类问题：
  1. 是否存在 Tab（YAML 禁止用 Tab 缩进）
  2. 缩进是否统一为 2 的倍数
  3. 顶层键是否符合预期（name / on / concurrency / permissions / jobs ...）
  4. jobs 下是否恰好是 backend / frontend / package（+ 被注释掉的 docker）
  5. 每个 job 是否带 runs-on 与 steps
  6. package 是否声明 needs: [backend, frontend]
  7. 是否出现疑似硬编码密码（password: 后面跟字面量而不是 secrets）
  8. 测试阶段是否被偷偷加了 -DskipTests

用法：python .workbuddy/tools/ci_yml_check.py .github/workflows/ci.yml
"""
import re
import sys

def main(path):
    with open(path, "r", encoding="utf-8") as f:
        raw = f.read()

    problems, notes = [], []

    if "\t" in raw:
        problems.append("文件中出现 Tab —— YAML 禁止用 Tab 缩进")

    lines = raw.split("\n")
    # 去掉被注释掉的 docker job 区块：注释行本就不参与解析
    live = [(i + 1, ln) for i, ln in enumerate(lines)
            if ln.strip() and not ln.lstrip().startswith("#")]

    for no, ln in live:
        indent = len(ln) - len(ln.lstrip(" "))
        if indent % 2 != 0:
            problems.append(f"第 {no} 行缩进 {indent} 不是 2 的倍数：{ln.strip()[:60]}")

    # 顶层键（缩进 0 且不是列表项）
    top = [ln.split(":")[0].strip() for _, ln in live
           if not ln.startswith(" ") and ":" in ln]
    expected_top = {"name", "on", "concurrency", "permissions", "jobs"}
    unexpected = [k for k in top if k not in expected_top]
    if unexpected:
        problems.append(f"出现预期之外的顶层键：{unexpected}")
    missing = expected_top - set(top)
    if missing:
        problems.append(f"缺少顶层键：{sorted(missing)}")
    notes.append(f"顶层键 = {top}")

    # 触发器
    if not re.search(r"^on:", raw, re.M):
        problems.append("缺少 on: 触发器定义")
    for trig in ("push", "pull_request"):
        if trig not in raw:
            problems.append(f"触发器缺少 {trig}")

    # jobs
    m = re.search(r"^jobs:\s*$", raw, re.M)
    if not m:
        problems.append("缺少 jobs: 段")
    else:
        jobs_block = raw[m.end():]
        job_names = re.findall(r"^  ([A-Za-z0-9_-]+):\s*$", jobs_block, re.M)
        notes.append(f"job 列表 = {job_names}")
        for need in ("backend", "frontend", "package"):
            if need not in job_names:
                problems.append(f"缺少 job：{need}")
        if 'needs: [ backend, frontend ]' not in raw:
            problems.append("package job 未声明 needs: [ backend, frontend ]")
        # 每个 job 必须带 runs-on 与 steps
        chunks = re.split(r"^  ([A-Za-z0-9_-]+):\s*$", jobs_block, flags=re.M)
        for idx in range(1, len(chunks), 2):
            jn, body = chunks[idx], chunks[idx + 1]
            if "runs-on:" not in body:
                problems.append(f"job {jn} 缺少 runs-on")
            if "steps:" not in body:
                problems.append(f"job {jn} 缺少 steps")

    # 版本要求（任务书硬指标）
    if "distribution: temurin" not in raw:
        problems.append("setup-java 未指定 distribution: temurin")
    if "java-version: '8'" not in raw:
        problems.append("setup-java 未固定 java-version: '8'")
    if "node-version: '22'" not in raw:
        problems.append("setup-node 未固定 node-version: '22'")
    if "cache: maven" not in raw:
        problems.append("未开启 Maven 缓存")

    # 测试阶段不得跳过测试：找 mvn ... test 那一行
    for no, ln in live:
        if re.search(r"mvn\s+-B\s+clean\s+test", ln) and "skipTests" in ln:
            problems.append(f"第 {no} 行的测试命令带了 skipTests：{ln.strip()}")

    # 硬编码密码（password 后面不是 secrets 引用即视为可疑）
    for no, ln in live:
        mm = re.search(r"(password|passwd|secret)\s*:\s*(\S+)", ln, re.I)
        if mm and "secrets." not in mm.group(2) and "${{" not in mm.group(2):
            problems.append(f"第 {no} 行疑似硬编码凭据：{ln.strip()[:70]}")

    print("=" * 66)
    print(f"ci.yml 结构自检：{path}")
    print("=" * 66)
    for n in notes:
        print("  · " + n)
    print("-" * 66)
    if problems:
        print(f"发现问题 {len(problems)} 项：")
        for p in problems:
            print("  [x] " + p)
        return 1
    print("通过：Tab / 缩进 / 顶层键 / job 结构 / 版本固定 / 凭据 / 测试阶段 均无问题")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else ".github/workflows/ci.yml"))
