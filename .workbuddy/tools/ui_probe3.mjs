/**
 * T6 补充探针 3：
 *  Q-1 正确解析 SFC 里「声明的列宽」（上一版用 indexOf('</template>') 被内层
 *      <template #default> 提前截断，只数到 3 列，结论错误）。
 *  Q-2 验证 element-ui.scss 里新加的 .el-dialog { max-width: calc(100vw - 32px) }
 *      在「窄到装不下声明宽度」时是否真的生效（宽视口下它应当完全不介入）。
 */
import fs from 'node:fs';
import path from 'node:path';

const LOG_DIR = 'D:/code/Manager_system/.workbuddy/logs';
const TOKEN = fs.readFileSync(path.join(LOG_DIR, 'token_admin.txt'), 'utf8').trim();
const SRC = 'D:/code/Manager_system/RuoYi-Vue3/src/views/laboratory';
const BASE = 'http://127.0.0.1:5173';

const logs = [];
const log = (m) => {
  const line = m;
  logs.push(line);
  fs.writeFileSync(path.join(LOG_DIR, 'ui_probe3.log'), logs.join('\n'), 'utf8');
  console.log(line);
};

// ------------------------------------------------------------- Q-1 声明列宽
function declaredColumns(file) {
  const src = fs.readFileSync(path.join(SRC, file), 'utf8');
  // 主模板 = 从开头的 <template> 到 <script setup> 之前（不能用第一个 </template>，会被内层插槽截断）
  const start = src.indexOf('<template>');
  const end = src.indexOf('<script');
  const body = src.slice(start, end);
  const re = /<el-table-column\b([\s\S]*?)(?:\/>|>)/g;
  const out = [];
  let m;
  while ((m = re.exec(body))) {
    const a = m[1];
    const label =
      (a.match(/label="([^"]*)"/) || [])[1] ||
      ('(type=' + ((a.match(/type="([^"]*)"/) || [])[1] || '?') + ')');
    const w = a.match(/(?:^|\s)width="(\d+)"/);
    const mw = a.match(/min-width="(\d+)"/);
    out.push({ label, width: w ? +w[1] : null, minWidth: mw ? +mw[1] : null });
  }
  return out;
}

for (const [file, target] of [['repair/index.vue', 1535], ['asset/index.vue', null], ['room/index.vue', null]]) {
  const cols = declaredColumns(file);
  let sum = 0;
  log(`=== ${file} 声明列宽（共 ${cols.length} 列） ===`);
  cols.forEach((c, i) => {
    const v = c.width ?? c.minWidth;
    sum += v;
    log(`  ${String(i + 1).padStart(2)}. ${c.label.padEnd(10)} ${c.width != null ? 'width' : 'min-width'}=${String(v).padStart(4)}`);
  });
  const fs_ = cols.filter((c) => c.width != null).reduce((a, c) => a + c.width, 0);
  const ms = cols.filter((c) => c.minWidth != null).reduce((a, c) => a + c.minWidth, 0);
  log(`  → 声明合计 = ${sum}px  (width 固定 ${fs_} + min-width 弹性 ${ms})`);
  if (target != null) log(`  → 与 T6 目标 ${target}px ${sum === target ? '一致 ✅' : '不一致 ❌'}`);
}

// ------------------------------------------------------------- Q-2 max-width 生效条件
class CDP {
  constructor(ws) {
    this.ws = ws;
    this.id = 0;
    this.pending = new Map();
    ws.onmessage = (e) => {
      const m = JSON.parse(e.data);
      if (m.id && this.pending.has(m.id)) {
        const { res, rej } = this.pending.get(m.id);
        this.pending.delete(m.id);
        if (m.error) rej(new Error(JSON.stringify(m.error)));
        else res(m.result);
      }
    };
  }
  send(m2, p = {}, s) {
    const id = ++this.id;
    return new Promise((res, rej) => {
      this.pending.set(id, { res, rej });
      this.ws.send(JSON.stringify({ id, method: m2, params: p, ...(s ? { sessionId: s } : {}) }));
      setTimeout(() => { if (this.pending.has(id)) { this.pending.delete(id); rej(new Error('timeout ' + m2)); } }, 60000);
    });
  }
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const ver = await fetch('http://127.0.0.1:9222/json/version').then((r) => r.json());
const ws = new WebSocket(ver.webSocketDebuggerUrl);
await new Promise((res, rej) => { ws.onopen = res; ws.onerror = () => rej(new Error('ws')); });
const cdp = new CDP(ws);
const { targetId } = await cdp.send('Target.createTarget', { url: 'about:blank' });
const { sessionId } = await cdp.send('Target.attachToTarget', { targetId, flatten: true });
await cdp.send('Page.enable', {}, sessionId);
await cdp.send('Runtime.enable', {}, sessionId);
const raw = async (e) => (await cdp.send('Runtime.evaluate', { expression: e, returnByValue: true, userGesture: true }, sessionId)).result?.value;
const ev = async (e) => (await cdp.send('Runtime.evaluate', { expression: `(async()=>{${e}})()`, awaitPromise: true, returnByValue: true, userGesture: true }, sessionId)).result?.value;
const waitFor = async (expr, label, t = 30000) => {
  const t0 = Date.now();
  while (Date.now() - t0 < t) {
    try { if (await raw(`(()=>{try{return !!(${expr})}catch(e){return false}})()`)) return; } catch {}
    await sleep(350);
  }
  throw new Error('timeout ' + label);
};

log('=== .el-dialog max-width 生效条件验证（报修详情，声明 width 860px） ===');
log('  规则：max-width: calc(100vw - 32px)  → 视口 < 892px 时才开始收紧');
for (const [W, H] of [[1920, 1080], [1366, 768], [900, 700], [800, 600], [600, 600]]) {
  await cdp.send('Emulation.setDeviceMetricsOverride', { width: W, height: H, deviceScaleFactor: 1, mobile: false }, sessionId);
  await cdp.send('Page.navigate', { url: `${BASE}/login` }, sessionId);
  await waitFor(`document.querySelector('#app')`, 'login');
  await sleep(700);
  await ev(`document.cookie='Admin-Token=${TOKEN}; path=/'; return 1;`);
  await cdp.send('Page.navigate', { url: `${BASE}/laboratory/repair` }, sessionId);
  await waitFor(`document.querySelectorAll('.laboratory-page .el-table__row').length>0`, 'table');
  await sleep(1100);
  await ev(`const r=[...document.querySelectorAll('.laboratory-page .el-table__row')];
            const b=[...r[0].querySelectorAll('button')].find(x=>x.innerText.includes('详情')); if(b) b.click(); return 1;`);
  await waitFor(`document.querySelector('.repair-meta')`, 'dialog');
  await sleep(900);
  const r = await raw(`(()=>{
    const dlg=[...document.querySelectorAll('.el-dialog')].find(d=>d.offsetParent!==null);
    const rc=dlg.getBoundingClientRect(); const cs=getComputedStyle(dlg);
    return JSON.stringify({vw:window.innerWidth, maxW:cs.maxWidth, usedW:cs.width,
      rectW:Math.round(rc.width), left:Math.round(rc.left), right:Math.round(rc.right),
      overflow: (Math.round(rc.left)<0 || Math.round(rc.right)>window.innerWidth) ? 'YES 溢出视口' : 'NO 在视口内',
      constrained: Math.round(rc.width) < 860 ? '受 max-width 收紧' : '未收紧（按声明 860px）'});
  })()`);
  log(`  视口 ${String(W).padStart(4)}px → ${r}`);
}

log('PROBE3 DONE');
await cdp.send('Target.closeTarget', { targetId });
ws.close();
process.exit(0);
