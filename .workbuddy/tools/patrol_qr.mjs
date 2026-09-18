/**
 * 巡检补充：资产「标签」二维码弹窗（修正探针）
 * 上一版按 <img> 找二维码 → 报"没有图"，属于**探针误报**：
 * 源码 asset/index.vue:183 是 <div class="qr-svg" v-html="qrInfo.svg">，二维码是**内联 SVG**。
 * 本版按 .qr-card 读取，并顺带把二维码「内容」（= 扫码后拿到的字符串）读出来，坐实 D-01。
 */
import fs from 'node:fs';
import path from 'node:path';
import { LOG_DIR, connect, makeEval, makeLogger, apiLogin, openWithToken, sleep } from './patrol_lib.mjs';

const log = makeLogger('patrol_qr.log');
const { cdp, sessionId, targetId, ws } = await connect();
const { raw, ev, waitFor } = makeEval(cdp, sessionId);

const r = await apiLogin('labadmin', 'admin123');
if (!r.token) {
  log('登录失败：' + JSON.stringify(r));
  process.exit(1);
}
log('登录 labadmin/admin123 → OK');

await openWithToken(cdp, sessionId, r.token, '/laboratory/asset', `document.querySelectorAll('.laboratory-page .el-table__row').length>0`, log, 'asset table');
await sleep(1000);

const firstRow = await raw(`(()=>{const x=document.querySelector('.laboratory-page .el-table__row'); return x? x.innerText.replace(/\\s+/g,' ').slice(0,60):'NONE';})()`);
log('首行文本：' + firstRow);

const clicked = await ev(`const r=[...document.querySelectorAll('.laboratory-page .el-table__row')][0];
  const b=[...r.querySelectorAll('button')].find(x=>x.innerText.includes('标签')); if(!b) return 'NO_BUTTON'; b.click(); return 'CLICKED';`);
log('点「标签」→ ' + clicked);

const ok = await waitFor(`document.querySelector('.qr-card .qr-svg svg')`, 'qr svg', 12000);
await sleep(900);

const qr = await raw(`(()=>{
  const card=document.querySelector('.qr-card');
  const dlg=[...document.querySelectorAll('.el-dialog')].find(d=>d.offsetParent!==null);
  if(!card) return JSON.stringify({qrCard:'未找到', 弹窗标题:(dlg&&dlg.querySelector('.el-dialog__title')||{}).innerText||''});
  const svg=card.querySelector('.qr-svg svg');
  const box=svg?svg.getBoundingClientRect():null;
  return JSON.stringify({
    弹窗标题:(dlg&&dlg.querySelector('.el-dialog__title')||{}).innerText||'',
    有内联SVG: !!svg,
    svg尺寸: box? (Math.round(box.width)+'x'+Math.round(box.height)) : null,
    svg元素数: svg? svg.querySelectorAll('*').length : 0,
    识别文本: (card.querySelector('p')||{}).innerText||'',
    二维码内容: (card.querySelector('span')||{}).innerText||'',
    内容是否相对路径: /^(?!https?:)/.test(((card.querySelector('span')||{}).innerText||'').trim())
  });
})()`);
log('二维码弹窗实测：' + qr);
const o = JSON.parse(qr);
log(o.有内联SVG ? '  → 结论：二维码渲染正常（内联 SVG），上一版探针误报' : '  → 结论：二维码仍未渲染，需继续排查');
log('  → 扫码内容 = ' + JSON.stringify(o.二维码内容) + ' ｜ 相对路径=' + o.内容是否相对路径);

// 存一张证据图
const shot = await cdp.send('Page.captureScreenshot', { format: 'png' }, sessionId);
const outDir = 'D:/code/Manager_system/docs/大作业/AI留痕/截图/T6-核心功能界面';
fs.writeFileSync(path.join(outDir, 'T6-P4-资产二维码标签弹窗.png'), Buffer.from(shot.data, 'base64'));
log('  证据图已存 T6-核心功能界面/T6-P4-资产二维码标签弹窗.png');

fs.writeFileSync(path.join(LOG_DIR, 'patrol_qr.result.json'), qr, 'utf8');
log('PATROL-QR DONE');
await cdp.send('Target.closeTarget', { targetId });
ws.close();
process.exit(0);
