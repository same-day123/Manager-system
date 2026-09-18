/**
 * build —— 五份交付文档：Markdown → PDF 一键构建 + 页数区间判定
 *
 * 用法:
 *   node build.mjs                 # 构建 docs/大作业/文档/*.md 全部
 *   MD2PDF_ONLY=01,02 node build.mjs
 *
 * 为什么用 Node 而不是 .ps1 编排：
 *   本机是 Windows PowerShell 5.1，它按 ANSI/GBK 读取无 BOM 的 .ps1 文件，
 *   脚本里的中文路径（docs\大作业\文档）会被解码坏、字符串还会断在引号上。
 *   Node 原生 UTF-8，没有这个问题。
 *
 * 每个文档做三件事：
 *   ① 渲染 PDF（调 md2pdf.mjs）
 *   ② 数页数（调 pdfinfo.mjs）
 *   ③ 对照任务书页数区间给 PASS / FAIL
 */

import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const NODE = process.execPath;
const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..', '..', '..');
const SRC_DIR = path.join(ROOT, 'docs', '大作业', '文档');
const OUT_DIR = path.join(SRC_DIR, 'pdf');

/** 任务书硬指标：页数区间 */
const RANGE = { '01': [8, 12], '02': [10, 15], '03': [6, 10], '04': [5, 8], '05': [4, 6] };

/** 同时打到控制台与 UTF-8 报告文件（PowerShell 控制台会按 GBK 解码，直接从报告读更可靠） */
const REPORT = path.join(ROOT, '.workbuddy', 'logs', 'md2pdf-build.txt');
const buf = [];
function say(s = '') {
  console.log(s);
  buf.push(s);
}
function writeReport() {
  fs.mkdirSync(path.dirname(REPORT), { recursive: true });
  fs.writeFileSync(REPORT, buf.join('\n') + '\n', 'utf8');
}

function run(script, args) {
  const r = spawnSync(NODE, [script, ...args], {
    encoding: 'utf8',
    cwd: HERE,
    timeout: 240000,
    maxBuffer: 32 * 1024 * 1024,
  });
  const extra =
    (r.error ? '\nspawn error: ' + r.error.message : '') +
    (r.signal ? '\nsignal=' + r.signal : '') +
    (r.status ? '\nexit=' + r.status : '');
  return { out: (r.stdout || '') + (r.stderr || '') + extra, code: r.status };
}

fs.mkdirSync(OUT_DIR, { recursive: true });

let mds = fs
  .readdirSync(SRC_DIR)
  .filter((f) => f.endsWith('.md'))
  .sort();
if (process.env.MD2PDF_ONLY) {
  const keys = process.env.MD2PDF_ONLY.split(',');
  mds = mds.filter((f) => keys.includes(f.slice(0, 2)));
}

say('md2pdf build @ ' + new Date().toISOString());
say('node   : ' + NODE);
say('src dir: ' + SRC_DIR);
writeReport();

say('\n===== 渲染（一次启动浏览器，批量出 PDF） =====');
const renderPairs = [];
for (const md of mds) {
  renderPairs.push(path.join(SRC_DIR, md), path.join(OUT_DIR, md.replace(/\.md$/, '.pdf')));
}
const rr = renderPairs.length
  ? run(path.join(HERE, 'md2pdf.mjs'), renderPairs)
  : { out: '', code: 0 };
rr.out.split('\n').forEach((l) => l.trim() && say('  ' + l));
writeReport();

// 从渲染输出里按「PDF : 路径 … pages : N」的顺序解析每份的页数
const pagesByPdf = new Map();
{
  let cur = null;
  for (const line of rr.out.split('\n')) {
    const p = line.match(/PDF\s*:\s*(.+?)\s*$/);
    if (p) cur = p[1].trim();
    const g = line.match(/pages\s*:\s*(\d+)/);
    if (g && cur) {
      pagesByPdf.set(path.basename(cur), +g[1]);
      cur = null;
    }
  }
}

const rows = mds.map((md) => {
  const pdf = path.join(OUT_DIR, md.replace(/\.md$/, '.pdf'));
  const name = path.basename(pdf);
  return { md, pdf, pages: pagesByPdf.has(name) ? pagesByPdf.get(name) : null };
});

say('\n===== pdfinfo（页数 / 字体嵌入 / 文字可复制） =====');
const pdfs = fs
  .readdirSync(OUT_DIR)
  .filter((f) => f.endsWith('.pdf'))
  .sort()
  .map((f) => path.join(OUT_DIR, f));
if (pdfs.length) run(path.join(HERE, 'pdfinfo.mjs'), pdfs).out.split('\n').forEach((l) => l && say(l));

say('\n===== 页数区间判定 =====');
let allPass = true;
for (const r of rows) {
  const key = r.md.slice(0, 2);
  const rg = RANGE[key];
  if (r.pages == null) {
    say(`  ${r.md.padEnd(30)} 未取到页数`);
    allPass = false;
    continue;
  }
  const inRange = rg && r.pages >= rg[0] && r.pages <= rg[1];
  if (!inRange) allPass = false;
  say(
    `  ${r.md.padEnd(30)} ${String(r.pages).padStart(3)} 页  ` +
      (inRange ? 'PASS' : `FAIL（需 ${rg ? rg[0] + '-' + rg[1] : '?'} 页）`)
  );
}
say('\n' + (allPass ? 'ALL PASS' : 'SOME FAILED'));
writeReport();
console.log('report -> ' + REPORT);
