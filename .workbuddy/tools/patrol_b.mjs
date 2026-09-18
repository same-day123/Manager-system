/**
 * 演示路径巡检 · 阶段 B：报修完整闭环
 *   student1 提交 → labadmin 审核通过 → 开始维修 → 维修完成 → student1 评价
 *
 * ⚠️ 本阶段会【真实写库】。闭环结束后由 patrol_cleanup.sql 复原，并逐项比对基线。
 * 每一步都以「真实点击 + 采集接口状态码」为准，不用直接调接口代替点击。
 */
import fs from 'node:fs';
import path from 'node:path';
import { API, LOG_DIR, connect, makeEval, makeLogger, apiLogin, openWithToken, sleep, dbQuery } from './patrol_lib.mjs';

const log = makeLogger('patrol_b.log');
// 脚本化的交互很长，任何未捕获的 rejection 都会让日志「戛然而止」→ 必须显式记录
process.on('unhandledRejection', (e) => {
  log('!! unhandledRejection: ' + (e && e.stack ? e.stack : String(e)));
  process.exit(9);
});
process.on('uncaughtException', (e) => {
  log('!! uncaughtException: ' + (e && e.stack ? e.stack : String(e)));
  process.exit(9);
});
const results = [];
const say = (id, title, verdict, detail) => {
  results.push({ id, title, verdict, detail });
  log(`  [${verdict}] ${id} ${title} — ${detail}`);
};

const SUBMIT_DESC = '【巡检测试】UI设计第二轮演示路径巡检合成单，请忽略';
const REPAIR_MAN = '巡检测试';
// 可报修资产列表分页 10 条/页、按编号倒序 → 不能写死编号，改为「选弹窗第一行并读出它的编号」
let ASSET_CODE = '';

const { cdp, sessionId, targetId, ws } = await connect(log);
let cur = null; // {raw, ev, waitFor}

// ---------------------------------------------------------------- 工具
const JS_HELPERS = `
  window.__p = {
    dlg: () => [...document.querySelectorAll('.el-dialog')].filter(d=>d.offsetParent!==null),
    visDlg: (titlePart) => [...document.querySelectorAll('.el-dialog')]
        .filter(d=>d.offsetParent!==null)
        .find(d => !titlePart || ((d.querySelector('.el-dialog__title')||{}).innerText||'').includes(titlePart)),
    btn: (scope, text) => [...(scope||document).querySelectorAll('button')]
        .find(b => b.innerText.replace(/\\s+/g,'').includes(text.replace(/\\s+/g,'')) && !b.disabled),
    clickBtn: (scope, text) => { const b = window.__p.btn(scope, text); if(!b) return 'NO_BUTTON:'+text; b.click(); return 'CLICKED:'+text; },
    setVal: (el, v) => {
      const proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
      const setter = Object.getOwnPropertyDescriptor(proto, 'value').set;
      setter.call(el, v);
      el.dispatchEvent(new Event('input', { bubbles: true }));
      el.dispatchEvent(new Event('change', { bubbles: true }));
      return 'SET';
    },
    toast: () => [...document.querySelectorAll('.el-message')].map(m=>m.innerText.trim()).join(' | ')
  };
  return 1;
`;

async function shot(name) {
  try {
    const s = await cdp.send('Page.captureScreenshot', { format: 'png' }, sessionId);
    const dir = 'D:/code/Manager_system/docs/大作业/AI留痕/截图/巡检-演示路径';
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(path.join(dir, name + '.png'), Buffer.from(s.data, 'base64'));
    log(`     （证据图 ${name}.png）`);
  } catch (e) {
    log('     截图失败：' + e.message);
  }
}

async function boot(token, route, readyExpr, label) {
  const r = await openWithToken(cdp, sessionId, token, route, readyExpr, log, label);
  await r.ev(JS_HELPERS);
  cur = r;
  return r;
}

// ================================================================ ① 提交
log('=== ④-1 student1 提交报修（提交入口 + 选资产 + 提交） ===');
const sLogin = await apiLogin('student1', 'admin123');
if (!sLogin.token) { log('student1 登录失败：' + JSON.stringify(sLogin)); process.exit(1); }
log('  student1 登录 OK');

await boot(sLogin.token, '/laboratory/repair', `document.querySelectorAll('.laboratory-page .el-table__row').length>0`, 'repair table');

const toolbar = await cur.raw(`(()=>{
  const btns=[...document.querySelectorAll('.laboratory-page .el-row.mb8 button')].map(b=>b.innerText.trim());
  return JSON.stringify({工具栏按钮:btns, 有新增: btns.some(x=>x.includes('新增'))});
})()`);
log('  工具栏：' + toolbar);
say('P4-1a', 'student1 可见「新增报修」入口', JSON.parse(toolbar).有新增 ? 'OK' : '问题', toolbar);

const c1 = await cur.ev(`return window.__p.clickBtn(document, '新增报修');`);
log('  点「新增报修」→ ' + c1);
await cur.waitFor(`window.__p.visDlg() && window.__p.visDlg().querySelector('.el-form')`, 'submit dialog', 10000);
await sleep(900);
await shot('P04-1-报修提交弹窗');

const c2 = await cur.ev(`const d=window.__p.visDlg(); return window.__p.clickBtn(d, '选择资产');`);
log('  点「选择资产」→ ' + c2);
await cur.waitFor(`[...document.querySelectorAll('.el-dialog')].some(d=>d.offsetParent!==null && d.querySelectorAll('.el-table__row').length>0)`, 'asset picker', 10000);
await sleep(1000);

const picker = await cur.raw(`(()=>{
  const d=[...document.querySelectorAll('.el-dialog')].filter(x=>x.offsetParent!==null).find(x=>x.querySelectorAll('.el-table__row').length>0);
  if(!d) return JSON.stringify({选资产弹窗:'未出现'});
  const rows=[...d.querySelectorAll('.el-table__row')];
  return JSON.stringify({标题:(d.querySelector('.el-dialog__title')||{}).innerText||'', 行数:rows.length,
    前两行:rows.slice(0,2).map(r=>r.innerText.replace(/\\s+/g,' ').slice(0,46))});
})()`);
log('  选资产弹窗：' + picker);
say('P4-1b', '「选择可报修资产」弹窗与列表', JSON.parse(picker).行数 > 0 ? 'OK' : '问题', picker);

const c3 = await cur.ev(`
  const d=[...document.querySelectorAll('.el-dialog')].filter(x=>x.offsetParent!==null).find(x=>x.querySelectorAll('.el-table__row').length>0);
  const rows=[...d.querySelectorAll('.el-table__row')];
  if(!rows.length) return 'NO_ROWS';
  const row=rows[0];
  const code=(row.innerText.match(/LAB-[A-Z]+-\\d{4}-\\d{3}/)||[''])[0];
  const b=[...row.querySelectorAll('button')].find(x=>x.innerText.includes('选择'));
  if(!b) return 'NO_BUTTON';
  b.click();
  return code || 'NO_CODE';
`);
ASSET_CODE = String(c3).startsWith('LAB-') ? String(c3) : '';
log('  选中可报修资产弹窗第 1 行 → ' + c3 + '（编号=' + ASSET_CODE + '）');
await sleep(900);

// 护栏：确认主弹窗的「报修资产」已回填，否则表单校验必然拦住提交
const filled = await cur.raw(`(()=>{
  const d=window.__p.visDlg(); if(!d) return 'NO_DIALOG';
  const ins=[...d.querySelectorAll('input')];
  const v=ins.map(i=>i.value).find(x=>/^[^\\s]*\\S/.test(x) && /LAB|实|仪|设|机|台/.test(x));
  return v || 'EMPTY';
})()`);
log('  报修资产回填检查 → ' + JSON.stringify(filled));
if (!ASSET_CODE || filled === 'EMPTY' || filled === 'NO_DIALOG') {
  log('  ✗ 资产未选中，提交必被校验拦住，终止本次闭环（库未被修改）');
  fs.writeFileSync(path.join(LOG_DIR, 'patrol_b.result.json'), JSON.stringify({ results, aborted: true, reason: 'asset not selected', c3, filled }, null, 2), 'utf8');
  await cdp.send('Target.closeTarget', { targetId });
  ws.close();
  process.exit(3);
}

// 故障等级：真实点开下拉并选第一项
const c4 = await cur.ev(`
  const d=window.__p.visDlg();
  const wrap=d.querySelector('.el-select__wrapper') || d.querySelector('.el-select');
  if(!wrap) return 'NO_SELECT';
  wrap.click();
  return 'CLICKED_SELECT';
`);
log('  点「故障等级」下拉 → ' + c4);
const dd = await (async () => {
  const { waitFor } = makeEval(cdp, sessionId);
  return waitFor(`[...document.querySelectorAll('.el-select-dropdown__item')].some(i=>i.offsetParent!==null)`, 'dropdown', 6000);
})();
const c5 = await cur.ev(`
  const items=[...document.querySelectorAll('.el-select-dropdown__item')].filter(i=>i.offsetParent!==null);
  if(!items.length) return 'NO_OPTION';
  const names=items.map(i=>i.innerText.trim());
  items[0].click();
  return JSON.stringify({选项:names, 已选:names[0]});
`);
log('  下拉选项 → ' + c5);
say('P4-1c', '故障等级下拉可用', dd && !String(c5).startsWith('NO_OPTION') ? 'OK' : '问题', String(c5));
await sleep(500);

const c6 = await cur.ev(`
  const d=window.__p.visDlg();
  const ta=d.querySelector('textarea');
  if(!ta) return 'NO_TEXTAREA';
  window.__p.setVal(ta, '${SUBMIT_DESC}');
  return 'SET_DESC';
`);
log('  填故障描述 → ' + c6);
await sleep(400);

const netBefore = cdp.net.length;
const c7 = await cur.ev(`return window.__p.clickBtn(window.__p.visDlg(), '确定');`);
log('  点「确定」提交 → ' + c7);
await sleep(3000);
const submitNet = cdp.net.slice(netBefore).find((n) => /laboratory\/repair$/.test(n.url) && n.status);
log('  提交接口：' + JSON.stringify(submitNet));
const toast1 = await cur.raw(`window.__p.toast()`);
log('  页面提示：' + JSON.stringify(toast1));
say('P4-1d', '提交报修接口', submitNet && submitNet.status === 200 ? 'OK' : '问题', `POST ${submitNet ? submitNet.url + ' → ' + submitNet.status : '（未捕获）'} ｜ 提示=${toast1}`);

// 从库里查出刚创建的单号 —— 报修列表**没有「故障描述」列**，
// 所以不能靠描述文本在 DOM 里找行，必须用单号定位（这是脚本化的关键）。
const NEWCODE = dbQuery(
  `select repair_code from lab_repair where fault_description = '${SUBMIT_DESC}' and del_flag = '0' order by repair_id desc limit 1;`
);
log('  库中定位到新单号 = ' + JSON.stringify(NEWCODE));
say('P4-1e', '新单落库并取到单号', /^[A-Z]{2}\d+/.test(NEWCODE) ? 'OK' : '问题', `repair_code=${NEWCODE}`);
if (!/^[A-Z]{2}\d+/.test(NEWCODE)) {
  log('  ✗ 未取到单号，后续步骤无法定位，终止（请人工检查库）');
  fs.writeFileSync(path.join(LOG_DIR, 'patrol_b.result.json'), JSON.stringify({ results, net: [...new Map(cdp.net.map(n => [n.url.split('?')[0] + n.status, n])).values()], desc: SUBMIT_DESC, NEWCODE }, null, 2), 'utf8');
  await cdp.send('Target.closeTarget', { targetId });
  ws.close();
  process.exit(2);
}
const status0 = dbQuery(`select status from lab_repair where repair_code='${NEWCODE}';`);
log('  新单当前状态 = ' + status0 + '（应为 0 待审核）');

// ================================================================ ② 审核 / 处理
log('');
log('=== ④-2 labadmin 审核通过 → 开始维修 → 维修完成 ===');
const mLogin = await apiLogin('labadmin', 'admin123');
if (!mLogin.token) { log('labadmin 登录失败'); process.exit(1); }
await boot(mLogin.token, '/laboratory/repair', `document.querySelectorAll('.laboratory-page .el-table__row').length>0`, 'repair table');
await sleep(1000);

const found = await cur.raw(`(()=>{
  const rows=[...document.querySelectorAll('.laboratory-page .el-table__row')];
  return JSON.stringify({行数:rows.length, 前两行:rows.slice(0,2).map(r=>r.innerText.replace(/\\s+/g,' ').slice(0,60)),
    按钮:rows.slice(0,2).map(r=>[...r.querySelectorAll('button')].map(b=>b.innerText.trim()).join('/'))});
})()`);
log('  labadmin 视角列表：' + found);

const STEPS = [
  { from: '0', pick: '待维修', label: '审核通过（0 待审核 → 1 待维修）' },
  { from: '1', pick: '维修中', label: '开始维修（1 待维修 → 2 维修中）' },
  { from: '2', pick: '已完成', label: '维修完成（2 维修中 → 3 已完成）' },
];

for (let i = 0; i < STEPS.length; i++) {
  const st = STEPS[i];
  // 找目标行：用单号精确定位（列表里没有故障描述列，不能靠描述找）
  const openProcess = await cur.ev(`
    const rows=[...document.querySelectorAll('.laboratory-page .el-table__row')];
    const row = rows.find(r=>r.innerText.includes('${NEWCODE}'));
    if(!row) return 'NO_ROW:${NEWCODE} 可见单号=' + rows.map(r=>(r.innerText.match(/[A-Z]{2}\\\\d+/)||[''])[0]).join(',');
    const b=[...row.querySelectorAll('button')].find(x=>x.innerText.includes('处理'));
    if(!b) return 'NO_PROCESS_BUTTON:' + [...row.querySelectorAll('button')].map(x=>x.innerText.trim()).join('/');
    b.click(); return 'CLICKED:' + row.innerText.replace(/\\s+/g,' ').slice(0,50);
  `);
  log(`  第 ${i + 1} 步 ${st.label}`);
  log('    点「处理」→ ' + openProcess);
  if (String(openProcess).startsWith('NO_')) {
    say('P4-2' + (i + 1), st.label, '问题', openProcess);
    break;
  }
  await cur.waitFor(`window.__p.visDlg() && window.__p.visDlg().querySelector('.el-radio')`, 'process dialog', 10000);
  await sleep(900);
  if (i === 0) await shot('P04-2-处理弹窗-审核');

  const radios = await cur.raw(`(()=>{
    const d=window.__p.visDlg();
    return JSON.stringify({标题:(d.querySelector('.el-dialog__title')||{}).innerText||'',
      可选状态:[...d.querySelectorAll('.el-radio')].map(r=>r.innerText.trim())});
  })()`);
  log('    弹窗：' + radios);

  const pickRadio = await cur.ev(`
    const d=window.__p.visDlg();
    const rs=[...d.querySelectorAll('.el-radio')];
    const t=rs.find(r=>r.innerText.includes('${st.pick}'));
    if(!t) return 'NO_RADIO:${st.pick} 实际可选=' + rs.map(r=>r.innerText.trim()).join(',');
    t.click(); return 'CLICKED:${st.pick}';
  `);
  log('    选状态 ' + st.pick + ' → ' + pickRadio);
  await sleep(400);

  // 维修人员/费用（有则填）
  await cur.ev(`
    const d=window.__p.visDlg();
    const ins=[...d.querySelectorAll('input')].filter(i=>i.offsetParent!==null && i.type==='text' && !i.readOnly);
    if(ins.length) window.__p.setVal(ins[0], '${REPAIR_MAN}');
    return 1;
  `);

  const n0 = cdp.net.length;
  const ok2 = await cur.ev(`return window.__p.clickBtn(window.__p.visDlg(), '确定');`);
  log('    点「确定」→ ' + ok2);
  await sleep(2600);
  const netStep = cdp.net.slice(n0).find((n) => /repair\/(audit|\d+)/.test(n.url) || /laboratory\/repair/.test(n.url));
  const toast = await cur.raw(`window.__p.toast()`);
  // 状态机推进以【库里真实状态】为准，不信接口返回
  const stAfter = dbQuery(`select status from lab_repair where repair_code='${NEWCODE}';`);
  const expect = String(i + 1);
  log('    接口：' + JSON.stringify(netStep) + ' ｜ 提示：' + JSON.stringify(toast) + ' ｜ 库中状态=' + stAfter + '（期望 ' + expect + '）');
  say('P4-2' + (i + 1), st.label,
    netStep && netStep.status === 200 && stAfter === expect ? 'OK' : '问题',
    `${netStep ? netStep.url + ' → ' + netStep.status : '未捕获接口'} ｜ 库中状态=${stAfter}(期望${expect}) ｜ 提示=${toast} ｜ 按钮=${ok2}`);

  // 每步后刷新列表，让下一轮能找到行
  await cur.ev(`window.__p.clickBtn(document, '搜索'); return 1;`);
  await sleep(2200);
}

// ================================================================ ③ 评价
log('');
log('=== ④-3 student1 评价（3 已完成 → 评分） ===');
await boot(sLogin.token, '/laboratory/repair', `document.querySelectorAll('.laboratory-page .el-table__row').length>0`, 'repair table');
await sleep(1000);

const evalBtns = await cur.raw(`(()=>{
  const rows=[...document.querySelectorAll('.laboratory-page .el-table__row')];
  return JSON.stringify({行数:rows.length, 每行按钮:rows.map(r=>[...r.querySelectorAll('button')].map(b=>b.innerText.trim()).join('/'))});
})()`);
log('  student1 视角按钮：' + evalBtns);

const openEval = await cur.ev(`
  const rows=[...document.querySelectorAll('.laboratory-page .el-table__row')];
  const row = rows.find(r=>r.innerText.includes('${NEWCODE}'));
  if(!row) return 'NO_ROW:${NEWCODE}';
  const b=[...row.querySelectorAll('button')].find(x=>x.innerText.includes('评价'));
  if(!b) return 'NO_EVAL_BUTTON:' + [...row.querySelectorAll('button')].map(x=>x.innerText.trim()).join('/');
  b.click(); return 'CLICKED';
`);
log('  点「评价」→ ' + openEval);
if (String(openEval) === 'CLICKED') {
  await cur.waitFor(`[...document.querySelectorAll('.el-dialog')].some(d=>d.offsetParent!==null && d.querySelector('.el-rate'))`, 'eval dialog', 8000);
  await sleep(800);
  await shot('P04-3-评价弹窗');
  await cur.ev(`
    const d=[...document.querySelectorAll('.el-dialog')].filter(x=>x.offsetParent!==null).find(x=>x.querySelector('.el-rate'));
    const ta=d.querySelector('textarea'); if(ta) window.__p.setVal(ta, '【巡检】问题已解决，处理及时。');
    return 1;
  `);
  await sleep(400);
  const n0 = cdp.net.length;
  const ok3 = await cur.ev(`
    const d=[...document.querySelectorAll('.el-dialog')].filter(x=>x.offsetParent!==null).find(x=>x.querySelector('.el-rate'));
    return window.__p.clickBtn(d, '提交评价');
  `);
  log('  点「提交评价」→ ' + ok3);
  await sleep(2600);
  const netEval = cdp.net.slice(n0).find((n) => /evaluate/.test(n.url));
  const toast = await cur.raw(`window.__p.toast()`);
  log('  接口：' + JSON.stringify(netEval) + ' ｜ 提示：' + JSON.stringify(toast));
  say('P4-3', '提交评价接口', netEval && netEval.status === 200 ? 'OK' : '问题',
    `${netEval ? netEval.url + ' → ' + netEval.status : '未捕获'} ｜ 提示=${toast} ｜ 按钮=${ok3}`);
} else {
  say('P4-3', '提交评价接口', '问题', String(openEval));
}

// ================================================================ 汇总
log('');
log('=== 闭环后的数据状态（用于复原比对） ===');
const ASSET_ID = dbQuery(`select asset_id from lab_asset where asset_code='${ASSET_CODE}' and del_flag='0';`);
const finalState = dbQuery(
  `select concat('新单状态=', status, ' 评分=', ifnull(rating,'NULL'), ' 维修人员=', ifnull(repair_user_name,'NULL'), ' 维修费用=', ifnull(repair_cost,'NULL')) from lab_repair where repair_code='${NEWCODE}';`
);
log('  新单 ' + NEWCODE + '：' + finalState);
log('  资产 ' + ASSET_CODE + '(id=' + ASSET_ID + ') 状态=' + dbQuery(`select status from lab_asset where asset_id='${ASSET_ID}';`) + '（期望 0 正常，闭环结束应回滚）');
log('  全库：' + dbQuery(`select concat('工单(未删)=', (select count(*) from lab_repair where del_flag='0'), ' 处理记录=', (select count(*) from lab_repair_record)) ;`));
say('P4-4', '闭环终态（含资产联动回滚）',
  /status=3/.test(finalState) && dbQuery(`select status from lab_asset where asset_id='${ASSET_ID}';`) === '0' ? 'OK' : '问题',
  `${finalState} ｜ 资产状态=${dbQuery(`select status from lab_asset where asset_id='${ASSET_ID}';`)}`);

log('');
log('=== 接口状态汇总（全阶段） ===');
const uniq = new Map();
for (const n of cdp.net) uniq.set(n.url.split('?')[0] + '|' + n.status, n);
const bad = cdp.net.filter((n) => n.status >= 400);
log(`  共 ${cdp.net.length} 条接口响应，非成功 ${bad.length} 条`);
for (const n of [...uniq.values()].sort((a, b) => a.url.localeCompare(b.url))) log(`   ${String(n.status).padStart(3)}  ${n.url.split('?')[0]}`);
bad.forEach((n) => log(`  ⚠️ ${n.status} ${n.url}`));
say('P4-X', '闭环全程 4xx/5xx 扫描', bad.length === 0 ? 'OK' : '问题', bad.map((b) => b.status + ' ' + b.url).join(' | ') || '无');

fs.writeFileSync(path.join(LOG_DIR, 'patrol_b.result.json'), JSON.stringify({ results, net: [...uniq.values()], bad, desc: SUBMIT_DESC, repairCode: NEWCODE, assetId: ASSET_ID, assetCode: ASSET_CODE }, null, 2), 'utf8');
log('');
log('PATROL-B DONE');
await cdp.send('Target.closeTarget', { targetId });
ws.close();
process.exit(0);
