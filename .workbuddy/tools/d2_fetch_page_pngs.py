"""下载 D2 答辩 PPT 全部 14 页导出图，按页码重命名到 导出预览/。

用法：python .workbuddy/tools/d2_fetch_page_pngs.py
"""
import os
import urllib.request

DST = r"D:\code\Manager_system\docs\大作业\答辩PPT\导出预览"

BASE = "https://ai.d.gtimg.com/mcp/exports/726840787753433/"

# (页码, 节点 id, 落地文件名, 导出 url 尾部)
PAGES = [
    (1, "2:2", "Slide01-封面", "2_2-20260918_181331937.png?sign=15c315385830a1d51b7169f927a9f403&t=1792318412"),
    (2, "4:1", "Slide02-背景与三个真实痛点", "4_1-20260918_181332015.png?sign=598769afc27ed559552a44164f8eaf6a&t=1792318412"),
    (3, "2:3", "Slide03-系统全景（四层架构）", "2_3-20260918_181332076.png?sign=db9c81b20d9e4a386d33d035eea3ae35&t=1792318412"),
    (4, "2:4", "Slide04-核心契约（报修状态机）", "2_4-20260918_181332142.png?sign=b7943fa89d25f59fb411b538f5140dd7&t=1792318412"),
    (5, "4:23", "Slide05-设计取舍", "4_23-20260918_181332216.png?sign=8187076142cd1f37a03b221b93ca0261&t=1792318412"),
    (6, "4:45", "Slide06-AI协作方式", "4_45-20260918_181333744.png?sign=34865a6ff73f2ffc1c24c2cd2d6a6f3a&t=1792318413"),
    (7, "5:1", "Slide07-质量保障（测试金字塔）", "5_1-20260918_181333804.png?sign=1f14f2b22ed5187b6b393880696bc40a&t=1792318413"),
    (8, "5:29", "Slide08-交付链路（流水线）", "5_29-20260918_181333863.png?sign=07fcb97431e43b68a618078fc80b2511&t=1792318413"),
    (9, "5:70", "Slide09-演示导引", "5_70-20260918_181333915.png?sign=84e01fc2c9b9b9cba0abbdec3323fb74&t=1792318413"),
    (10, "4:73", "Slide10-权限口径", "4_73-20260918_181333969.png?sign=f26393b534d87e8641394eff062c097e&t=1792318414"),
    (11, "5:94", "Slide11-数据模型", "5_94-20260918_181332654.png?sign=5fcf8508fd3c77fa2bffb7ce342720d1&t=1792318412"),
    (12, "4:118", "Slide12-五条容忍偏差", "4_118-20260918_181332713.png?sign=7e391edba3d634b8b0fef0a30fdde13c&t=1792318412"),
    (13, "5:132", "Slide13-规模指标", "5_132-20260918_181332771.png?sign=2bd67f6cba1978d8d80fc8f01d9581f5&t=1792318412"),
    (14, "4:146", "Slide14-分工与AI使用声明", "4_146-20260918_181332839.png?sign=8a2fed3002f86bccfcb899c40fdeed1b&t=1792318412"),
]


def main():
    os.makedirs(DST, exist_ok=True)
    ok, bad = 0, []
    for num, node, name, tail in PAGES:
        out = os.path.join(DST, name + ".png")
        try:
            req = urllib.request.Request(BASE + tail, headers={"User-Agent": "Mozilla/5.0"})
            with urllib.request.urlopen(req, timeout=120) as resp, open(out, "wb") as fh:
                fh.write(resp.read())
            size = os.path.getsize(out) / 1024.0
            print("%2d  %-34s %8.1f KB   (%s)" % (num, name, size, node))
            ok += 1
        except Exception as exc:  # noqa: BLE001
            print("%2d  %-34s FAIL  %s" % (num, name, exc))
            bad.append(name)
    print("\n落地 %d / %d" % (ok, len(PAGES)))
    if bad:
        print("失败：" + "、".join(bad))


if __name__ == "__main__":
    main()
