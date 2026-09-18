"""检查 pptx 导出件里文字是否以可编辑文本落地（还是被转成了矢量）。

用法：python .workbuddy/tools/d2_pptx_inspect.py
"""
import re
import zipfile

PPTX = r"D:\code\Manager_system\docs\大作业\答辩PPT\答辩PPT-v1.pptx"

with zipfile.ZipFile(PPTX) as z:
    names = sorted(n for n in z.namelist() if re.match(r"^ppt/slides/slide\d+\.xml$", n))
    print("slide xml =", len(names), names)
    print()

    for n in names:
        x = z.read(n).decode("utf-8", "ignore")
        ts = re.findall(r"<a:t>(.*?)</a:t>", x, re.S)
        br = x.count("<a:br/>")
        paras = x.count("<a:p>")
        sample = " / ".join(t.strip()[:24] for t in ts[:6] if t.strip())
        print("%-26s runs=%-4d br=%-3d p=%-4d | %s" % (n.split("/")[-1], len(ts), br, paras, sample))

    print()
    print("=== slide14.xml 片段（前 1200 字符，看结构）===")
    target = None
    for n in names:
        x = z.read(n).decode("utf-8", "ignore")
        if "使用声明" in x or "产品经理" in x or "六个角色" in x:
            target = (n, x)
    if target:
        n, x = target
        print("命中文件:", n)
        print(x[:1200])
    else:
        print("!! 所有 slide 里都没找到『使用声明 / 产品经理 / 六个角色』")
