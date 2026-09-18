/**
 * pdfinfo —— 不装任何依赖，直接从 PDF 字节里读出「验收要看的三件事」：
 *   ① 页数（数 /Type /Page，排除 /Type /Pages；兜底取 /Count 最大值）
 *   ② 字体是否**嵌入**（/FontFile2 = 嵌入的 TrueType、/FontFile3 = 嵌入的 CFF/Type1、/FontFile = Type1）
 *   ③ 文字是否**可提取/可复制**（/ToUnicode 的数量；没有它 → 复制出来是乱码，查重也读不到）
 *
 * 用法: node pdfinfo.mjs <file.pdf> [more.pdf ...]
 */

import fs from 'node:fs';

function info(file) {
  const buf = fs.readFileSync(file);
  const s = buf.toString('latin1');

  const direct = (s.match(/\/Type\s*\/Page(?![s])/g) || []).length;
  let maxCount = 0;
  for (const m of s.matchAll(/\/Count\s+(\d+)/g)) {
    maxCount = Math.max(maxCount, parseInt(m[1], 10));
  }
  const pages = direct || maxCount;

  const baseFonts = [...new Set([...s.matchAll(/\/BaseFont\s*\/([#\w+\-.,]+)/g)].map((m) => m[1]))];
  const fontFile2 = (s.match(/\/FontFile2/g) || []).length;
  const fontFile3 = (s.match(/\/FontFile3/g) || []).length;
  const fontFile1 = (s.match(/\/FontFile(?![23])/g) || []).length;
  const toUnicode = (s.match(/\/ToUnicode/g) || []).length;
  const fonts = (s.match(/\/Type\s*\/Font/g) || []).length;

  return {
    file,
    sizeKB: +(buf.length / 1024).toFixed(1),
    pages,
    pageCounters: `direct=${direct} count=${maxCount}`,
    fontObjects: fonts,
    embedded: { FontFile: fontFile1, FontFile2: fontFile2, FontFile3: fontFile3 },
    toUnicode,
    baseFonts,
  };
}

for (const f of process.argv.slice(2)) {
  if (!fs.existsSync(f)) {
    console.log(`!! 不存在: ${f}`);
    continue;
  }
  const r = info(f);
  const emb = r.embedded.FontFile + r.embedded.FontFile2 + r.embedded.FontFile3;
  console.log('='.repeat(72));
  console.log(`file      : ${r.file}`);
  console.log(`size      : ${r.sizeKB} KB`);
  console.log(`pages     : ${r.pages}   (${r.pageCounters})`);
  console.log(`fonts     : ${r.fontObjects} 个字体对象，嵌入字体程序 ${emb} 个` +
    ` (FontFile=${r.embedded.FontFile} FontFile2=${r.embedded.FontFile2} FontFile3=${r.embedded.FontFile3})`);
  console.log(`ToUnicode : ${r.toUnicode} 个  → 文字${r.toUnicode > 0 ? '可提取/可复制 ✅' : '不可复制 ❌'}`);
  console.log(`BaseFont  : ${r.baseFonts.join(' | ') || '(无)'}`);
  console.log(`verdict   : ${emb > 0 && r.toUnicode > 0 ? '字体已嵌入 + 文字可复制 ✅' : '需复查 ⚠️'}`);
}
