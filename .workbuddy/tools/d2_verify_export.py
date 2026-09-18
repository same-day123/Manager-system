"""D2 导出件校验：下载 pptx/pdf，验页数、验关键文字是否完整落地。

用法：python .workbuddy/tools/d2_verify_export.py
输出：docx/pptx 的幻灯片数 + PDF 页数 + Slide 14 多行文本是否保住换行。
"""
import os
import re
import urllib.request
import zipfile

DST = r"D:\code\Manager_system\docs\大作业\答辩PPT"

PPTX_URL = ("https://ai.d.gtimg.com/mcp/exports/726840787753433/"
            "%E7%AD%94%E8%BE%A9PPT-%E8%8D%89%E6%A1%88-%E9%AB%98%E6%A0%A1%E5%AE%9E%E9%AA%8C%E5%AE%A4"
            "%E8%B5%84%E4%BA%A7%E4%B8%8E%E6%8A%A5%E4%BF%AE%E7%AE%A1%E7%90%86%E5%B9%B3%E5%8F%B0"
            "-20260918_181245804.pptx?sign=9195cf95fe8256dae6e07d174a9e303f&t=1792318365")

PDF_URL = ("https://ai.d.gtimg.com/mcp/exports/726840787753433/"
           "%E7%AD%94%E8%BE%A9PPT-%E8%8D%89%E6%A1%88-%E9%AB%98%E6%A0%A1%E5%AE%9E%E9%AA%8C%E5%AE%A4"
           "%E8%B5%84%E4%BA%A7%E4%B8%8E%E6%8A%A5%E4%BF%AE%E7%AE%A1%E7%90%86%E5%B9%B3%E5%8F%B0"
           "-20260918_181255128.pdf?sign=52d298af42e4ad96368652ad593e1758&t=1792318375")


def fetch(url, name):
    path = os.path.join(DST, name)
    os.makedirs(DST, exist_ok=True)
    req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=180) as resp, open(path, "wb") as fh:
        fh.write(resp.read())
    print("DOWNLOAD OK  %-28s %8.1f KB" % (name, os.path.getsize(path) / 1024.0))
    return path


def check_pptx(path):
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        slides = sorted(n for n in names if re.match(r"^ppt/slides/slide\d+\.xml$", n))
        print("PPTX slides (ppt/slides/slideN.xml) = %d" % len(slides))
        # 抽 Slide 14（分工与 AI 声明）的文字，验证 \n 换行是否保住
        target = None
        for n in slides:
            xml = z.read(n).decode("utf-8", "ignore")
            if "六个角色各领一块" in xml:
                target = (n, xml)
        if not target:
            print("!! 未找到含『六个角色各领一块』的幻灯片")
            return
        n, xml = target
        texts = re.findall(r"<a:t>(.*?)</a:t>", xml, re.S)
        joined = "\n".join(texts)
        print("\n--- %s 的文字（前 900 字符）---" % n)
        print(joined[:900])
        roles = ["产品经理", "代码负责", "测试负责", "文档负责", "UI 设计", "PPT 和设计"]
        hit = [r for r in roles if r in joined]
        print("\n六个 AI 角色命中 %d / 6 : %s" % (len(hit), "、".join(hit)))


def check_pdf(path):
    with open(path, "rb") as fh:
        data = fh.read()
    counts = [int(m) for m in re.findall(rb"/Count\s+(\d+)", data)]
    pages = len(re.findall(rb"/Type\s*/Page[^s]", data))
    print("PDF  /Count 候选 = %s , /Type /Page 计数 = %d" % (counts, pages))


def main():
    pptx = fetch(PPTX_URL, "答辩PPT-v1.pptx")
    check_pptx(pptx)
    pdf = fetch(PDF_URL, "答辩PPT-v1.pdf")
    check_pdf(pdf)


if __name__ == "__main__":
    main()
