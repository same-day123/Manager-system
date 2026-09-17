#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""Extract plain text (paragraphs + tables) from .docx using stdlib only."""
import sys, zipfile, re, os

NS = 'http://schemas.openxmlformats.org/wordprocessingml/2006/main'
W = '{%s}' % NS


def para_text(p):
    parts = []
    for node in p.iter():
        tag = node.tag
        if tag == W + 't':
            parts.append(node.text or '')
        elif tag == W + 'tab':
            parts.append('\t')
        elif tag == W + 'br':
            parts.append('\n')
    return ''.join(parts)


def style_of(p):
    ppr = p.find(W + 'pPr')
    if ppr is None:
        return ''
    st = ppr.find(W + 'pStyle')
    if st is None:
        return ''
    return st.get(W + 'val') or ''


def walk(body, out):
    for child in body:
        tag = child.tag
        if tag == W + 'p':
            t = para_text(child).strip()
            if t:
                s = style_of(child)
                prefix = ''
                if s.lower().startswith('heading') or re.match(r'^\d+$', s):
                    prefix = ''
                out.append(t)
        elif tag == W + 'tbl':
            for row in child.findall(W + 'tr'):
                cells = []
                for tc in row.findall(W + 'tc'):
                    ct = ' '.join(para_text(p).strip() for p in tc.findall(W + 'p'))
                    cells.append(ct.strip())
                out.append('| ' + ' | '.join(cells) + ' |')
            out.append('')


def extract(path, out):
    out.append('=' * 90)
    out.append('FILE: ' + os.path.basename(path))
    out.append('=' * 90)
    with zipfile.ZipFile(path) as z:
        names = z.namelist()
        with z.open('word/document.xml') as f:
            xml = f.read().decode('utf-8')
    # simple split of body children to keep order
    root = __import__('xml.etree.ElementTree', fromlist=['x']).fromstring(xml)
    body = root.find(W + 'body')
    walk(body, out)
    out.append('')


if __name__ == '__main__':
    folder = r'C:\Users\王旻辉\Desktop\智能软件工程大作业'
    files = [f for f in sorted(os.listdir(folder)) if f.lower().endswith('.docx') and not f.startswith('~$')]
    out = []
    for f in files:
        extract(os.path.join(folder, f), out)
    dest = sys.argv[1] if len(sys.argv) > 1 else r'D:\code\Manager_system\.workbuddy\logs\hw_extract.txt'
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    with open(dest, 'w', encoding='utf-8') as fh:
        fh.write('\n'.join(out))
    print('written: ' + dest + ' lines=' + str(len(out)))
