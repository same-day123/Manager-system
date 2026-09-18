/**
 * T6 界面截图采集脚本（UI设计会话）
 *
 * 依赖：Chrome 已以 --remote-debugging-port=9222 启动；前端 dev server 在 5173；
 *      后端 8080 已起；logs/token_admin.txt 里有 ui_login.mjs 拿到的 jwt。
 *
 * 为什么不用 playwright：本机离线装不上 npm 包（AGENTS 也禁止新增依赖），
 * 而 Node 22 自带 WebSocket + fetch，可以直接对话 CDP。
 *
 * 采集内容：
 *   A. 答辩 PPT 第 3 类素材「核心功能界面」4 张：首页看板 / 资产台账 / 资产履历 / 报修处理时间线
 *   B. T6 卡第 10.3 节 4 项「静态看不出来、必须目视」的验证截图（V-1 ~ V-4）
 *
 * 账号说明：首页看板受 laboratory:dashboard:view 保护，而**真实库里这条权限菜单不存在**
 *   （laboratory_permission_fix.sql 未执行），只有超管 admin 能过这道门，故统一用 admin 取景。
 */

import fs from 'node:fs';
import path from 'node:path';

const DEBUG_PORT = 9222;
const BASE = 'http://127.0.0.1:5173';
const LOG_DIR = 'D:/code/Manager_system/.workbuddy/logs';
const OUT = 'D:/code/Manager_system/docs/大作业/AI留痕/截图/T6-核心功能界面';
const TOKEN = fs.readFileSync(path.join(LOG_DIR, 'token_admin.txt'), 'utf8').trim();

fs.mkdirSync(OUT, { recursive: true });

const logs = [];
const log = (m) => {
  const line = `[${new Date().toISOString().slice(11, 19)}] ${m}`;
  logs.push(line);
  fs.writeFileSync(path.join(LOG_DIR, 'ui_shots.log'), logs.join('\n'), 'utf8');
  console.log(line);
};

class CDP {
  constructor(ws) {
    this.ws = ws;
    this.id = 0;
    this.pending = new Map();
    this.handlers = new Map();
    ws.onmessage = (ev) => {
      const m = JSON.parse(ev.data);
      if (m.id && this.pending.has(m.id)) {
        const { res, rej } = this.pending.get(m.id);
        this.pending.delete(m.id);
        if (m.error) rej(new Error(JSON.stringify(m.error)));
        else res(m.result);
      } else if (m.method) {
        (this.handlers.get(m.method) || []).forEach((h) => h(m.params, m.sessionId));
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
          rej(new Error(`CDP timeout: ${method}`));
        }
      }, 60000);
    });
  }
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function main() {
  const ver = await fetch(`http://127.0.0.1:${DEBUG_PORT}/json/version`).then((r) => r.json());
  log(`connected: ${ver.Browser}  frontend=${BASE}  token=${TOKEN.slice(0, 12)}…(${TOKEN.length})`);

  const ws = new WebSocket(ver.webSocketDebuggerUrl);
  await new Promise((res, rej) => {
    ws.onopen = res;
    ws.onerror = () => rej(new Error('WS connect failed'));
  });
  const cdp = new CDP(ws);
  const { targetId } = await cdp.send('Target.createTarget', { url: 'about:blank' });
  const { sessionId } = await cdp.send('Target.attachToTarget', { targetId, flatten: true });
  await cdp.send('Page.enable', {}, sessionId);
  await cdp.send('Runtime.enable', {}, sessionId);

  const pageErrors = [];
  const failedReq = [];
  cdp.handlers.set('Runtime.consoleAPICalled', [
    (p) => {
      if (p.type === 'error') pageErrors.push((p.args || []).map((a) => a.value ?? a.description ?? '').join(' '));
    },
  ]);
  cdp.handlers.set('Network.enable', []);
  cdp.handlers.set('Network.loadingFailed', [(p) => failedReq.push(p.errorText)]);

  const ev = async (expr) => {
    const r = await cdp.send(
      'Runtime.evaluate',
      { expression: `(async()=>{ ${expr} })()`, awaitPromise: true, returnByValue: true, userGesture: true },
      sessionId
    );
    if (r.exceptionDetails) throw new Error('eval: ' + (r.exceptionDetails.exception?.description || r.exceptionDetails.text));
    return r.result?.value;
  };
  const raw = async (expr) => {
    const r = await cdp.send('Runtime.evaluate', { expression: expr, returnByValue: true, userGesture: true }, sessionId);
    if (r.exceptionDetails) throw new Error('eval: ' + (r.exceptionDetails.exception?.description || r.exceptionDetails.text));
    return r.result?.value;
  };

  const viewport = async (w, h) => {
    await cdp.send('Emulation.setDeviceMetricsOverride', { width: w, height: h, deviceScaleFactor: 1, mobile: false }, sessionId);
    log(`viewport ${w}x${h}`);
  };
  const goto = async (url) => {
    await cdp.send('Page.navigate', { url }, sessionId);
    log(`goto ${url}`);
  };
  const waitFor = async (expr, label, timeout = 30000) => {
    const t0 = Date.now();
    while (Date.now() - t0 < timeout) {
      try {
        if (await raw(`(()=>{ try { return !!(${expr}); } catch(e){ return false; } })()`)) {
          log(`wait OK (${Date.now() - t0}ms): ${label}`);
          return true;
        }
      } catch {
        /* 导航中途忽略 */
      }
      await sleep(350);
    }
    log(`wait TIMEOUT: ${label} << ${expr.slice(0, 110)}`);
    throw new Error('timeout: ' + label);
  };
  const shot = async (file, label) => {
    const { data } = await cdp.send('Page.captureScreenshot', { format: 'png' }, sessionId);
    const p = path.join(OUT, file);
    fs.writeFileSync(p, Buffer.from(data, 'base64'));
    log(`SHOT ${file}  (${(fs.statSync(p).size / 1024).toFixed(1)} KB)  ${label || ''}`);
  };
  /** 关掉任何挡在前面的消息框（例如若依的「初始密码」提示）；点「取消」避免跳转个人中心 */
  const dismissMsgBox = async () => {
    const r = await ev(`
      const boxes = [...document.querySelectorAll('.el-message-box')].filter(b => b.offsetParent !== null);
      if (!boxes.length) return 'no message box';
      const btns = [...boxes[0].querySelectorAll('.el-message-box__btns button')];
      const cancel = btns.find(b => b.innerText.includes('取消')) || btns[0];
      cancel.click();
      await new Promise(r => setTimeout(r, 600));
      return 'dismissed';
    `);
    log(`message box -> ${r}`);
  };

  // ================================================================ 登录态
  await viewport(1920, 1080);
  await goto(`${BASE}/login`);
  await waitFor(`document.readyState==='complete' && !!document.querySelector('#app')`, '登录页就绪', 40000);
  await sleep(1000);
  log(`inject token -> ${await ev(`document.cookie='Admin-Token=${TOKEN}; path=/'; return document.cookie.includes('Admin-Token') ? 'cookie set':'FAILED';`)}`);

  // ================================================================ 1. 首页运维看板
  await goto(`${BASE}/index`);
  await waitFor(`document.querySelector('.lab-dashboard')`, '首页 .lab-dashboard', 40000);
  await dismissMsgBox();
  await waitFor(`document.querySelectorAll('.chart-grid canvas').length >= 4`, '4 张 ECharts canvas 渲染完成', 40000);
  await sleep(2600); // 等动画收尾
  const dash = await raw(`(()=>{
    const cards = [...document.querySelectorAll('.metric-card')];
    const canv = [...document.querySelectorAll('.chart-grid canvas')];
    const root = document.querySelector('.lab-dashboard');
    return {metrics: cards.map(c=>c.innerText.replace(/\\s+/g,' ').trim().slice(0,40)),
            canvas: canv.length,
            canvasSizes: canv.map(c=>c.width+'x'+c.height),
            fallback: !!document.body.innerText.includes('暂没有运维工作台'),
            pageBg: getComputedStyle(root).backgroundColor,
            rootSW: root.scrollWidth, rootCW: root.clientWidth};
  })()`);
  log(`首页: 指标卡 ${dash.metrics.length} 张 / ECharts canvas ${dash.canvas} 个 (${dash.canvasSizes.join(', ')})`);
  dash.metrics.forEach((m, i) => log(`  指标#${i+1}: ${m}`));
  log(`首页: 降级提示=${dash.fallback} 页底色=${dash.pageBg} 横向滚动=${dash.rootSW > dash.rootCW ? '有' : '无'}`);
  await shot('T6-01-首页运维看板.png', '答辩 PPT 第 3 类素材');

  // ================================================================ 2. 资产台账
  await goto(`${BASE}/laboratory/asset`);
  await waitFor(`document.querySelectorAll('.laboratory-page .el-table__row').length > 0`, '资产表格出数据', 40000);
  await sleep(1500);
  const asset = await raw(`(()=>{
    const page = document.querySelector('.laboratory-page');
    const cs = getComputedStyle(page);
    const tbl = document.querySelector('.laboratory-page > .el-table');
    const tcs = getComputedStyle(tbl);
    const pag = document.querySelector('.laboratory-page .pagination-container');
    const form = document.querySelector('.laboratory-page > .el-form');
    const fcs = getComputedStyle(form);
    const row = document.querySelector('.laboratory-page > .el-row.mb8');
    const rcs = getComputedStyle(row);
    return {rows: page.querySelectorAll('.el-table__row').length,
      pageBg: cs.backgroundColor, pageMinH: cs.minHeight, pagePad: cs.padding,
      tableMarginTop: tcs.marginTop, tableRadius: tcs.borderRadius, tableOverflow: tcs.overflow, tableBorder: tcs.borderColor,
      formRadius: fcs.borderTopLeftRadius, formBorderBottom: fcs.borderBottomWidth, formPad: fcs.padding,
      rowRadiusBottom: rcs.borderBottomLeftRadius, rowBorderTop: rcs.borderTopWidth,
      pagBg: pag ? getComputedStyle(pag).backgroundColor : 'n/a',
      rootSW: page.scrollWidth, rootCW: page.clientWidth};
  })()`);
  log(`资产页: 行数=${asset.rows}`);
  log(`  页面底色=${asset.pageBg} min-height=${asset.pageMinH} padding=${asset.pagePad}`);
  log(`  搜索区 card: radius=${asset.formRadius} border-bottom=${asset.formBorderBottom} padding=${asset.formPad}`);
  log(`  工具行 card: radius-bottom=${asset.rowRadiusBottom} border-top=${asset.rowBorderTop}`);
  log(`  表格 card: margin-top=${asset.tableMarginTop} radius=${asset.tableRadius} overflow=${asset.tableOverflow} border=${asset.tableBorder}`);
  log(`  分页容器底色=${asset.pagBg}   [实验室改动的关键点：应为 rgba(0,0,0,0)]`);
  log(`  横向滚动=${asset.rootSW > asset.rootCW ? '有' : '无'} (${asset.rootSW}/${asset.rootCW})`);

  // D-22 复测：操作列 4 个按钮是否还在折行
  const opcol = await raw(`(()=>{
    const th=[...document.querySelectorAll('.laboratory-page th')].find(t=>t.innerText.trim()==='操作');
    const colW = th ? Math.round(th.getBoundingClientRect().width) : -1;
    const tds=[...document.querySelectorAll('.laboratory-page td.el-table-fixed-column--right')].filter(td=>td.querySelector('button'));
    const lines = tds.map(td=>new Set([...td.querySelectorAll('button')].map(b=>Math.round(b.getBoundingClientRect().top))).size);
    const cellH = tds.length ? Math.round((tds[0].querySelector('.cell')||tds[0]).getBoundingClientRect().height) : -1;
    const btnN  = tds.length ? tds[0].querySelectorAll('button').length : -1;
    return JSON.stringify({colWidth:colW, sampleLines:lines.slice(0,10), maxLines:Math.max(...lines),
      btnCount:btnN, cellHeight:cellH,
      rowHeights:[...document.querySelectorAll('.laboratory-page .el-table__row')].slice(0,3).map(r=>Math.round(r.getBoundingClientRect().height))});
  })()`);
  const oc = JSON.parse(opcol);
  log(`  资产操作列(D-22 复测): 列宽=${oc.colWidth}px 按钮${oc.btnCount}个 占 ${oc.maxLines} 行 单元格高=${oc.cellHeight}px 行高=${oc.rowHeights.join('/')}`);
  log(`  → ${oc.maxLines === 1 ? '按钮已排成一行 ✅' : '仍有折行 ❌'}`);
  await shot('T6-02-资产台账.png', '答辩 PPT 第 3 类素材');
  await shot('T6-P2-资产操作列按钮换行.png', 'D-22 修复后对照');

  // ================================================================ 3. 资产履历（抽屉）
  log(`资产履历: ${await ev(`
    const rows=[...document.querySelectorAll('.laboratory-page .el-table__row')];
    for (let i=0;i<rows.length;i++){
      const b=[...rows[i].querySelectorAll('button')].find(x=>x.innerText.includes('履历'));
      if(b){ b.click(); return 'clicked row '+i; }
    }
    return 'no 履历 button';
  `)}`);
  await waitFor(`document.querySelectorAll('.record-timeline .el-timeline-item').length>0`, '履历时间线出数据', 25000);
  await sleep(1300);
  const rec = await raw(`(()=>{
    const items=[...document.querySelectorAll('.record-timeline .el-timeline-item')];
    const dw=document.querySelector('.el-drawer');
    return {count:items.length, width: dw?Math.round(dw.getBoundingClientRect().width):-1,
      texts:items.slice(0,6).map(i=>i.innerText.replace(/\\s+/g,' ').trim())};
  })()`);
  log(`资产履历: 抽屉宽=${rec.width}px  条目=${rec.count}`);
  rec.texts.forEach((t, i) => log(`  履历#${i+1}: ${t}`));
  const rawArrow = rec.texts.filter((t) => t.includes('→'));
  log(`  校验「状态码是否已翻译」: 含 → 的条目 ${rawArrow.length} 条，其中出现裸数字码「0 → 2」的有 ${rawArrow.filter((t)=>/[^0-9]0\s*→\s*[0-9]/.test(t)).length} 条`);
  await shot('T6-03-资产履历时间线.png', '答辩 PPT 第 3 类素材');
  await ev(`document.querySelector('.el-drawer__close-btn')?.click(); await new Promise(r=>setTimeout(r,800)); return 1;`);

  // ================================================================ 4. 报修列表 + 详情时间线（1920）
  await goto(`${BASE}/laboratory/repair`);
  await waitFor(`document.querySelectorAll('.laboratory-page .el-table__row').length > 0`, '报修表格出数据', 40000);
  await sleep(1500);
  const rep = await raw(`(()=>{
    const page=document.querySelector('.laboratory-page');
    const tbl=page.querySelector(':scope > .el-table');
    const head=[...tbl.querySelectorAll('.el-table__header col')].map(c=>c.getAttribute('width')||c.style.width);
    const body=page.querySelector('.el-table__body-wrapper .el-scrollbar__wrap');
    const opCol=[...tbl.querySelectorAll('.el-table__header th')];
    return {rows:page.querySelectorAll('.el-table__row').length, colCount:head.length, cols:head,
      bodySW:body.scrollWidth, bodyCW:body.clientWidth,
      opWidth: (()=>{const th=opCol.find(t=>t.innerText.trim()==='操作'); return th?Math.round(th.getBoundingClientRect().width):-1})(),
      rootSW:page.scrollWidth, rootCW:page.clientWidth};
  })()`);
  log(`报修页 1920: 行数=${rep.rows} 列数=${rep.colCount}`);
  log(`  列宽: ${rep.cols.join(' + ')}`);
  const nums = rep.cols.map((c) => parseFloat(c)).filter((n) => !isNaN(n));
  log(`  求和 = ${nums.reduce((a, b) => a + b, 0)}px （T6 目标 1535px）  操作列实测宽=${rep.opWidth}px`);
  log(`  V-3 表格内部: scrollWidth=${rep.bodySW} clientWidth=${rep.bodyCW} → ${rep.bodySW > rep.bodyCW + 2 ? '有横滚' : '无横滚'}`);
  await shot('T6-04a-报修列表.png', '附带产出：报修列表（列宽收敛后）');

  const pick = await ev(`
    const t=document.cookie.match(/Admin-Token=([^;]+)/)[1];
    const H={Authorization:'Bearer '+t};
    const list=await fetch('/dev-api/laboratory/repair/list?pageNum=1&pageSize=50',{headers:H}).then(r=>r.json());
    const rows=list.rows||[];
    let target=null;
    for(const r of rows){
      const rec=await fetch('/dev-api/laboratory/repair/'+r.repairId+'/records',{headers:H}).then(x=>x.json());
      if((rec.data||[]).length>0){ target={id:r.repairId,code:r.repairCode,records:rec.data.length}; break; }
    }
    if(!target && rows[0]) target={id:rows[0].repairId,code:rows[0].repairCode,records:0};
    if(!target) return 'NO DATA';
    const domRows=[...document.querySelectorAll('.laboratory-page .el-table__row')];
    const idx=domRows.findIndex(tr=>tr.innerText.includes(target.code));
    if(idx<0) return 'row not in DOM: '+target.code;
    const b=[...domRows[idx].querySelectorAll('button')].find(x=>x.innerText.includes('详情'));
    if(!b) return 'no 详情 button';
    b.click();
    return JSON.stringify(target)+' | 该行按钮数='+domRows[idx].querySelectorAll('button').length;
  `);
  log(`报修详情: ${pick}`);
  await waitFor(`document.querySelector('.repair-meta') && document.querySelectorAll('.timeline-section .el-timeline-item').length>0`, '详情摘要条 + 处理时间线', 25000);
  await sleep(1500);
  const detail = await raw(`(()=>{
    const meta=document.querySelector('.repair-meta');
    const items=[...document.querySelectorAll('.timeline-section .el-timeline-item')];
    const dlg=[...document.querySelectorAll('.el-dialog')].find(d=>d.offsetParent!==null);
    const btnCount=[...document.querySelectorAll('.el-table__fixed-right, .el-table-fixed-column--right')].length;
    return {meta: meta?meta.innerText.replace(/\\s+/g,' ').trim():'MISSING',
      timeline:items.length, tlTexts:items.map(i=>i.innerText.replace(/\\s+/g,' ').trim()),
      dlgWidth:dlg?Math.round(dlg.getBoundingClientRect().width):-1,
      dlgMaxW:dlg?getComputedStyle(dlg).maxWidth:'n/a', vw:window.innerWidth, fixedNodes:btnCount};
  })()`);
  log(`详情摘要条: ${detail.meta}`);
  log(`处理时间线 ${detail.timeline} 条 (视口 ${detail.vw} / 弹窗 ${detail.dlgWidth}px / max-width=${detail.dlgMaxW}):`);
  detail.tlTexts.forEach((t, i) => log(`  时间线#${i+1}: ${t}`));
  await shot('T6-04-报修处理时间线.png', '答辩 PPT 第 3 类素材');

  // ================================================================ V-4：1366 窄屏下弹窗宽度是否被收住
  await viewport(1366, 768);
  await sleep(1400);
  const v4 = await raw(`(()=>{
    const dlg=[...document.querySelectorAll('.el-dialog')].find(d=>d.offsetParent!==null);
    if(!dlg) return 'no dialog';
    const r=dlg.getBoundingClientRect();
    const footer=dlg.querySelector('.el-dialog__footer');
    const fr=footer?footer.getBoundingClientRect():null;
    const overlay=document.querySelector('.el-overlay');
    return JSON.stringify({dlgLeft:Math.round(r.left), dlgWidth:Math.round(r.width), maxW:getComputedStyle(dlg).maxWidth,
      vw:window.innerWidth, overflowX: Math.round(r.left)<0 || Math.round(r.right)>window.innerWidth ? 'YES-溢出' : 'NO-在视口内',
      footerBottom: fr?Math.round(fr.bottom):-1, innerH:window.innerHeight,
      overlayScrollable: overlay ? (overlay.scrollHeight > overlay.clientHeight) : null});
  })()`);
  log(`V-4 窄屏弹窗(1366): ${v4}`);
  await shot('T6-V4-1366窄屏弹窗.png', 'V-4 目视：弹窗宽度是否溢出视口');

  // 关掉弹窗，回到干净的列表
  await ev(`
    [...document.querySelectorAll('.el-dialog')].forEach(d=>{const c=d.querySelector('.el-dialog__headerbtn'); if(c) c.click();});
    await new Promise(r=>setTimeout(r,900)); return 1;
  `);

  // ================================================================ V-1：1366 横向滚动后固定列是否跟随
  const v1 = await ev(`
    const wrap=document.querySelector('.laboratory-page .el-table__body-wrapper .el-scrollbar__wrap');
    if(!wrap) return 'no scroll wrap';
    const before=wrap.scrollLeft;
    wrap.scrollLeft=99999;
    await new Promise(r=>setTimeout(r,1000));
    const fixedTds=[...document.querySelectorAll('td.el-table-fixed-column--right')];
    const headerFixed=[...document.querySelectorAll('th.el-table-fixed-column--right')];
    return JSON.stringify({scrollBefore:before, scrollAfter:wrap.scrollLeft, scrollMax:wrap.scrollWidth-wrap.clientWidth,
      fixedCellCount:fixedTds.length, fixedHeaderCount:headerFixed.length,
      position: fixedTds[0]?getComputedStyle(fixedTds[0]).position:'n/a',
      cellLeft: fixedTds[0]?Math.round(fixedTds[0].getBoundingClientRect().left):-1,
      cellRight: fixedTds[0]?Math.round(fixedTds[0].getBoundingClientRect().right):-1,
      headerRight: headerFixed[0]?Math.round(headerFixed[0].getBoundingClientRect().right):-1,
      winW:window.innerWidth, dpr:window.devicePixelRatio});
  `);
  log(`V-1 固定列(1366, 横向滚到底): ${v1}`);
  await sleep(700);
  await shot('T6-V1-1366横向滚动后固定列.png', 'V-1 目视：固定列是否跟随');

  // ================================================================ V-2：折叠搜索区后工具栏行的圆角
  await viewport(1920, 1080);
  await sleep(1000);
  const v2 = await ev(`
    const tb=document.querySelector('.laboratory-page .top-right-btn');
    if(!tb) return 'no .top-right-btn';
    const btn=tb.querySelector('button');   // RightToolbar 第一个按钮 = 显示/隐藏搜索
    if(!btn) return 'no search button';
    const before=document.querySelector('.laboratory-page > .el-form');
    const beforeDisplay=before?getComputedStyle(before).display:'none-el';
    btn.click();
    await new Promise(r=>setTimeout(r,1200));
    const form=document.querySelector('.laboratory-page > .el-form');
    const row=document.querySelector('.laboratory-page > .el-row.mb8');
    const fs=form?getComputedStyle(form):null, rs=getComputedStyle(row);
    return JSON.stringify({beforeDisplay,
      formInDom:!!form, formDisplay: fs?fs.display:'missing', formInline: form?form.getAttribute('style'):'',
      rowBorderTop: rs.borderTopWidth+' / '+rs.borderTopColor,
      rowRadiusTop: rs.borderTopLeftRadius, rowRadiusBottom: rs.borderBottomLeftRadius,
      rowHasTopBorder: rs.borderTopWidth !== '0px'});
  `);
  log(`V-2 折叠态(1920): ${v2}`);
  await sleep(700);
  await shot('T6-V2-搜索区折叠后工具栏圆角.png', 'V-2 目视：折叠后工具栏行是否露直角');

  // 展开回去
  await ev(`document.querySelector('.laboratory-page .top-right-btn button')?.click(); await new Promise(r=>setTimeout(r,900)); return 1;`);
  await sleep(600);
  await shot('T6-V2b-搜索区展开态.png', 'V-2 对照组：展开态');

  if (pageErrors.length) {
    log(`console errors (${pageErrors.length}):`);
    pageErrors.slice(0, 10).forEach((e) => log('  ! ' + e.slice(0, 250)));
  } else log('console errors: none');
  if (failedReq.length) log(`failed requests: ${[...new Set(failedReq)].join(', ')}`);

  log('ALL DONE');
  await cdp.send('Target.closeTarget', { targetId });
  ws.close();
  process.exit(0);
}

main().catch((e) => {
  log('ABORTED: ' + e.stack);
  process.exit(1);
});
