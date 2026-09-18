/**
 * 演示路径巡检 · 阶段 A（只读，不改任何数据）
 *   ① 登录：文档口令 123456 是否真不成立 / 实际口令 admin123 是否成立（D-20）
 *   ② 首页看板：lab_manager 是否还能看到看板（D-21 复测）
 *   ③ 资产台账：标签（二维码）与履历时间线是否点得通、字典翻译是否生效
 * 全程采集 XHR 状态码 → 自动找出 401 / 403 / 500
 */
import fs from 'node:fs';
import path from 'node:path';
import { API, WEB, LOG_DIR, connect, makeEval, makeLogger, apiLogin, openWithToken, sleep } from './patrol_lib.mjs';

const log = makeLogger('patrol_a.log');
const { cdp, sessionId, targetId, ws } = await connect();
const results = [];
const say = (id, title, verdict, detail) => {
  results.push({ id, title, verdict, detail });
  log(`  [${verdict}] ${id} ${title} — ${detail}`);
};

// ============================================================ ① 登录
log('=== ① 登录路径（D-20 复核） ===');

const documented = await apiLogin('labadmin', '123456');
log(`  A) labadmin / 123456（文档写的）→ HTTP ${documented.http} code=${documented.code} msg=${documented.msg}`);
say('P1-a', 'labadmin + 文档口令 123456', documented.code === 200 ? 'OK' : '问题', `code=${documented.code} msg=${documented.msg}`);

const actual = await apiLogin('labadmin', 'admin123');
log(`  B) labadmin / admin123（脚本实际哈希）→ HTTP ${actual.http} code=${actual.code} msg=${actual.msg}`);
say('P1-b', 'labadmin + 实际口令 admin123', actual.code === 200 ? 'OK' : '问题', `code=${actual.code} msg=${actual.msg} token=${actual.token ? '已取得' : '无'}`);

if (!actual.token) {
  log('  ✗ 无法取得 token，后续路径无法继续');
  await cdp.send('Target.closeTarget', { targetId });
  ws.close();
  process.exit(1);
}
const TOKEN_M = actual.token;

// 顺带确认其它演示账号也用同一口令（只做 1 次抽样，避免加重试计数）
const sample = await apiLogin('student1', 'admin123');
log(`  C) student1 / admin123（抽样）→ code=${sample.code} msg=${sample.msg}`);
say('P1-c', 'student1 + admin123（抽样）', sample.code === 200 ? 'OK' : '问题', `code=${sample.code} msg=${sample.msg}`);
const TOKEN_S = sample.token || '';

// ============================================================ ② 首页看板
log('');
log('=== ② 首页看板（D-21 复测：lab_manager 现在还能看到吗） ===');
{
  const { ok, raw } = await openWithToken(cdp, sessionId, TOKEN_M, '/index', `document.querySelector('.app-wrapper,.app-main')`, log);
  await sleep(1500);
  const info = await raw(`(()=>{
    const txt = document.body.innerText;
    return JSON.stringify({
      有看板容器: !!document.querySelector('.lab-dashboard'),
      指标卡数: document.querySelectorAll('.lab-dashboard .metric-card, .lab-dashboard .stat-card, .lab-dashboard [class*=card]').length,
      canvas数: document.querySelectorAll('.lab-dashboard canvas').length,
      占位提示: /暂无|没有.*权限|无权限|占位/.test(txt) ? (txt.match(/.{0,24}(暂无|没有.{0,6}权限|无权限).{0,24}/)||[''])[0].trim() : null,
      首行文本: (txt.split('\\n').find(x=>x.trim())||'').slice(0,40)
    });
  })()`);
  log('  ' + info);
  const o = JSON.parse(info);
  say('P2', '首页运维看板（lab_manager 取景）', o.有看板容器 && o.canvas数 >= 4 ? 'OK' : '问题',
    `看板容器=${o.有看板容器} canvas=${o.canvas数} 占位提示=${o.占位提示 ? JSON.stringify(o.占位提示) : '无'}`);
}

// ============================================================ ③ 资产台账
log('');
log('=== ③ 资产台账 → 标签（二维码）/ 履历 ===');
{
  const { ok, raw, ev } = await openWithToken(
    cdp, sessionId, TOKEN_M, '/laboratory/asset',
    `document.querySelectorAll('.laboratory-page .el-table__row').length>0`, log, 'asset table'
  );
  await sleep(1200);
  const base = await raw(`(()=>{
    const rows=[...document.querySelectorAll('.laboratory-page .el-table__row')];
    const btns=[...rows[0].querySelectorAll('button')].map(b=>b.innerText.trim());
    return JSON.stringify({行数:rows.length, 首行按钮:btns, 页脚分页: !!document.querySelector('.pagination-container')});
  })()`);
  log('  ' + base);
  say('P3-a', '资产台账列表渲染', JSON.parse(base).行数 > 0 ? 'OK' : '问题', base);

  // 标签（二维码）
  const clickedTag = await ev(`const r=[...document.querySelectorAll('.laboratory-page .el-table__row')][0];
    const b=[...r.querySelectorAll('button')].find(x=>x.innerText.includes('标签')); if(!b) return 'NO_BUTTON';
    b.click(); return 'CLICKED';`);
  log('  点「标签」→ ' + clickedTag);
  const tagReady = await (async () => {
    const { waitFor } = makeEval(cdp, sessionId);
    return waitFor(`[...document.querySelectorAll('.el-dialog')].some(d=>d.offsetParent!==null && d.querySelector('img'))`, 'qr dialog', 8000);
  })();
  const qr = await raw(`(()=>{
    const dlg=[...document.querySelectorAll('.el-dialog')].find(d=>d.offsetParent!==null);
    if(!dlg) return JSON.stringify({弹窗:'未出现'});
    const img=dlg.querySelector('img');
    return JSON.stringify({
      弹窗标题:(dlg.querySelector('.el-dialog__title')||{}).innerText||'',
      有图: !!img,
      图已加载: img ? (img.naturalWidth>0) : false,
      图尺寸: img ? img.naturalWidth+'x'+img.naturalHeight : null,
      imgSrc前缀: img ? String(img.src).slice(0,60) : null
    });
  })()`);
  log('  二维码弹窗：' + qr);
  const q = JSON.parse(qr);
  say('P3-b', '资产「标签」二维码弹窗', q.有图 && q.图已加载 ? 'OK' : '问题', qr);
  await ev(`const dlg=[...document.querySelectorAll('.el-dialog')].find(d=>d.offsetParent!==null);
    if(dlg){const b=[...dlg.querySelectorAll('button')].find(x=>/关闭|取消/.test(x.innerText)); if(b) b.click();} return 1;`);
  await sleep(800);

  // 履历
  const clickedHis = await ev(`const r=[...document.querySelectorAll('.laboratory-page .el-table__row')][0];
    const b=[...r.querySelectorAll('button')].find(x=>x.innerText.includes('履历')); if(!b) return 'NO_BUTTON';
    b.click(); return 'CLICKED';`);
  log('  点「履历」→ ' + clickedHis);
  const { waitFor } = makeEval(cdp, sessionId);
  await waitFor(`document.querySelector('.el-drawer') || [...document.querySelectorAll('.el-dialog')].some(d=>d.offsetParent!==null && d.querySelector('.el-timeline'))`, 'history', 8000);
  await sleep(1000);
  const his = await raw(`(()=>{
    const d=[...document.querySelectorAll('.el-drawer,.el-dialog')].find(x=>x.offsetParent!==null && x.querySelector('.el-timeline'));
    if(!d) return JSON.stringify({履历:'未出现'});
    const items=[...d.querySelectorAll('.el-timeline-item')];
    const texts=items.map(i=>i.innerText.replace(/\\s+/g,' ').trim()).slice(0,6);
    const arrows=items.map(i=>{const m=i.innerText.match(/(\\S+)\\s*→\\s*(\\S+)/); return m? m[1]+'→'+m[2] : null}).filter(Boolean);
    return JSON.stringify({类型:d.className.includes('drawer')?'drawer':'dialog', 条目数:items.length, 前几条:texts, 状态箭头:arrows});
  })()`);
  log('  履历：' + his);
  const h = JSON.parse(his);
  const bareCode = (h.状态箭头 || []).filter((a) => /^\\d+→\\d+$/.test(a) || /^\d+→\d+$/.test(a));
  say('P3-c', '资产履历时间线（含字典翻译）', h.条目数 > 0 && bareCode.length === 0 ? 'OK' : '问题',
    `条目=${h.条目数} 状态箭头=${JSON.stringify(h.状态箭头)} 裸码=${JSON.stringify(bareCode)}`);
}

// ============================================================ 网络状态
log('');
log('=== XHR / 接口状态汇总（自动找 401/403/500） ===');
const bad = cdp.net.filter((n) => n.status >= 400);
const uniq = new Map();
for (const n of cdp.net) uniq.set(n.url.split('?')[0] + '|' + n.status, n);
log(`  共采集 ${cdp.net.length} 条接口响应；非 2xx/3xx 的有 ${bad.length} 条`);
for (const n of [...uniq.values()].sort((a, b) => a.url.localeCompare(b.url))) {
  log(`   ${String(n.status).padStart(3)}  ${n.url.split('?')[0]}`);
}
if (bad.length) {
  log('  ⚠️ 非成功状态明细：');
  bad.forEach((n) => log(`     ${n.status} ${n.url}`));
}
say('P-X', '接口 4xx/5xx 扫描', bad.length === 0 ? 'OK' : '问题', `非成功响应 ${bad.length} 条：${bad.map((b) => b.status + ' ' + b.url).join(' | ') || '无'}`);

// 落盘结构化结果
fs.writeFileSync(
  path.join(LOG_DIR, 'patrol_a.result.json'),
  JSON.stringify({ results, net: [...uniq.values()], bad }, null, 2),
  'utf8'
);

log('');
log('PATROL-A DONE');
await cdp.send('Target.closeTarget', { targetId });
ws.close();
process.exit(0);
