/**
 * slides2pdf —— 把一组「已经是页面尺寸」的 PNG 合成一份多页 PDF（本机离线可用）
 *
 * 为什么需要它：
 *   画布工具自带的 PDF 导出会把所有 Frame 平铺到**同一页**（实测 /Count=1），
 *   对「一页一张幻灯片」的答辩稿不适用。
 *   本机又没装 LibreOffice / Office，不能把 pptx 转 PDF。
 *   → 复用 md2pdf 的 Chrome + CDP 打印链路，每张 PNG 占一页。
 *
 * 用法：
 *   node slides2pdf.mjs <out.pdf> <img1.png> <img2.png> ... [--chrome=<path>] [--w=1920] [--h=1080]
 *
 * 产出：out.pdf（每页尺寸 = w×h 英寸换算，页边距 0，位图 1:1 铺满）
 */

import fs from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { spawn, spawnSync } from 'node:child_process';

const args = process.argv.slice(2);
const argVal = (k) => {
  const a = args.find((x) => x.startsWith(`--${k}=`));
  return a ? a.slice(k.length + 3) : null;
};
const positional = args.filter((a) => !a.startsWith('--'));
if (positional.length < 2) {
  console.error('用法: node slides2pdf.mjs <out.pdf> <img1.png> [img2.png ...] [--chrome=<path>] [--w=1920] [--h=1080]');
  process.exit(2);
}
const outPdf = positional[0];
const images = positional.slice(1);
const W = parseInt(argVal('w') || '1920', 10);
const H = parseInt(argVal('h') || '1080', 10);

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

const mimeOf = (p) => {
  const e = p.toLowerCase().slice(p.lastIndexOf('.'));
  return e === '.png' ? 'image/png'
    : e === '.jpg' || e === '.jpeg' ? 'image/jpeg'
    : e === '.webp' ? 'image/webp'
    : 'application/octet-stream';
};

function buildHtml() {
  const figs = images.map((p, i) => {
    if (!fs.existsSync(p)) {
      console.warn(`  ! 缺图：${p}`);
      return `<section class="pg"><div class="miss">缺图 ${i + 1}</div></section>`;
    }
    const data = fs.readFileSync(p).toString('base64');
    return `<section class="pg"><img src="data:${mimeOf(p)};base64,${data}" alt="page ${i + 1}"/></section>`;
  }).join('\n');

  return `<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8"/>
<title>答辩 PPT</title>
<style>
  @page { size: ${W}px ${H}px; margin: 0; }
  *{ box-sizing:border-box; }
  html,body{ margin:0; padding:0; background:#fff; }
  .pg{
    width:${W}px; height:${H}px; margin:0; padding:0;
    overflow:hidden; page-break-after:always; break-after:page; position:relative;
  }
  .pg:last-child{ page-break-after:auto; break-after:auto; }
  .pg img{ width:${W}px; height:${H}px; display:block; object-fit:contain; }
  .miss{ width:${W}px; height:${H}px; display:flex; align-items:center; justify-content:center;
         font: 48px "Noto Sans SC","SimHei",sans-serif; color:#999; background:#f4f7fb; }
</style></head>
<body>
${figs}
</body></html>`;
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function killTree(child) {
  try {
    if (process.platform === 'win32' && child.pid) {
      spawnSync('taskkill', ['/PID', String(child.pid), '/T', '/F'], { stdio: 'ignore' });
    } else if (child.pid) {
      child.kill('SIGKILL');
    }
  } catch { /* 忽略 */ }
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
        clearTimeout(timer);
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
      }, 120000);
      timer.unref?.();
      this.pending.set(id, { res, rej, timer });
      this.ws.send(JSON.stringify({ id, method, params, ...(sessionId ? { sessionId } : {}) }));
    });
  }
}

async function launchChromeOnce() {
  const port = 9533 + Math.floor(Math.random() * 300);
  const userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'slides2pdf-'));
  const child = spawn(CHROME, [
    '--headless=new',
    `--remote-debugging-port=${port}`,
    '--remote-allow-origins=*',
    `--user-data-dir=${userDataDir}`,
    '--no-first-run', '--no-default-browser-check',
    '--disable-extensions', '--disable-gpu', '--hide-scrollbars',
    '--allow-file-access-from-files',
    'about:blank',
  ], { stdio: 'ignore' });

  const t0 = Date.now();
  let ver = null;
  while (Date.now() - t0 < 40000) {
    try {
      const res = await fetch(`http://127.0.0.1:${port}/json/version`, { signal: AbortSignal.timeout(1500) });
      ver = await res.json();
      break;
    } catch { await sleep(300); }
  }
  if (!ver) { killTree(child); throw new Error('Chrome 未在 40s 内就绪（port=' + port + '）'); }
  console.log(`chrome ready on port ${port} in ${Date.now() - t0}ms`);
  return { child, userDataDir, ver };
}

async function launchChrome(attempts = 4) {
  let lastErr;
  for (let i = 1; i <= attempts; i++) {
    try { return await launchChromeOnce(); }
    catch (e) { lastErr = e; console.log(`chrome 启动第 ${i} 次失败：${e.message}`); await sleep(1500); }
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

async function main() {
  const html = buildHtml();
  const htmlPath = outPdf.replace(/\.pdf$/i, '.html');
  fs.mkdirSync(path.dirname(path.resolve(outPdf)), { recursive: true });
  fs.writeFileSync(htmlPath, html, 'utf8');
  console.log(`HTML  : ${htmlPath}  (${(html.length / 1024 / 1024).toFixed(2)} MB, ${images.length} 页)`);

  const { child, userDataDir, ver } = await launchChrome();
  console.log(`browser: ${ver.Browser}`);
  try {
    const ws = new WebSocket(ver.webSocketDebuggerUrl);
    await new Promise((res, rej) => { ws.onopen = res; ws.onerror = () => rej(new Error('WS 连接失败')); });
    const cdp = new CDP(ws);

    const { targetId } = await cdp.send('Target.createTarget', { url: 'about:blank' });
    const { sessionId } = await cdp.send('Target.attachToTarget', { targetId, flatten: true });
    await cdp.send('Page.enable', {}, sessionId);
    await cdp.send('Runtime.enable', {}, sessionId);

    const fileUrl = 'file:///' + htmlPath.replace(/\\/g, '/').replace(/^\/+/, '');
    await cdp.send('Page.navigate', { url: fileUrl }, sessionId);

    const t0 = Date.now();
    for (;;) {
      const r = await cdp.send('Runtime.evaluate', {
        expression: "document.readyState === 'complete' && document.images.length > 0 && Array.from(document.images).every(i => i.complete)",
        returnByValue: true,
      }, sessionId).catch(() => ({ result: {} }));
      if (r.result?.value === true || Date.now() - t0 > 30000) break;
      await sleep(300);
    }
    await sleep(600);

    const { data } = await cdp.send('Page.printToPDF', {
      landscape: false,
      displayHeaderFooter: false,
      printBackground: true,
      scale: 1,
      paperWidth: W / 96,
      paperHeight: H / 96,
      marginTop: 0, marginBottom: 0, marginLeft: 0, marginRight: 0,
      preferCSSPageSize: true,
      transferMode: 'ReturnAsBase64',
    }, sessionId);

    const buf = Buffer.from(data, 'base64');
    fs.writeFileSync(outPdf, buf);
    const { direct, maxCount, pages } = countPdfPages(buf);
    console.log(`PDF   : ${outPdf}`);
    console.log(`size  : ${(buf.length / 1024).toFixed(1)} KB`);
    console.log(`pages : ${pages}  (direct=${direct}, count=${maxCount})   期望 ${images.length}`);
    if (pages !== images.length) {
      console.error(`!! 页数不符：期望 ${images.length}，实得 ${pages}`);
      process.exitCode = 1;
    }

    await cdp.send('Target.closeTarget', { targetId }).catch(() => {});
    try { ws.close(); } catch { /* 忽略 */ }
  } finally {
    killTree(child);
    try { fs.rmSync(userDataDir, { recursive: true, force: true }); } catch { /* 忽略 */ }
  }
}

main()
  .then(() => process.exit(process.exitCode || 0))
  .catch((e) => { console.error('FAILED: ' + (e.stack || e.message)); process.exit(1); });
