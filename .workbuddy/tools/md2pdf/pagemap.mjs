/**
 * pagemap —— 量出「每章落在第几页」，用于判断某份文档到底哪一段把页数撑超了
 *
 * 用法:
 *   node pagemap.mjs <某个 .html>            # 量 docs/大作业/文档/pdf/*.html
 *   node pagemap.mjs <a.html> <b.html> ...
 *
 * 原理:
 *   Page.printToPDF 的内容区尺寸是可以算出来的 ——
 *     内容宽 = (paperWidth  - marginLeft - marginRight) inch × 96
 *     内容高 = (paperHeight - marginTop  - marginBottom) inch × 96
 *   把无头浏览器的视口按这个尺寸固定，再读 document 的 scrollHeight，
 *   就等价于"打印时正文一共多高"，除以内容高即为页数。
 *
 *   注意：这里量的是**近似值**，用于定位"哪一段该删"，不作为交付页数的最终口径；
 *   最终页数一律以 Page.printToPDF 出来的 PDF 为准（md2pdf.mjs / build.mjs）。
 */

import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawn, spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..', '..', '..');

const CHROME = process.env.MD2PDF_CHROME || 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';

// 与 md2pdf.mjs 的 printToPDF 参数保持一致（改一处必须同时改另一处）
const PAPER_W = 8.27;
const PAPER_H = 11.69;
const M_TOP = 0.71;
const M_BOTTOM = 0.67;
const M_LEFT = 0.65;
const M_RIGHT = 0.65;

const CSS_W = Math.round((PAPER_W - M_LEFT - M_RIGHT) * 96);
const CSS_H = Math.round((PAPER_H - M_TOP - M_BOTTOM) * 96);

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

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
      }, 60000);
      timer.unref?.();
      this.pending.set(id, { res, rej, timer });
      this.ws.send(JSON.stringify({ id, method, params, ...(sessionId ? { sessionId } : {}) }));
    });
  }
}

async function launchChromeOnce() {
  const port = 9500 + Math.floor(Math.random() * 300);
  const userDataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'pagemap-'));
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
        signal: AbortSignal.timeout(1500),
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
  return { child, port, userDataDir };
}

async function launchChrome(attempts = 4) {
  let lastErr;
  for (let i = 1; i <= attempts; i++) {
    try {
      return await launchChromeOnce();
    } catch (e) {
      lastErr = e;
      await sleep(1500);
    }
  }
  throw lastErr;
}

const MEASURE_JS = `(() => {
  const rows = [];
  const walk = (el) => {
    for (const c of el.children) {
      const tag = c.tagName;
      if (/^H[1-6]$/.test(tag)) {
        rows.push({
          level: +tag[1],
          text: (c.textContent || '').trim().slice(0, 60),
          top: Math.round(c.getBoundingClientRect().top + window.scrollY),
        });
      }
      walk(c);
    }
  };
  walk(document.body);
  return JSON.stringify({
    total: Math.round(document.documentElement.scrollHeight),
    rows,
  });
})()`;

async function measure(cdp, sessionId, htmlPath) {
  const fileUrl = 'file:///' + path.resolve(htmlPath).replace(/\\/g, '/').replace(/^\/+/, '');
  await cdp.send('Emulation.setDeviceMetricsOverride', {
    width: CSS_W,
    height: CSS_H,
    deviceScaleFactor: 1,
    mobile: false,
  });
  await cdp.send('Page.navigate', { url: fileUrl }, sessionId);

  const t0 = Date.now();
  for (;;) {
    const r = await cdp
      .send(
        'Runtime.evaluate',
        {
          expression: "document.readyState === 'complete' && document.fonts && document.fonts.status",
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

  const r = await cdp.send('Runtime.evaluate', { expression: MEASURE_JS, returnByValue: true }, sessionId);
  return JSON.parse(r.result.value);
}

const files = process.argv.slice(2).filter((a) => !a.startsWith('--'));
if (!files.length) {
  console.error('用法: node pagemap.mjs <某个 .html> [更多 .html]');
  process.exit(2);
}

const out = [];
const say = (s = '') => {
  console.log(s);
  out.push(s);
};

const LOG_DIR = path.join(ROOT, '.workbuddy', 'logs');
fs.mkdirSync(LOG_DIR, { recursive: true });
const rep = path.join(LOG_DIR, 'md2pdf-pagemap.txt');

say(`内容区（与 printToPDF 参数对齐）: ${CSS_W} × ${CSS_H} px  = A4 ${PAPER_W}in×${PAPER_H}in 去除四边留白`);
say('');

fs.writeFileSync(rep, out.join('\n') + '\n', 'utf8'); // 先落一次盘：后面即使崩了也留得下东西
let child = null;
let port = 0;
try {
  const launched = await launchChrome();
  child = launched.child;
  port = launched.port;
  say('  chrome ready on port ' + port);
  const list = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
  const page = list.find((t) => t.type === 'page');
  const ws = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((res, rej) => {
    ws.onopen = res;
    ws.onerror = rej;
  });
  const cdp = new CDP(ws);
  const sessionId = (await cdp.send('Target.attachToTarget', { targetId: page.id, flatten: true })).sessionId;

  for (const f of files) {
    const { total, rows } = await measure(cdp, sessionId, f);
    const pages = total / CSS_H;
    const last = rows.length ? rows[rows.length - 1] : null;
    const lastPage = last ? Math.floor(last.top / CSS_H) + 1 : 0;
    const fill = last ? ((last.top % CSS_H) / CSS_H) * 100 : 0;

    say('==== ' + path.basename(f) + ' ====');
    say(`  正文总高 ${total} px  ÷  ${CSS_H} px/页  =  ${pages.toFixed(2)} 页  →  ceil = ${Math.ceil(pages)} 页`);
    say(`  最后一个标题「${last ? last.text : '-'}」落在第 ${lastPage} 页、距页顶 ${fill.toFixed(0)}% 处`);
    say(`  —— 即末页只用到 ${(((last ? last.top % CSS_H : 0) / CSS_H) * 100).toFixed(0)}% 之后的部分；该页剩余空间 = ${CSS_H - (last ? last.top % CSS_H : 0)} px`);
    say('');
    say('  每章起止（页内百分比 = 距该页顶部的位置）:');
    for (let i = 0; i < rows.length; i++) {
      const r = rows[i];
      const p = Math.floor(r.top / CSS_H) + 1;
      const pct = ((r.top % CSS_H) / CSS_H) * 100;
      const next = rows[i + 1];
      const span = next ? (next.top - r.top) / CSS_H : (total - r.top) / CSS_H;
      const indent = '  '.repeat(Math.max(0, r.level - 2));
      say(
        `    ${indent}${String(r.level === 2 ? '' : '· ')}${r.text.padEnd(34)} ` +
          `p${String(p).padStart(2)}  +${pct.toFixed(0).padStart(3)}%   ≈${span.toFixed(2)} 页`
      );
    }
    say('');
  }
} catch (e) {
  say('!! 失败: ' + (e && e.stack ? e.stack : String(e)));
} finally {
  if (child) killTree(child);
  fs.writeFileSync(rep, out.join('\n') + '\n', 'utf8');
  process.exit(0);
}
