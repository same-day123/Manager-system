/**
 * T6 第 11.1 节 V-4 收口探针：报修页操作列 220px 能否放得下「3 个带图标按钮」。
 *
 * 背景：真实库里 4 条工单的状态全是 3（已完成）/ 4（已拒绝），管理员在这两种状态下
 *       操作列只出「详情」1 个按钮 → 3 按钮的极端态一直没量到。
 *
 * 本探针【不写数据库】：只在浏览器里往页面组件 setupState 的 repairList 里插一行合成数据，
 * 量完立即移除（刷新即消失）。这样既不动演示数据，也不用改任何源码。
 *
 * 预期（读 repair/index.vue 的条件得到）：
 *   管理员 + status=0 → 详情 / 处理 / 删除
 *   非管理员 + status=0 → 详情 / 修改 / 删除
 *   两组都是 3 个、都是 2 个汉字 → 宽度等价，故只量一组即可代表极端态。
 */
import fs from 'node:fs';
import path from 'node:path';

const LOG_DIR = 'D:/code/Manager_system/.workbuddy/logs';
const LOG = path.join(LOG_DIR, 'ui_probe_v4.log');
const TOKEN = fs.readFileSync(path.join(LOG_DIR, 'token_admin.txt'), 'utf8').trim();
const BASE = 'http://127.0.0.1:5173';

const logs = [];
const log = (m) => {
  logs.push(m);
  fs.writeFileSync(LOG, logs.join('\n'), 'utf8');
  console.log(m);
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
  send(m2, p = {}, s) {
    const id = ++this.id;
    return new Promise((res, rej) => {
      this.pending.set(id, { res, rej });
      this.ws.send(JSON.stringify({ id, method: m2, params: p, ...(s ? { sessionId: s } : {}) }));
      setTimeout(() => {
        if (this.pending.has(id)) {
          this.pending.delete(id);
          rej(new Error('timeout ' + m2));
        }
      }, 60000);
    });
  }
}
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const ver = await fetch('http://127.0.0.1:9222/json/version').then((r) => r.json());
const ws = new WebSocket(ver.webSocketDebuggerUrl);
await new Promise((res, rej) => {
  ws.onopen = res;
  ws.onerror = () => rej(new Error('ws open failed'));
});
const cdp = new CDP(ws);
const { targetId } = await cdp.send('Target.createTarget', { url: 'about:blank' });
const { sessionId } = await cdp.send('Target.attachToTarget', { targetId, flatten: true });
await cdp.send('Page.enable', {}, sessionId);
await cdp.send('Runtime.enable', {}, sessionId);

const raw = async (e) =>
  (await cdp.send('Runtime.evaluate', { expression: e, returnByValue: true, userGesture: true }, sessionId))
    .result?.value;
const ev = async (e) =>
  (
    await cdp.send(
      'Runtime.evaluate',
      { expression: `(async()=>{${e}})()`, awaitPromise: true, returnByValue: true, userGesture: true },
      sessionId
    )
  ).result?.value;
const waitFor = async (expr, label, t = 30000) => {
  const t0 = Date.now();
  while (Date.now() - t0 < t) {
    try {
      if (await raw(`(()=>{try{return !!(${expr})}catch(e){return false}})()`)) return;
    } catch {}
    await sleep(350);
  }
  throw new Error('timeout ' + label);
};

await cdp.send('Emulation.setDeviceMetricsOverride', { width: 1920, height: 1080, deviceScaleFactor: 1, mobile: false }, sessionId);
await cdp.send('Page.navigate', { url: `${BASE}/login` }, sessionId);
await waitFor(`document.querySelector('#app')`, 'login');
await sleep(700);
await ev(`document.cookie='Admin-Token=${TOKEN}; path=/'; return 1;`);
await cdp.send('Page.navigate', { url: `${BASE}/laboratory/repair` }, sessionId);
await waitFor(`document.querySelectorAll('.laboratory-page .el-table__row').length>0`, 'table');
await sleep(1200);

log('=== 基线（真实库数据，无合成行） ===');
log(
  '  ' +
    (await raw(`(()=>{
      const rows=[...document.querySelectorAll('.laboratory-page .el-table__row')];
      const s=rows.map(r=>[...r.querySelectorAll('button')].map(b=>b.innerText.trim()).join('/'));
      const cnt={}; s.forEach(x=>cnt[x]=(cnt[x]||0)+1);
      return JSON.stringify({行数:rows.length, 每行按钮组合:cnt});
    })()`))
);

// ---------------------------------------------------------- 注入合成行
log('');
log('=== 注入合成行（status=0 待审核，只改内存，不落库） ===');
const injected = await raw(`(()=>{
  // 从表格行的 __vueParentComponent 沿 parent 链找页面组件（setupState 里有 repairList）
  const row=document.querySelector('.laboratory-page .el-table__row');
  let c = row && (row.__vueParentComponent || row.__vue__);
  let page=null;
  while(c){ const s=c.setupState; if(s && Object.prototype.hasOwnProperty.call(s,'repairList')){ page=c; break; } c=c.parent; }
  if(!page) return 'FAIL: 未找到页面组件（setupState 里没有 repairList）';
  window.__LAB_PAGE__ = page;                       // 挂起来，后面移除时用
  const list = page.setupState.repairList;
  if(!Array.isArray(list)) return 'FAIL: repairList 不是数组，实际 '+Object.prototype.toString.call(list);
  window.__LAB_LEN__ = list.length;
  list.push({
    repairId: '__PROBE__',
    repairCode: 'RP-PROBE-0001',
    assetId: null, assetCode: 'LAB-PROBE-001', assetName: '合成探针资产',
    roomId: null, roomName: '合成探针实验室',
    faultLevel: '1', faultDescription: 'V-4 探针合成行',
    status: '0', rating: null,
    applicantId: null, applicantName: '探针', applicantPhone: '13800000000',
    repairCost: null, finishTime: null, createTime: new Date().toISOString()
  });
  return 'OK 注入前行数='+window.__LAB_LEN__+' 注入后='+list.length;
})()`);
log('  ' + injected);

await waitFor(`[...document.querySelectorAll('.laboratory-page .el-table__row')].some(r=>r.innerText.includes('RP-PROBE-0001'))`, 'probe row', 15000);
await sleep(1200);

// ---------------------------------------------------------- 量测
log('');
log('=== V-4 实测：3 按钮在 220px 操作列里的排布 ===');
const res = await raw(`(()=>{
  const tr=[...document.querySelectorAll('.laboratory-page .el-table__row')].find(r=>r.innerText.includes('RP-PROBE-0001'));
  if(!tr) return 'FAIL: 找不到合成行';
  const btns=[...tr.querySelectorAll('button')];
  const rects=btns.map(b=>{const r=b.getBoundingClientRect(); return {t:b.innerText.trim(), w:+r.width.toFixed(1), h:+r.height.toFixed(1), top:+r.top.toFixed(1), left:+r.left.toFixed(1), right:+r.right.toFixed(1)};});
  const tops=[...new Set(rects.map(r=>r.top))];
  const totalW = rects.length ? +(Math.max(...rects.map(r=>r.right)) - Math.min(...rects.map(r=>r.left))).toFixed(1) : 0;
  // 操作列单元格（右下角固定列）
  const cell = btns[0] ? btns[0].closest('.cell') : null;
  const cellBox = cell ? cell.getBoundingClientRect() : null;
  const cellW = cellBox ? +cellBox.width.toFixed(1) : null;
  return JSON.stringify({
    按钮: rects.map(r=>r.t+'('+r.w+')'),
    按钮数: rects.length,
    按钮纵向top去重: tops.length,
    换行: tops.length>1 ? 'YES 有换行' : 'NO 单行',
    按钮总面积宽: totalW,
    操作列单元格内容宽: cellW,
    判定: cellW!=null && totalW>cellW ? '溢出 ❌' : '放得下 ✅',
    行高: +tr.getBoundingClientRect().height.toFixed(1)
  }, null, 0);
})()`);
log('  ' + res);

// 同时给出整表在 1920 下的横滚情况（确认插入行没引入横滚）
const scroll = await raw(`(()=>{
  const wrap=document.querySelector('.laboratory-page .el-scrollbar__wrap');
  if(!wrap) return 'no wrap';
  return JSON.stringify({可视宽:wrap.clientWidth, 内容宽:wrap.scrollWidth, 需横滚: wrap.scrollWidth>wrap.clientWidth+1 ? 'YES '+ (wrap.scrollWidth-wrap.clientWidth)+'px' : 'NO'});
})()`);
log('  整表横滚（1920，含合成行）: ' + scroll);

// ---------------------------------------------------------- 清理
const cleaned = await raw(`(()=>{
  const p=window.__LAB_PAGE__; if(!p) return 'FAIL: 未记录页面组件';
  const l=p.setupState.repairList;
  const before=l.length;
  const i=l.findIndex(x=>x.repairId==='__PROBE__');
  if(i>=0) l.splice(i,1);
  return 'OK 移除合成行 前='+before+' 后='+l.length+'（应回到注入前的 '+window.__LAB_LEN__+'）';
})()`);
log('');
log('  ' + cleaned);
await sleep(800);
const after = await raw(`[...document.querySelectorAll('.laboratory-page .el-table__row')].some(r=>r.innerText.includes('RP-PROBE-0001')) ? '合成行仍在 ❌' : '合成行已消失 ✅'`);
log('  清理复核: ' + after);

log('');
log('PROBE-V4 DONE');
await cdp.send('Target.closeTarget', { targetId });
ws.close();
process.exit(0);
