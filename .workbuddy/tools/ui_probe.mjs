/**
 * T6 补充探针：对两个「目视才发现、需要量化」的问题取硬证据。
 *
 * P-1 报修表格「声明列宽」合计（DOM 里读到的 col 宽度是 EP 按容器拉伸后的结果，
 *     不是 SFC 里声明的值；验收口径要的是声明值 1535px，所以直接解析源文件）。
 * P-2 资产台账操作列 4 个按钮换行（截图里看到「删除」被挤到第二行）。
 * P-3 1366x768 下报修详情弹窗纵向超长时，底部按钮能否滚到（T6 卡 1.3-e 的真实形态）。
 *
 * 用法：node ui_probe.mjs
 */
import fs from 'node:fs';
import path from 'node:path';

const BASE = 'http://127.0.0.1:5173';
const LOG_DIR = 'D:/code/Manager_system/.workbuddy/logs';
const OUT = 'D:/code/Manager_system/docs/大作业/AI留痕/截图/T6-核心功能界面';
const TOKEN = fs.readFileSync(path.join(LOG_DIR, 'token_admin.txt'), 'utf8').trim();
const SRC = 'D:/code/Manager_system/RuoYi-Vue3/src/views/laboratory';

const logs = [];
const log = (m) => {
  const line = `[${new Date().toISOString().slice(11, 19)}] ${m}`;
  logs.push(line);
  fs.writeFileSync(path.join(LOG_DIR, 'ui_probe.log'), logs.join('\n'), 'utf8');
  console.log(line);
};

// ---------------------------------------------------------------- P-1 声明列宽
function declaredColumns(file) {
  const src = fs.readFileSync(path.join(SRC, file), 'utf8');
  const body = src.slice(src.indexOf('<template>'), src.indexOf('</template>'));
  const re = /<el-table-column\b([^>]*?)\/?>/gs;
  const out = [];
  let m;
  while ((m = re.exec(body))) {
    const attrs = m[1];
    const label = (attrs.match(/label="([^"]*)"/) || [, attrs.match(/type="([^"]*)"/) ? `(type=${attrs.match(/type="([^"]*)"/)[1]})` : '(无 label)'])[1];
    const w = attrs.match(/\swidth="(\d+)"/);
    const mw = attrs.match(/min-width="(\d+)"/);
    out.push({ label, width: w ? +w[1] : null, minWidth: mw ? +mw[1] : null });
  }
  return out;
}

log('=== P-1 报修表格：SFC 里声明的列宽 ===');
const repCols = declaredColumns('repair/index.vue');
let declared = 0;
repCols.forEach((c, i) => {
  const v = c.width ?? c.minWidth;
  const kind = c.width != null ? 'width' : 'min-width';
  declared += v;
  log(`  ${String(i + 1).padStart(2)}. ${c.label.padEnd(8)} ${kind}=${v}`);
});
log(`  声明合计 = ${declared}px  →  T6 目标 1535px  →  ${declared === 1535 ? '一致 OK' : '不一致'}`);
const fixedSum = repCols.filter((c) => c.width != null).reduce((a, c) => a + c.width, 0);
const minSum = repCols.filter((c) => c.minWidth != null).reduce((a, c) => a + c.minWidth, 0);
log(`  其中 width 固定列合计 = ${fixedSum}px，min-width 弹性列合计 = ${minSum}px`);

log('=== P-1b 资产表格：SFC 里声明的列宽 ===');
const assetCols = declaredColumns('asset/index.vue');
let assetDeclared = 0;
assetCols.forEach((c, i) => {
  const v = c.width ?? c.minWidth;
  assetDeclared += v;
  log(`  ${String(i + 1).padStart(2)}. ${c.label.padEnd(8)} ${c.width != null ? 'width' : 'min-width'}=${v}`);
});
log(`  声明合计 = ${assetDeclared}px`);

// ---------------------------------------------------------------- CDP
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
      setTimeout(() => {
        if (this.pending.has(id)) {
          this.pending.delete(id);
          rej(new Error('timeout ' + method));
        }
      }, 60000);
    });
  }
}

const ver = await fetch('http://127.0.0.1:9222/json/version').then((r) => r.json());
const ws = new WebSocket(ver.webSocketDebuggerUrl);
await new Promise((res, rej) => {
  ws.onopen = res;
  ws.onerror = () => rej(new Error('ws fail'));
});
const cdp = new CDP(ws);
const { targetId } = await cdp.send('Target.createTarget', { url: 'about:blank' });
const { sessionId } = await cdp.send('Target.attachToTarget', { targetId, flatten: true });
await cdp.send('Page.enable', {}, sessionId);
await cdp.send('Runtime.enable', {}, sessionId);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const ev = async (expr) => {
  const r = await cdp.send('Runtime.evaluate', { expression: `(async()=>{${expr}})()`, awaitPromise: true, returnByValue: true, userGesture: true }, sessionId);
  if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description || r.exceptionDetails.text);
  return r.result?.value;
};
const raw = async (expr) => (await cdp.send('Runtime.evaluate', { expression: expr, returnByValue: true, userGesture: true }, sessionId)).result?.value;
const waitFor = async (expr, label, timeout = 30000) => {
  const t0 = Date.now();
  while (Date.now() - t0 < timeout) {
    try { if (await raw(`(()=>{try{return !!(${expr})}catch(e){return false}})()`)) { log(`  wait OK ${label}`); return; } } catch {}
    await sleep(350);
  }
  throw new Error('wait timeout: ' + label);
};
const shot = async (f) => {
  const { data } = await cdp.send('Page.captureScreenshot', { format: 'png' }, sessionId);
  fs.writeFileSync(path.join(OUT, f), Buffer.from(data, 'base64'));
  log(`  SHOT ${f} (${(fs.statSync(path.join(OUT, f)).size / 1024).toFixed(1)} KB)`);
};

await cdp.send('Emulation.setDeviceMetricsOverride', { width: 1920, height: 1080, deviceScaleFactor: 1, mobile: false }, sessionId);
await cdp.send('Page.navigate', { url: `${BASE}/login` }, sessionId);
await waitFor(`document.querySelector('#app')`, '登录页');
await sleep(900);
await ev(`document.cookie='Admin-Token=${TOKEN}; path=/'; return 1;`);

// ---------------------------------------------------------------- P-2 资产操作列换行
log('=== P-2 资产台账操作列按钮是否换行（1920） ===');
await cdp.send('Page.navigate', { url: `${BASE}/laboratory/asset` }, sessionId);
await waitFor(`document.querySelectorAll('.laboratory-page .el-table__row').length>0`, '资产表格');
await sleep(1500);
const p2 = await raw(`(()=>{
  const th=[...document.querySelectorAll('.laboratory-page th')].find(t=>t.innerText.trim()==='操作');
  const colW = th ? Math.round(th.getBoundingClientRect().width) : -1;
  const tds=[...document.querySelectorAll('.laboratory-page td.el-table-fixed-column--right')].filter(td=>td.querySelector('button'));
  const rows=tds.slice(0,5).map(td=>{
    const cell=td.querySelector('.cell')||td;
    const btns=[...cell.querySelectorAll('button')];
    // 用每个按钮的 offsetTop 判断落在第几行
    const tops=[...new Set(btns.map(b=>Math.round(b.getBoundingClientRect().top)))];
    return {btnCount:btns.length, lines:tops.length,
            cellScrollW:cell.scrollWidth, cellClientW:cell.clientWidth,
            clientH:Math.round(cell.getBoundingClientRect().height),
            labels:btns.map(b=>b.innerText.trim()).join('/')};
  });
  const rowH=[...document.querySelectorAll('.laboratory-page .el-table__row')].slice(0,3).map(r=>Math.round(r.getBoundingClientRect().height));
  return JSON.stringify({opColWidth:colW, sample:rows, rowHeights:rowH});
})()`);
log(`  操作列声明/实测宽 = ${JSON.parse(p2).opColWidth}px`);
JSON.parse(p2).sample.forEach((s, i) => log(`  行${i + 1}: 按钮${s.btnCount}个 占${s.lines}行 [${s.labels}] 单元格高=${s.clientH}px`));
log(`  行高样本: ${JSON.parse(p2).rowHeights.join(', ')}px  （若 >52px 说明被按钮换行撑高）`);
await shot('T6-P2-资产操作列按钮换行.png');

// ---------------------------------------------------------------- P-3 1366 弹窗能否滚到底部
log('=== P-3 1366x768 报修详情弹窗纵向可达性 ===');
await cdp.send('Emulation.setDeviceMetricsOverride', { width: 1366, height: 768, deviceScaleFactor: 1, mobile: false }, sessionId);
await cdp.send('Page.navigate', { url: `${BASE}/laboratory/repair` }, sessionId);
await waitFor(`document.querySelectorAll('.laboratory-page .el-table__row').length>0`, '报修表格');
await sleep(1300);
await ev(`
  const rows=[...document.querySelectorAll('.laboratory-page .el-table__row')];
  const b=[...rows[0].querySelectorAll('button')].find(x=>x.innerText.includes('详情'));
  if(b) b.click();
  return 1;
`);
await waitFor(`document.querySelector('.repair-meta')`, '详情弹窗');
await sleep(1400);

const p3a = await raw(`(()=>{
  const pick=s=>{const e=document.querySelector(s); if(!e) return null;
    const cs=getComputedStyle(e); const r=e.getBoundingClientRect();
    return {sel:s, overflowY:cs.overflowY, scrollH:e.scrollHeight, clientH:e.clientHeight,
            scrollable:e.scrollHeight>e.clientHeight+1, rectTop:Math.round(r.top), rectBottom:Math.round(r.bottom)}};
  const dlg=[...document.querySelectorAll('.el-dialog')].find(d=>d.offsetParent!==null);
  const footer=dlg?dlg.querySelector('.el-dialog__footer'):null;
  return JSON.stringify({winH:window.innerHeight, viewportScrollable: document.documentElement.scrollHeight>document.documentElement.clientHeight,
    dlgHeight: dlg?Math.round(dlg.getBoundingClientRect().height):-1,
    footerBottom: footer?Math.round(footer.getBoundingClientRect().bottom):-1,
    containers:[pick('.el-overlay'), pick('.el-overlay-dialog'), pick('.el-scrollbar__wrap')].filter(Boolean)});
})()`);
log(`  P-3a 滚动前: ${p3a}`);

const p3b = await ev(`
  const cands=['.el-overlay-dialog','.el-overlay','body'];
  const done=[];
  for(const s of cands){
    const e=document.querySelector(s);
    if(!e) continue;
    const before=e.scrollTop;
    e.scrollTop = 999999;
    await new Promise(r=>setTimeout(r,600));
    done.push(s+': '+before+' -> '+e.scrollTop+' (max '+(e.scrollHeight-e.clientHeight)+')');
  }
  await new Promise(r=>setTimeout(r,400));
  const dlg=[...document.querySelectorAll('.el-dialog')].find(d=>d.offsetParent!==null);
  const footer=dlg?dlg.querySelector('.el-dialog__footer'):null;
  const fr=footer?footer.getBoundingClientRect():null;
  const closeBtn=dlg?[...dlg.querySelectorAll('.el-dialog__footer button')].map(b=>b.innerText.trim()):[];
  return JSON.stringify({scrolled:done,
    footerBottom: fr?Math.round(fr.bottom):-1, winH:window.innerHeight,
    footerVisibleAfterScroll: fr ? fr.bottom <= window.innerHeight + 1 : null, footerButtons: closeBtn});
`);
log(`  P-3b 滚动后: ${p3b}`);
await shot('T6-P3-1366弹窗滚到底部.png');

log('PROBE DONE');
await cdp.send('Target.closeTarget', { targetId });
ws.close();
process.exit(0);
