"""段落级解析 pptx 的某一页：把每个 <a:p> 段落内的 <a:t> 拼起来，看断行是否落在预期位置。

为什么不能对原始 XML 直接搜字符串：pptx 会把一行渲染文本切成多个 <a:t> run，
字符串会被 XML 标签打断 → `"产品经理" in xml` 会假阴性。
必须先按 <a:p> 切段、再把段内 <a:t> 拼起来。

用法：python .workbuddy/tools/d2_pptx_paras.py [slide序号，默认 14]
"""
import re
import sys
import zipfile

PPTX = r"D:\code\Manager_system\docs\大作业\答辩PPT\答辩PPT-v1.pptx"
WANT = "ppt/slides/slide%s.xml" % (sys.argv[1] if len(sys.argv) > 1 else "14")


def paragraphs(xml):
    out = []
    for p in re.findall(r"<a:p>(.*?)</a:p>", xml, re.S):
        text = "".join(re.findall(r"<a:t>(.*?)</a:t>", p, re.S))
        out.append(text)
    return out


with zipfile.ZipFile(PPTX) as z:
    xml = z.read(WANT).decode("utf-8", "ignore")
    print("=== %s 段落级内容（空段也保留，便于看结构）===" % WANT.split("/")[-1])
    for i, text in enumerate(paragraphs(xml), 1):
        mark = "空段" if not text.strip() else text
        print("%2d | %s" % (i, mark))
