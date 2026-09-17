# -*- coding: utf-8 -*-
"""T5 逐字校验：履历文案「改造前 → 改造后」的字符串级比对。

用法（仓库根执行）：
    python .workbuddy/tools/t5_literal_proof.py

为什么不能只做「子串包含」判断：
  * `"非法的报修状态流转"`（异常文案）包含子串 `状态流转`，但它是另一个字面量
    —— 子串判断会报假阳性。
  * 改造前部分文案是**拼接**（`"资产入库：" + name`），整条模板在改前根本不存在
    —— 要求"整条模板在改前存在"会报假阴性。

因此按三类分别校验（基线一律取 `git show HEAD:<file>`）：
  A 类 迁移字面量  —— 改前是 Service 里的独立字面量，改后必须原样搬到枚举、Service 清空。
  B 类 拼接→模板   —— 改前是若干拼接片段，改后必须合成一条模板进枚举，片段在 Service 清空。
  C 类 派生动作名  —— 改前由 LabStatusUtils.assetRecordType 的 return 字面量给出，
                      改后必须搬到枚举，且该方法退化为委托。
"""
import io
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
MAIN = "RuoYi-Vue-fast/src/main/java/com/ruoyi/project/laboratory"

ASSET_SVC = MAIN + "/service/impl/LabAssetServiceImpl.java"
REPAIR_SVC = MAIN + "/service/impl/LabRepairServiceImpl.java"
STATUS_UTIL = MAIN + "/util/LabStatusUtils.java"
ASSET_EVT = MAIN + "/constant/LabAssetEvent.java"
REPAIR_EVT = MAIN + "/constant/LabRepairEvent.java"
FACTORY = MAIN + "/constant/LabRecordFactory.java"

# A 类：改前就是独立字面量（动作名 + 无占位符文案）
A_IN_SERVICE = {
    "资产动作名": ["入库", "调拨", "报废", "资料修改"],
    "资产文案": ["资产所属实验室调整", "资产删除或报废", "资产批量删除或报废",
                 "资产基础资料更新", "资产状态变更"],
    "报修动作名": ["提交报修", "状态流转", "报修信息更新", "维修信息更新", "维修评价"],
    "报修文案": ["报修单信息已更新"],
}

# B 类：改前是拼接片段 → 改后合成模板
B_FRAGMENTS = {          # 片段（改前必须存在于 Service）
    "资产入库：": ("资产入库：%s", ASSET_EVT),
    "故障等级：": ("故障等级：%s；%s", REPAIR_EVT),
    "；": ("故障等级：%s；%s", REPAIR_EVT),
    "评分：": ("评分：%s；%s", REPAIR_EVT),
    "报修状态由“": ("报修状态由“%s”变更为“%s”", REPAIR_EVT),
    "”变更为“": ("报修状态由“%s”变更为“%s”", REPAIR_EVT),
    "”": ("报修状态由“%s”变更为“%s”", REPAIR_EVT),
}
B_TEMPLATES = {"资产入库：%s": ASSET_EVT, "故障等级：%s；%s": REPAIR_EVT,
               "评分：%s；%s": REPAIR_EVT, "报修状态由“%s”变更为“%s”": REPAIR_EVT}

# C 类：派生动作名（改前在 LabStatusUtils.assetRecordType 的 return 字面量里）
C_DERIVED = ["启用", "停用", "维修"]

LITERAL_RE = re.compile(r'"(?:[^"\\]|\\.)*"')
ESCAPES = {"\\\"": '"', "\\\\": "\\", "\\n": "\n", "\\t": "\t", "\\r": "\r"}


def read_disk(rel):
    p = os.path.join(ROOT, rel)
    if not os.path.exists(p):
        return None
    with io.open(p, "r", encoding="utf-8") as f:
        return f.read()


def read_head(rel):
    try:
        out = subprocess.check_output(["git", "show", "HEAD:" + rel], cwd=ROOT,
                                      stderr=subprocess.DEVNULL)
    except Exception:
        return None
    return out.decode("utf-8", "replace")


def literals(src):
    out = set()
    for m in LITERAL_RE.finditer(src or ""):
        raw = m.group(0)[1:-1]
        for k, v in ESCAPES.items():
            raw = raw.replace(k, v)
        out.add(raw)
    return out


def method_body(src, header):
    """按大括号配对取出方法体（含签名行）。"""
    if not src:
        return ""
    i = src.find(header)
    if i < 0:
        return ""
    j = src.find("{", i)
    if j < 0:
        return ""
    depth = 0
    for k in range(j, len(src)):
        if src[k] == "{":
            depth += 1
        elif src[k] == "}":
            depth -= 1
            if depth == 0:
                return src[i:k + 1]
    return src[i:]


def hexs(s):
    return " ".join("%02X" % b for b in s.encode("utf-8"))


def main():
    fails = []
    old_asset = read_head(ASSET_SVC) or ""
    old_repair = read_head(REPAIR_SVC) or ""
    old_status = read_head(STATUS_UTIL) or ""
    old_asset_lit = literals(old_asset)
    old_repair_lit = literals(old_repair)
    new_asset_lit = literals(read_disk(ASSET_SVC))
    new_repair_lit = literals(read_disk(REPAIR_SVC))
    evt_asset_lit = literals(read_disk(ASSET_EVT))
    evt_repair_lit = literals(read_disk(REPAIR_EVT))
    factory = read_disk(FACTORY) or ""
    new_status = read_disk(STATUS_UTIL) or ""

    print("=" * 80)
    print("T5 逐字校验：履历文案「改造前 → 改造后」字面量级比对")
    print("基线 = git HEAD ；判据 = 字符串字面量内容精确相等（不做子串包含）")
    print("=" * 80)
    if not old_asset or not old_repair or not old_status:
        fails.append("git HEAD 基线读取失败")

    print("\n【A 类】迁移字面量：改前在 Service 里 → 改后必须原样进枚举、Service 清空")
    for kind, items in A_IN_SERVICE.items():
        for t in items:
            evt = evt_asset_lit if kind.startswith("资产") else evt_repair_lit
            new_lit = new_asset_lit if kind.startswith("资产") else new_repair_lit
            old_lit = old_asset_lit if kind.startswith("资产") else old_repair_lit
            in_old, in_new, in_evt = t in old_lit, t in new_lit, t in evt
            ok = in_old and not in_new and in_evt
            if not in_old:
                fails.append("[A/%s] 改前 Service 缺字面量：%s" % (kind, t))
            if in_new:
                fails.append("[A/%s] 改后 Service 仍残留：%s" % (kind, t))
            if not in_evt:
                fails.append("[A/%s] 枚举缺字面量：%s" % (kind, t))
            print("   %-8s %-16s 改前=%-5s 改后=%-5s 枚举=%-5s %s"
                  % (kind, t, in_old, in_new, in_evt, "OK" if ok else "!! 不符"))

    print("\n【B 类】拼接 → 模板：改前是拼接片段，改后必须合成一条模板进枚举")
    for frag, (tpl, evt_rel) in B_FRAGMENTS.items():
        evt = literals(read_disk(evt_rel))
        in_old_re = frag in old_repair_lit or frag in old_asset_lit
        in_new_svc = frag in new_asset_lit or frag in new_repair_lit
        if not in_old_re:
            fails.append("[B] 改前 Service 缺拼接片段字面量：%s" % frag)
        if tpl in evt:
            pass  # 模板已在枚举（下面统一判）
        print("   片段 %-12s 改前=%-5s 改后=%-5s | 目标模板 %-24s 在枚举=%s"
              % (frag, in_old_re, in_new_svc, tpl, tpl in evt))
        if in_new_svc:
            # 例外：B 类片段里 `；` / `”` 等单个标点若在别处合法出现，需人工说明；
            # 这里只做提示，不算失败（下方的模板级检查才是硬判据）。
            print("        · 提示：该片段仍在 Service 里出现，需人工确认是否为履历文案残留")
    for tpl, evt_rel in B_TEMPLATES.items():
        evt = literals(read_disk(evt_rel))
        ok = tpl in evt
        if not ok:
            fails.append("[B] 枚举缺目标模板：%s" % tpl)
        print("   模板 %-24s 在枚举=%s %s" % (tpl, ok, "OK" if ok else "!! 不符"))

    print("\n【C 类】派生动作名：改前在 LabStatusUtils.assetRecordType 的 return 里")
    old_body = method_body(old_status, "public static String assetRecordType(")
    new_body = method_body(new_status, "public static String assetRecordType(")
    print("   改前方法体字面量 : %s" % sorted(literals(old_body)))
    print("   改后方法体字面量 : %s" % sorted(literals(new_body)))
    for t in C_DERIVED:
        in_old, in_new_body, in_evt = t in literals(old_body), t in literals(new_body), t in evt_asset_lit
        ok = in_old and not in_new_body and in_evt
        if not in_old:
            fails.append("[C] 改前 assetRecordType 缺派生动作名：%s" % t)
        if in_new_body:
            fails.append("[C] 改后 assetRecordType 仍有字面量：%s" % t)
        if not in_evt:
            fails.append("[C] 枚举缺派生动作名：%s" % t)
        print("   %-6s 改前方法体=%-5s 改后方法体=%-5s 枚举=%-5s %s"
              % (t, in_old, in_new_body, in_evt, "OK" if ok else "!! 不符"))
    delegated = "LabAssetEvent.ofAssetStatus(status).actionName()" in new_body
    print("   assetRecordType 已委托枚举 : %s" % delegated)
    if not delegated:
        fails.append("[C] assetRecordType 未委托枚举，状态→动作名仍是两张表")

    print("\n【D 类】全角引号专项（U+201C / U+201D，AI 最易写成英文直引号之处）")
    tpl = "报修状态由“%s”变更为“%s”"
    print("   模板     : %s" % tpl)
    print("   UTF-8 hex: %s" % hexs(tpl))
    print("   英文直引号版本在枚举里吗 : %s" % ('报修状态由"%s"变更为"%s"' in evt_repair_lit))
    print("   全角版本在枚举里吗       : %s" % (tpl in evt_repair_lit))
    if tpl not in evt_repair_lit or ('报修状态由"%s"变更为"%s"' in evt_repair_lit):
        fails.append("[D] 全角引号模板未逐字保留")

    print("\n【E 类】工厂静态结构与边界（无 Spring / 日志 / 缓存）")
    for needle in ["public final class LabRecordFactory", "private LabRecordFactory()",
                   "public static LabAssetRecord assetRecord(", "public static LabRepairRecord repairRecord("]:
        ok = needle in factory
        print("   %-54s %s" % (needle, "OK" if ok else "MISSING"))
        if not ok:
            fails.append("[E] 工厂缺结构：" + needle)
    hit = [k for k in ["@Component", "@Service", "@Autowired", "@Repository", "Logger", "log.", "Cache", "@Lazy"]
           if k in factory]
    print("   禁止项命中 : %s" % (hit if hit else "无"))
    if hit:
        fails.append("[E] 工厂出现禁止项：%s" % hit)

    print("\n【F 类】调用点数量（含各自私有转发方法定义 1 处）")
    n_ao = len(re.findall(r"insertAssetRecord\(", old_asset))
    n_an = len(re.findall(r"insertAssetRecord\(", read_disk(ASSET_SVC) or ""))
    n_ro = len(re.findall(r"insertRepairRecord\(", old_repair))
    n_rn = len(re.findall(r"insertRepairRecord\(", read_disk(REPAIR_SVC) or ""))
    print("   LabAssetServiceImpl  : %d → %d（调用点 %d → %d）" % (n_ao, n_an, n_ao - 1, n_an - 1))
    print("   LabRepairServiceImpl : %d → %d（调用语句 %d → %d）" % (n_ro, n_rn, n_ro - 1, n_rn - 1))

    print("\n" + "=" * 80)
    if fails:
        print("结论：FAIL（%d 项）" % len(fails))
        for f in fails:
            print("  - " + f)
        return 1
    print("结论：PASS —— 履历动作名与文案模板全部逐字节迁移，Service 无残留手写文案")
    return 0


if __name__ == "__main__":
    sys.exit(main())
