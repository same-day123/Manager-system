/**
 * 重拍 T6-01 首页运维看板（UI设计 第三轮）
 *
 * 背景：D-24/D-25 演示数据校准已于 9-18 在真实库执行，旧 T6-01 图是「说谎的证据」
 *   （维修中 5 / 待审核 0 + 两张空图）→ 重拍。新数据应为：
 *   维修中 2 / 待审核 1 / 已完成 3，repairCostTrend 5 天（09-16 含 ¥120）、repairTrend 5 天有柱。
 *
 * 账号：admin（演示口令已定 admin123，Q-15 走 A）。
 * 取景：1920×1080、admin 登录、无弹窗遮挡、4 张 canvas 渲染完成。
 */
import fs from 'node:fs';
import path from 'node:path';
import { apiLogin, connect, makeEval, makeLogger, sleep, LOG_DIR } from './patrol_lib.mjs';

const OUT = 'D:/code/Manager_system/docs/大作业/AI留痕/截图/T6-核心功能界面';
const OUT_FILE = 'T6-01-首页运维看板.png';
const log = makeLogger('reshoot_t6_01.log');

async function main() {
  // 1. 登录（admin / admin123）
  const login = await apiLogin('admin', 'admin123');
  log(`login admin: http=${login.http} code=${login.code} msg=${login.msg}`);
  if (login.code !== 200 || !login.token) {
    log('ABORT: 登录失败，无 token');
    process.exit(1);
  }

  // 2. 起 Chrome + CDP
  const { cdp, sessionId, targetId, ws } = await connect(log);
  const { raw, ev, waitFor } = makeEval(cdp, sessionId);

  // 3. 注入 token 并打开首页
  await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/login' }, sessionId);
  await waitFor(`document.querySelector('#app')`, '登录页 #app', 40000);
  await sleep(600);
  await ev(`document.cookie='Admin-Token=${login.token}; path=/'; return 1;`);
  await cdp.send('Page.navigate', { url: 'http://127.0.0.1:5173/index' }, sessionId);

  // 4. 等首页看板渲染
  const okDash = await waitFor(`document.querySelector('.lab-dashboard')`, '首页 .lab-dashboard', 40000);
  if (!okDash) {
    log('ABORT: .lab-dashboard 未出现（admin 也应可见看板）');
    process.exit(1);
  }

  // 关掉可能的「初始密码」消息框
  await ev(`
    const boxes=[...document.querySelectorAll('.el-message-box')].filter(b=>b.offsetParent!==null);
    if(boxes.length){const btns=[...boxes[0].querySelectorAll('.el-message-box__btns button')];
      const c=btns.find(b=>b.innerText.includes('取消'))||btns[0]; c&&c.click();}
    return boxes.length;
  `);

  const okCanvas = await waitFor(`document.querySelectorAll('.chart-grid canvas').length >= 4`, '4 张 canvas', 40000);
  log(`canvas >= 4 : ${okCanvas}`);
  await sleep(2800); // 等 ECharts 动画收尾

  // 5. 读出画面上的真实数字（验收关键：不能是旧数据）
  const dash = await raw(`(()=>{
    const cards=[...document.querySelectorAll('.metric-card')];
    const canv=[...document.querySelectorAll('.chart-grid canvas')];
    const root=document.querySelector('.lab-dashboard');
    return {metrics: cards.map(c=>c.innerText.replace(/\\s+/g,' ').trim()),
            canvas: canv.length,
            canvasSizes: canv.map(c=>c.width+'x'+c.height),
            fallback: !!document.body.innerText.includes('暂没有运维工作台'),
            rootSW: root.scrollWidth, rootCW: root.clientWidth};
  })()`);
  log(`指标卡 ${dash.metrics.length} 张 / canvas ${dash.canvas} 个 (${dash.canvasSizes.join(', ')})`);
  dash.metrics.forEach((m, i) => log(`  指标#${i+1}: ${m}`));
  log(`降级提示=${dash.fallback}  横向滚动=${dash.rootSW > dash.rootCW ? '有' : '无'}`);

  // 6. 截图
  const { data } = await cdp.send('Page.captureScreenshot', { format: 'png' }, sessionId);
  fs.mkdirSync(OUT, { recursive: true });
  const p = path.join(OUT, OUT_FILE);
  fs.writeFileSync(p, Buffer.from(data, 'base64'));
  log(`SHOT ${OUT_FILE} (${(fs.statSync(p).size / 1024).toFixed(1)} KB) -> ${p}`);

  log('DONE');
  await cdp.send('Target.closeTarget', { targetId });
  ws.close();
  process.exit(0);
}

process.on('unhandledRejection', (e) => {
  log('UNHANDLED REJECTION: ' + (e && e.stack ? e.stack : e));
  process.exit(9);
});
process.on('uncaughtException', (e) => {
  log('UNCAUGHT: ' + (e && e.stack ? e.stack : e));
  process.exit(9);
});

main().catch((e) => {
  log('ABORTED: ' + (e && e.stack ? e.stack : e));
  process.exit(1);
});
