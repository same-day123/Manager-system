/**
 * T6 补充探针 2：精确测「1366x768 下报修详情弹窗底部按钮能否滚到」。
 *
 * 上一版踩的坑：报修页有 3 个 el-dialog（详情 / 选择资产 / 维修评价），
 * 都 append-to-body，于是 DOM 里有 3 组 .el-overlay / .el-overlay-dialog。
 * 用 document.querySelector 会抓到第一个（隐藏的那组），量出来全是 0×0，结论完全错。
 * 正确做法：从「可见的那个 .el-dialog」出发，用 closest() 取它自己的祖先链。
 */
import fs from 'node:fs';
import path from 'node:path';

const BASE = 'http://127.0.0.1:5173';
const LOG_DIR = 'D:/code/Manager_system/.workbuddy/logs';
const TOKEN = fs.readFileSync(path.join(LOG_DIR, 'token_admin.txt'), 'utf8').trim();

const logs = [];
const log = (m) => {
  const line = `[${new Date().toISOString().slice(11, 19)}] ${m}`;
  logs.push(line);
  fs.writeFileSync(path.join(LOG_DIR, 'ui_probe2.log'), logs.join('\n'), 'utf8');
  console.log(line);
};

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
  send(method, params = {}, sessionId) {
    const id = ++this.id;
    return new Promise((res, rej) => {
      this.pending.set(id, { res, rej });
      this.ws.send(JSON.stringify({ id, method, params, ...(sessionId ? { sessionId } : {}) }));
      setTimeout(() => { if (this.pending.has(id)) { this.pending.delete(id); rej(new Error('timeout ' + method)); } }, 60000);
    });
  }
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const ver = await fetch('http://127.0.0.1:9222/json/version').then((r) => r.json());
const ws = new WebSocket(ver.webSocketDebuggerUrl);
await new Promise((res, rej) => { ws.onopen = res; ws.onerror = () => rej(new Error('ws fail')); });
const cdp = new CDP(ws);
const { targetId } = await cdp.send('Target.createTarget', { url: 'about:blank' });
const { sessionId } = await cdp.send('Target.attachToTarget', { targetId, flatten: true });
await cdp.send('Page.enable', {}, sessionId);
await cdp.send('Runtime.enable', {}, sessionId);
const raw = async (e) => (await cdp.send('Runtime.evaluate', { expression: e, returnByValue: true, userGesture: true }, sessionId)).result?.value;
const ev = async (e) => {
  const r = await cdp.send('Runtime.evaluate', { expression: `(async()=>{${e}})()`, awaitPromise: true, returnByValue: true, userGesture: true }, sessionId);
  if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description || r.exceptionDetails.text);
  return r.result?.value;
};
const waitFor = async (expr, label, t = 30000) => {
  const t0 = Date.now();
  while (Date.now() - t0 < t) {
    try { if (await raw(`(()=>{try{return !!(${expr})}catch(e){return false}})()`)) return; } catch {}
    await sleep(350);
  }
  throw new Error('timeout ' + label);
};

for (const H of [768, 900]) {
  const W = H === 768 ? 1366 : 1440;
  log(`================ 视口 ${W}x${H} ================`);
  await cdp.send('Emulation.setDeviceMetricsOverride', { width: W, height: H, deviceScaleFactor: 1, mobile: false }, sessionId);
  await cdp.send('Page.navigate', { url: `${BASE}/login` }, sessionId);
  await waitFor(`document.querySelector('#app')`, 'login');
  await sleep(800);
  await ev(`document.cookie='Admin-Token=${TOKEN}; path=/'; return 1;`);
  await cdp.send('Page.navigate', { url: `${BASE}/laboratory/repair` }, sessionId);
  await waitFor(`document.querySelectorAll('.laboratory-page .el-table__row').length>0`, 'table');
  await sleep(1200);
  await ev(`const r=[...document.querySelectorAll('.laboratory-page .el-table__row')];
            const b=[...r[0].querySelectorAll('button')].find(x=>x.innerText.includes('详情')); if(b) b.click(); return 1;`);
  await waitFor(`document.querySelector('.repair-meta')`, 'dialog');
  await sleep(1200);

  const before = await raw(`(()=>{
    const dlg=[...document.querySelectorAll('.el-dialog')].find(d=>d.offsetParent!==null);
    if(!dlg) return 'no visible dialog';
    const chain=[]; let e=dlg;
    while(e && e!==document.documentElement){
      const cs=getComputedStyle(e); const r=e.getBoundingClientRect();
      chain.push({tag:e.tagName.toLowerCase(), cls:(e.className||'').toString().slice(0,60),
        pos:cs.position, ovY:cs.overflowY, ovX:cs.overflowX,
        rect:Math.round(r.width)+'x'+Math.round(r.height),
        clientH:e.clientHeight, scrollH:e.scrollHeight,
        canScroll:e.scrollHeight>e.clientHeight+1});
      e=e.parentElement;
    }
    const footer=dlg.querySelector('.el-dialog__footer');
    const fr=footer.getBoundingClientRect();
    const header=dlg.querySelector('.el-dialog__headerbtn');
    const hr=header.getBoundingClientRect();
    return JSON.stringify({winH:window.innerHeight, dlgH:Math.round(dlg.getBoundingClientRect().height),
      dlgRect:{top:Math.round(dlg.getBoundingClientRect().top),bottom:Math.round(dlg.getBoundingClientRect().bottom)},
      footerBottom:Math.round(fr.bottom), footerButtons:[...footer.querySelectorAll('button')].map(b=>b.innerText.trim()),
      headerCloseBtn: {top:Math.round(hr.top), bottom:Math.round(hr.bottom), visibleInViewport: hr.bottom<=window.innerHeight+1},
      chain});
  })()`);
  const b = JSON.parse(before);
  log(`  弹窗高 ${b.dlgH}px，top=${b.dlgRect.top} bottom=${b.dlgRect.bottom}，视口高 ${b.winH}`);
  log(`  底部按钮 [${b.footerButtons.join('/')}] 的 bottom=${b.footerBottom} → 视口内? ${b.footerBottom <= b.winH + 1}`);
  log(`  右上角 X 按钮 bottom=${b.headerCloseBtn.bottom} → 视口内? ${b.headerCloseBtn.visibleInViewport}`);
  log('  祖先链（从弹窗往上）:');
  b.chain.forEach((c) => log(`    ${c.tag}.${c.cls} | pos=${c.pos} overflow-y=${c.ovY} | ${c.rect} clientH=${c.clientH} scrollH=${c.scrollH} 可滚动=${c.canScroll}`));
  const scrollable = b.chain.filter((c) => c.canScroll);
  log(`  → 可滚动祖先数量 = ${scrollable.length}${scrollable.length ? ' （' + scrollable.map((s) => s.tag + '.' + s.cls).join(', ') + '）' : '  ⚠ 没有任何祖先能滚动'}`);

  if (scrollable.length) {
    const after = await ev(`
      const dlg=[...document.querySelectorAll('.el-dialog')].find(d=>d.offsetParent!==null);
      let e=dlg; const res=[];
      while(e && e!==document.documentElement){
        if(e.scrollHeight>e.clientHeight+1){ e.scrollTop=999999; res.push(e.tagName+'.'+e.className+': scrollTop='+e.scrollTop); }
        e=e.parentElement;
      }
      await new Promise(r=>setTimeout(r,700));
      const fr=dlg.querySelector('.el-dialog__footer').getBoundingClientRect();
      return JSON.stringify({scrolled:res, footerBottom:Math.round(fr.bottom), winH:window.innerHeight,
        nowVisible: fr.bottom<=window.innerHeight+1});
    `);
    log(`  滚动后: ${after}`);
  }
}

log('PROBE2 DONE');
await cdp.send('Target.closeTarget', { targetId });
ws.close();
process.exit(0);
