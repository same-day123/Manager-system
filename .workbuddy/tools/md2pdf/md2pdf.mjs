/**
 * md2pdf —— 零依赖的「Markdown → HTML → PDF」流水线（本机离线可用）
 *
 * 为什么自写：
 *   本机没有 pandoc / wkhtmltopdf / LaTeX；npm 离线装不上任何 markdown 库。
 *   但本机有 Chrome，且 Node 22 自带 WebSocket + fetch，可直接对话 CDP 的 Page.printToPDF。
 *   → 自写一个够用的 GFM 子集渲染器 + Chrome 无头打印，即可得到「文字可复制、字体已嵌入」的 PDF。
 *
 * 用法：
 *   node md2pdf.mjs <input.md> <output.pdf> [--keep-html] [--chrome=<path>]
 *
 * 产出：
 *   - output.pdf（A4，页脚含「第 X 页 / 共 Y 页」）
 *   - output.html（同名，便于人工核对排版；--keep-html 可保留，默认也保留）
 *   - 控制台打印页数
 *
 * PDF 页数怎么数：读 PDF 字节，
 *   ① 首选统计 `/Type /Page`（排除 `/Type /Pages`）；
 *   ② 失败则取页树里 `/Count N` 的最大值。
 */

import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawn, spawnSync } from 'node:child_process';

// ---------------------------------------------------------------------------
// 0. 参数
// ---------------------------------------------------------------------------
const args = process.argv.slice(2);
/** 位置参数按「输入 md、输出 pdf」成对收集；`--xxx=yyy` 形式的开关不计入 */
const positional = args.filter((a) => !a.startsWith('--'));
if (positional.length < 2 || positional.length % 2 !== 0) {
  console.error('用法: node md2pdf.mjs <in.md> <out.pdf> [<in.md> <out.pdf> ...] [--chrome=<path>]');
  process.exit(2);
}
const pairs = [];
for (let i = 0; i < positional.length; i += 2) pairs.push([positional[i], positional[i + 1]]);
const argVal = (k) => {
  const a = args.find((x) => x.startsWith(`--${k}=`));
  return a ? a.slice(k.length + 3) : null;
};

const CHROME_CANDIDATES = [
  argVal('chrome'),
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
].filter(Boolean);
const CHROME = CHROME_CANDIDATES.find((p) => fs.existsSync(p));
if (!CHROME) {
  console.error('找不到 Chrome / Edge，可传 --chrome=<exe 路径>');
  process.exit(3);
}

// ---------------------------------------------------------------------------
// 1. 极简 GFM 渲染器（够本项目 5 份文档用）
// ---------------------------------------------------------------------------
const esc = (s) =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

/** [待填] → 可见的填空线（不编数据，保留空位语义） */
function blanks(s) {
  return s.replace(/\[待填\]/g, '<span class="blank">____</span>');
}

/** 行内：图片 → code → strong → em。先切出 code，避免 code 里的 * 被当强调。 */
function inline(text) {
  let out = esc(text);
  // 图片：![alt](path) —— path 稍后会被内联成 data URI，保证 file:// 下也能出图
  out = out.replace(/!\[([^\]]*)\]\(([^)\s]+)\)/g, '<img src="$2" alt="$1"/>');
  const codes = [];
  out = out.replace(/`([^`]+)`/g, (_, c) => {
    codes.push(c);
    return `\u0000${codes.length - 1}\u0000`;
  });
  out = out
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|[^*])\*([^*]+)\*/g, '$1<em>$2</em>');
  out = out.replace(/\u0000(\d+)\u0000/g, (_, i) => `<code>${codes[i]}</code>`);
  return blanks(out);
}

/** 把 html 里的相对图片路径读成 data URI —— 否则 PDF 里会出现裂图 */
function inlineAssets(html, baseDir) {
  const missing = [];
  const out = html.replace(/(<img[^>]*?src=")([^"]+)(")/g, (m, p1, src, p3) => {
    if (/^(https?:|data:)/i.test(src)) return m;
    const abs = path.resolve(baseDir, decodeURIComponent(src));
    if (!fs.existsSync(abs)) {
      missing.push(abs);
      return `${p1}${src}${p3} data-missing="1"`;
    }
    const ext = abs.toLowerCase().slice(abs.lastIndexOf('.'));
    const mime =
      ext === '.svg' ? 'image/svg+xml'
      : ext === '.png' ? 'image/png'
      : ext === '.jpg' || ext === '.jpeg' ? 'image/jpeg'
      : ext === '.gif' ? 'image/gif'
      : ext === '.webp' ? 'image/webp'
      : 'application/octet-stream';
    return p1 + `data:${mime};base64,` + fs.readFileSync(abs).toString('base64') + p3;
  });
  if (missing.length) {
    console.warn('  ! 找不到图片（PDF 中会是裂图）:');
    [...new Set(missing)].forEach((p) => console.warn('    ' + p));
  }
  return out;
}

const splitRow = (line) =>
  line
    .trim()
    .replace(/^\|/, '')
    .replace(/\|$/, '')
    .split('|')
    .map((c) => c.trim());

const isSepRow = (line) => /^\s*\|?[\s:|-]+\|[\s:|-]*$/.test(line) && line.includes('-');

const alignOf = (sep) =>
  splitRow(sep).map((c) => {
    const l = c.startsWith(':');
    const r = c.endsWith(':');
    return l && r ? 'center' : r ? 'right' : l ? 'left' : '';
  });

function renderTable(rows, sep) {
  const aligns = sep ? alignOf(sep) : [];
  const head = splitRow(rows[0]);
  const body = rows.slice(1);
  const td = (cells, tag) =>
    cells
      .map((c, i) => {
        const a = aligns[i] ? ` class="ta-${aligns[i]}"` : '';
        return `<${tag}${a}>${inline(c)}</${tag}>`;
      })
      .join('');
  return (
    '<table><thead><tr>' +
    td(head, 'th') +
    '</tr></thead><tbody>' +
    body
      .map((r) => `<tr>${td(splitRow(r), 'td')}</tr>`)
      .join('') +
    '</tbody></table>'
  );
}

/** 把 md 转成 html 片段 */
function mdToHtml(md) {
  const lines = md.replace(/\r\n/g, '\n').split('\n');
  const out = [];
  let i = 0;
  let inCode = false;
  let codeBuf = [];
  let para = [];
  let listBuf = [];
  let quoteBuf = [];

  const flushPara = () => {
    if (para.length) {
      const joined = para.join(' ');
      // 独占一段的图片 → 图块（居中 + 图注）
      const only = joined.match(/^!\[([^\]]*)\]\(([^)\s]+)\)$/);
      if (only) {
        out.push(
          `<figure class="fig">${inline(joined)}` +
            (only[1] ? `<figcaption>${inline(only[1])}</figcaption>` : '') +
            `</figure>`
        );
      } else {
        out.push(`<p>${inline(joined)}</p>`);
      }
      para = [];
    }
  };
  const flushList = () => {
    if (listBuf.length) {
      out.push(`<ul>${listBuf.map((t) => `<li>${inline(t)}</li>`).join('')}</ul>`);
      listBuf = [];
    }
  };
  const flushQuote = () => {
    if (quoteBuf.length) {
      out.push(`<blockquote>${quoteBuf.map((t) => (t ? `<p>${inline(t)}</p>` : '')).join('')}</blockquote>`);
      quoteBuf = [];
    }
  };
  const flushAll = () => {
    flushPara();
    flushList();
    flushQuote();
  };

  while (i < lines.length) {
    const line = lines[i];

    // 代码块
    if (/^\s*```/.test(line)) {
      if (!inCode) {
        flushAll();
        inCode = true;
        codeBuf = [];
      } else {
        out.push(`<pre><code>${esc(codeBuf.join('\n'))}</code></pre>`);
        inCode = false;
      }
      i++;
      continue;
    }
    if (inCode) {
      codeBuf.push(line);
      i++;
      continue;
    }

    // 表格：本行以 | 开头，且下一行是分隔行
    if (/^\s*\|/.test(line) && i + 1 < lines.length && isSepRow(lines[i + 1])) {
      flushAll();
      const sep = lines[i + 1];
      const rows = [line];
      i += 2;
      while (i < lines.length && /^\s*\|/.test(lines[i])) {
        rows.push(lines[i]);
        i++;
      }
      out.push(renderTable(rows, sep));
      continue;
    }

    // 引用
    if (/^\s*>\s?/.test(line)) {
      flushPara();
      flushList();
      quoteBuf.push(line.replace(/^\s*>\s?/, ''));
      i++;
      continue;
    }
    if (quoteBuf.length) flushQuote();

    // 列表
    if (/^\s*[-*]\s+/.test(line)) {
      flushPara();
      flushQuote();
      listBuf.push(line.replace(/^\s*[-*]\s+/, ''));
      i++;
      continue;
    }
    if (listBuf.length) flushList();

    // 标题
    const h = line.match(/^(#{1,6})\s+(.*)$/);
    if (h) {
      flushAll();
      const lv = h[1].length;
      out.push(`<h${lv}>${inline(h[2])}</h${lv}>`);
      i++;
      continue;
    }

    // 分隔线
    if (/^\s*---+\s*$/.test(line)) {
      flushAll();
      out.push('<hr/>');
      i++;
      continue;
    }

    // 空行
    if (!line.trim()) {
      flushAll();
      i++;
      continue;
    }

    para.push(line.trim());
    i++;
  }
  flushAll();
  return out.join('\n');
}

/** 拆出「封面部分（H1 + 第一张表）」与「正文部分」 */
function splitCover(md) {
  const lines = md.replace(/\r\n/g, '\n').split('\n');
  let idx = 0;
  // 跳过开头空行，第一行是标题
  while (idx < lines.length && !lines[idx].trim()) idx++;
  const titleLine = lines[idx] || '';
  idx++;
  // 标题之后找**第一张表**：中间可能夹着引用/空行（如"本文件是源稿…"），那些留给正文
  let t = idx;
  while (t < lines.length && !/^\s*\|/.test(lines[t])) t++;
  let end = t;
  while (end < lines.length && /^\s*\|/.test(lines[end])) end++;
  const coverTable = t < lines.length ? lines.slice(t, end).join('\n') : '';
  // 正文 = 标题之后的内容，并**剔除**已上封面的那张表
  const bodyLines = lines.slice(idx, t).concat(lines.slice(end));
  return { titleLine, coverTable, body: bodyLines.join('\n') };
}

/** 封面用「字段名 : 取值」两列表（丢掉原表的表头行「项目 | 内容」） */
function renderCoverTable(tableMd) {
  const rows = tableMd
    .split('\n')
    .filter((r) => !isSepRow(r))
    .slice(1); // 去掉表头
  if (!rows.length) return '';
  return (
    '<table>' +
    rows
      .map((r) => {
        const c = splitRow(r);
        return `<tr><th>${inline(c[0] || '')}</th><td>${inline(c.slice(1).join(' | '))}</td></tr>`;
      })
      .join('') +
    '</table>'
  );
}

// ---------------------------------------------------------------------------
// 2. HTML 模板（打印用 CSS：A4、中文字体、页内不裂行）
// ---------------------------------------------------------------------------
function buildHtml(md, baseDir) {
  const { titleLine, coverTable, body } = splitCover(md);
  const title = titleLine.replace(/^#\s*/, '').trim();

  // 封面信息表：字段值两列，居中排布
  const coverRows = coverTable ? renderCoverTable(coverTable) : '';

  const bodyHtml = mdToHtml(body);

  return inlineAssets(`<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8"/>
<title>${esc(title)}</title>
<style>
  /* ---------- 字体与基础 ---------- */
  :root{
    --ink:#1a1a1a; --ink2:#444; --line:#b9b9b9; --hline:#5b3f2f;
    --soft:#8a8a8a; --bg-soft:#f5f4f2;
  }
  *{ box-sizing:border-box; }
  html,body{ margin:0; padding:0; }
  body{
    font-family:"SimSun","宋体","Songti SC",serif;
    font-size:10pt; line-height:1.5; color:var(--ink);
    text-align:justify; text-justify:inter-ideograph;
    -webkit-print-color-adjust:exact; print-color-adjust:exact;
  }
  h1,h2,h3,h4,h5,h6{
    font-family:"SimHei","黑体","Microsoft YaHei","微软雅黑",sans-serif;
    color:#111; line-height:1.3; margin:0 0 .42em;
    page-break-after:avoid; break-after:avoid;
  }
  h1{ font-size:17pt; text-align:center; margin:0 0 .9em; }
  h2{ font-size:13pt; border-left:5px solid var(--hline); padding-left:.5em;
      margin-top:1.15em; }
  h3{ font-size:11pt; margin-top:.95em; }
  h4{ font-size:10pt; margin-top:.8em; color:#222; }
  p{ margin:0 0 .5em; }
  strong{ font-family:"SimHei","黑体","Microsoft YaHei",sans-serif; font-weight:normal; }
  code{
    font-family:"Consolas","Courier New",monospace; font-size:9pt;
    background:var(--bg-soft); border:1px solid #e2e0dd; border-radius:2px;
    padding:0 2px;
  }
  pre{
    background:#faf9f7; border:1px solid #e0dedb; border-left:3px solid var(--hline);
    padding:.6em .8em; margin:.6em 0; page-break-inside:avoid; break-inside:avoid;
    white-space:pre-wrap; word-break:break-all;
  }
  pre code{ background:none; border:0; padding:0; font-size:8.1pt; line-height:1.38; }
  blockquote{
    margin:.5em 0; padding:.45em .75em; background:#fbfaf8;
    border-left:3px solid #c9c3bb; color:var(--ink2); font-size:9.5pt;
  }
  blockquote p:last-child{ margin-bottom:0; }
  hr{ border:0; border-top:1px solid #ddd; margin:.9em 0; }
  ul{ margin:.35em 0 .6em; padding-left:1.5em; }
  li{ margin:.12em 0; }
  .blank{ color:#999; letter-spacing:1px; }

  /* ---------- 表格 ---------- */
  table{
    width:100%; border-collapse:collapse; margin:.5em 0 .75em;
    font-size:8.4pt; page-break-inside:auto;
  }
  thead{ display:table-header-group; }
  tr{ page-break-inside:avoid; break-inside:avoid; }
  th,td{
    border:1px solid var(--line); padding:2.5px 5px;
    vertical-align:top; text-align:left; word-break:break-word;
    line-height:1.42;
  }
  th{
    font-family:"SimHei","黑体","Microsoft YaHei",sans-serif; font-weight:normal;
    background:#eceae6; text-align:center;
  }
  td.ta-center,th.ta-center{ text-align:center; }
  td.ta-right,th.ta-right{ text-align:right; }

  /* ---------- 图 ---------- */
  figure.fig{
    margin:.8em 0 1em; text-align:center;
    page-break-inside:avoid; break-inside:avoid;
  }
  figure.fig img{ max-width:100%; max-height:185mm; }
  figure.fig figcaption{
    font-family:"SimHei","黑体","Microsoft YaHei",sans-serif;
    font-size:9pt; color:#555; margin-top:.45em;
  }

  /* ---------- 封面 ---------- */
  .cover{ height:247mm; position:relative; page-break-after:always; break-after:page; }
  .cover .band{ height:6px; background:var(--hline); margin:0 0 26mm; }
  .cover .org{ font-family:"SimHei","黑体",sans-serif; font-size:12pt;
               text-align:center; letter-spacing:.35em; color:#333; margin-bottom:6mm;}
  .cover h1{ font-size:26pt; letter-spacing:.06em; margin:0 0 4mm; }
  .cover .sub{ text-align:center; font-size:12pt; color:#555; letter-spacing:.2em;
               margin-bottom:16mm; font-family:"SimHei","黑体",sans-serif; }
  .cover .meta{ width:78%; margin:0 auto; }
  .cover .meta table{ font-size:10.5pt; }
  .cover .meta th{ background:none; border:0; font-family:"SimHei","黑体",sans-serif;
                   width:34%; text-align:left; padding:5px 8px 5px 0; color:#333;}
  .cover .meta td{ border:0; border-bottom:1px solid #cfcdc9; padding:5px 4px; }
  .cover .foot{ position:absolute; bottom:6mm; width:100%; text-align:center;
                font-size:9.5pt; color:#777; letter-spacing:.1em;}
  .body{ page-break-before:auto; }
</style></head>
<body>
  <section class="cover">
    <div class="band"></div>
    <div class="org">软件新技术专题 · 课程大作业</div>
    <h1>${esc(title)}</h1>
    <div class="sub">高校实验室资产与报修管理平台</div>
    <div class="meta">${coverRows}</div>
    <div class="foot">第 6 组　·　软件工程 2023-2</div>
  </section>
  <section class="body">
${bodyHtml}
  </section>
</body></html>`, baseDir);
}

// ---------------------------------------------------------------------------
// 3. CDP 驱动：打印 PDF
// ---------------------------------------------------------------------------
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** 收拾 Chrome：headless 会拉出一棵进程树，只 kill 父进程会留下孤儿进程 */
function killTree(child) {
  try {
    if (process.platform === 'win32' && child.pid) {
      spawnSync('taskkill', ['/PID', String(child.pid), '/T', '/F'], { stdio: 'ignore' });
    } else if (child.pid) {
      child.kill('SIGKILL');
    }
  } catch {
    /* 忽略 */
  }
}

class CDP {
  constructor(ws) {
    this.ws = ws;
    this.id = 0;
    this.pending = new Map();
    ws.onmessage = (ev) => {
      const m = JSON.parse(ev.data);
      if (m.id && this.pending.has(m.id)) {
        const { res, rej, timer } = this.pending.get(m.id);
        clearTimeout(timer); // 必须清掉，否则定时器会吊住事件循环、进程 120s 不退出
        this.pending.delete(m.id);
        if (m.error) rej(new Error(`${m.method} ${JSON.stringify(m.error)}`));
        else res(m.result);
      }
    };
  }
  send(method, params = {}, sessionId) {
    const id = ++this.id;
    return new Promise((res, rej) => {
      const timer = setTimeout(() => {
        if (this.pending.has(id)) {
          this.pending.delete(id);
          rej(new Error(`CDP timeout: ${method}`));
        }
      }, 60000);
      timer.unref?.();
      this.pending.set(id, { res, rej, timer });
      this.ws.send(JSON.stringify({ id, method, params, ...(sessionId ? { sessionId } : {}) }));
    });
  }
}

async function launchChromeOnce() {
  const port = 9333 + Math.floor(Math.random() * 200);
  const userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'md2pdf-'));
  const child = spawn(
    CHROME,
    [
      '--headless=new',
      `--remote-debugging-port=${port}`,
      '--remote-allow-origins=*',
      `--user-data-dir=${userDataDir}`,
      '--no-first-run',
      '--no-default-browser-check',
      '--disable-extensions',
      '--disable-gpu',
      '--hide-scrollbars',
      '--allow-file-access-from-files',
      'about:blank',
    ],
    { stdio: 'ignore' }
  );
  const t0 = Date.now();
  let ver = null;
  while (Date.now() - t0 < 40000) {
    try {
      const res = await fetch(`http://127.0.0.1:${port}/json/version`, {
        signal: AbortSignal.timeout(1500), // 没有这个，单个 fetch 可能永久挂住、循环无法超时
      });
      ver = await res.json();
      break;
    } catch {
      await sleep(300);
    }
  }
  if (!ver) {
    killTree(child);
    throw new Error('Chrome 未在 40s 内就绪（port=' + port + '）');
  }
  console.log(`chrome ready on port ${port} in ${Date.now() - t0}ms`);
  return { child, port, userDataDir, ver };
}

/**
 * headless Chrome 偶发启动失败（连着起两个实例时尤其明显），
 * 失败就换个端口与临时目录重试，别让整批渲染倒在第一步。
 */
async function launchChrome(attempts = 4) {
  let lastErr;
  for (let i = 1; i <= attempts; i++) {
    try {
      return await launchChromeOnce();
    } catch (e) {
      lastErr = e;
      console.log(`chrome 启动第 ${i} 次失败：${e.message}`);
      await sleep(1500);
    }
  }
  throw lastErr;
}

function countPdfPages(buf) {
  const s = buf.toString('latin1');
  const direct = (s.match(/\/Type\s*\/Page(?![s])/g) || []).length;
  let maxCount = 0;
  const re = /\/Count\s+(\d+)/g;
  let m;
  while ((m = re.exec(s))) maxCount = Math.max(maxCount, parseInt(m[1], 10));
  return { direct, maxCount, pages: direct || maxCount };
}

/** 渲染一份：md → html → pdf，并打印页数 */
async function renderOne(cdp, sessionId, inputMd, outputPdf, footer) {
  const md = fs.readFileSync(inputMd, 'utf8');
  const html = buildHtml(md, path.dirname(path.resolve(inputMd)));
  const htmlPath = outputPdf.replace(/\.pdf$/i, '.html');
  fs.mkdirSync(path.dirname(outputPdf), { recursive: true });
  fs.writeFileSync(htmlPath, html, 'utf8');

  const fileUrl = 'file:///' + htmlPath.replace(/\\/g, '/').replace(/^\/+/, '');
  await cdp.send('Page.navigate', { url: fileUrl }, sessionId);

  // 等 document 完成 + 字体就绪（中文字体必须等，否则会按后备字体排版、页数就不准）
  const t0 = Date.now();
  for (;;) {
    const r = await cdp
      .send(
        'Runtime.evaluate',
        {
          expression:
            "document.readyState === 'complete' && document.fonts && document.fonts.status",
          returnByValue: true,
        },
        sessionId
      )
      .catch(() => ({ result: {} }));
    const v = r.result?.value;
    if (v === 'loaded' || (v === undefined && Date.now() - t0 > 4000)) break;
    if (Date.now() - t0 > 20000) break;
    await sleep(250);
  }
  await sleep(400);

  const { data } = await cdp.send(
    'Page.printToPDF',
    {
      landscape: false,
      displayHeaderFooter: true,
      printBackground: true,
      scale: 1,
      paperWidth: 8.27, // A4 = 210mm
      paperHeight: 11.69, // A4 = 297mm
      marginTop: 0.71, // 18mm
      marginBottom: 0.67, // 17mm（留出页脚）
      marginLeft: 0.65, // 16.5mm
      marginRight: 0.65,
      headerTemplate: '<div></div>',
      footerTemplate: footer,
      preferCSSPageSize: false,
      transferMode: 'ReturnAsBase64',
    },
    sessionId
  );

  const buf = Buffer.from(data, 'base64');
  fs.writeFileSync(outputPdf, buf);
  const { direct, maxCount, pages } = countPdfPages(buf);
  console.log(`PDF   : ${outputPdf}`);
  console.log(`HTML  : ${htmlPath}`);
  console.log(`size  : ${(buf.length / 1024).toFixed(1)} KB`);
  console.log(`pages : ${pages}  (direct=${direct}, count=${maxCount})`);
  return pages;
}

async function main() {
  const footer =
    '<div style="width:100%;font-family:SimSun,serif;font-size:8.5pt;color:#666;' +
    'text-align:center;padding:0 12mm;line-height:1.2;">' +
    '第 <span class="pageNumber"></span> 页 / 共 <span class="totalPages"></span> 页</div>';

  // 整批只起一次浏览器：连起多个 headless 实例既慢又容易失败
  const { child, userDataDir, ver } = await launchChrome();
  console.log(`browser: ${ver.Browser}`);
  try {
    const ws = new WebSocket(ver.webSocketDebuggerUrl);
    await new Promise((res, rej) => {
      ws.onopen = res;
      ws.onerror = () => rej(new Error('WS 连接失败'));
    });
    const cdp = new CDP(ws);

    const { targetId } = await cdp.send('Target.createTarget', { url: 'about:blank' });
    const { sessionId } = await cdp.send('Target.attachToTarget', { targetId, flatten: true });
    await cdp.send('Page.enable', {}, sessionId);
    await cdp.send('Runtime.enable', {}, sessionId);

    for (const [inputMd, outputPdf] of pairs) {
      try {
        await renderOne(cdp, sessionId, inputMd, outputPdf, footer);
      } catch (e) {
        // 单份失败不拖垮整批：记下来、继续下一份，最后以非零码退出
        console.error(`FAILED ${path.basename(inputMd)}: ${e.message}`);
        process.exitCode = 1;
      }
    }
    await cdp.send('Target.closeTarget', { targetId }).catch(() => {});
    try { ws.close(); } catch {}
  } finally {
    killTree(child);
    try {
      fs.rmSync(userDataDir, { recursive: true, force: true });
    } catch {
      /* 临时目录删不掉不影响结果 */
    }
  }
}

main()
  .then(() => process.exit(process.exitCode || 0))
  .catch((e) => {
    console.error('FAILED: ' + (e.stack || e.message));
    process.exit(1);
  });
