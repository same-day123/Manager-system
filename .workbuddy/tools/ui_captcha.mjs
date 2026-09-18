/**
 * 把 logs/captcha.png（实为 JPEG 字节）在浏览器里放大若干倍后截图，
 * 便于人眼/多模态读图识别验证码。
 *
 * 为什么不用 sharp/jimp：本机离线，npm 装不上图像库。
 * 借用已经以 --remote-debugging-port=9222 启动的 Chrome，用 CDP 渲染 + 截图即可。
 *
 * 用法：node ui_captcha.mjs [放大宽度，默认 900]
 */
import fs from 'node:fs';
import path from 'node:path';

const WIDTH = Number(process.argv[2] || 900);
const LOG_DIR = 'D:/code/Manager_system/.workbuddy/logs';
const src = path.join(LOG_DIR, 'captcha.png');
const out = path.join(LOG_DIR, 'captcha_zoom.png');

const b64 = fs.readFileSync(src).toString('base64');
const dataUrl = `data:image/jpeg;base64,${b64}`;

const ver = await fetch('http://127.0.0.1:9222/json/version').then((r) => r.json());
const ws = new WebSocket(ver.webSocketDebuggerUrl);
await new Promise((res, rej) => {
  ws.onopen = res;
  ws.onerror = () => rej(new Error('ws fail'));
});

let id = 0;
const pending = new Map();
ws.onmessage = (e) => {
  const m = JSON.parse(e.data);
  if (m.id && pending.has(m.id)) {
    const { res, rej } = pending.get(m.id);
    pending.delete(m.id);
    if (m.error) rej(new Error(JSON.stringify(m.error)));
    else res(m.result);
  }
};
const send = (method, params = {}, sessionId) =>
  new Promise((res, rej) => {
    const i = ++id;
    pending.set(i, { res, rej });
    ws.send(JSON.stringify({ id: i, method, params, ...(sessionId ? { sessionId } : {}) }));
  });

const { targetId } = await send('Target.createTarget', { url: 'about:blank' });
const { sessionId } = await send('Target.attachToTarget', { targetId, flatten: true });
await send('Page.enable', {}, sessionId);
await send('Runtime.enable', {}, sessionId);
await send('Emulation.setDeviceMetricsOverride', { width: WIDTH + 40, height: 400, deviceScaleFactor: 1, mobile: false }, sessionId);

const html = `<body style="margin:0;background:#fff"><img id="c" src="${dataUrl}" style="width:${WIDTH}px;display:block;image-rendering:auto"></body>`;
await send('Runtime.evaluate', { expression: `document.open();document.write(${JSON.stringify(html)});document.close();` }, sessionId);
await new Promise((r) => setTimeout(r, 800));

const rect = (
  await send(
    'Runtime.evaluate',
    { expression: `(()=>{const r=document.getElementById('c').getBoundingClientRect();return {w:Math.round(r.width),h:Math.round(r.height)};})()`, returnByValue: true },
    sessionId
  )
).result.value;

const { data } = await send('Page.captureScreenshot', { format: 'png', clip: { x: 0, y: 0, width: rect.w, height: rect.h, scale: 1 } }, sessionId);
fs.writeFileSync(out, Buffer.from(data, 'base64'));
console.log(`zoom ${WIDTH}px -> ${out} (${rect.w}x${rect.h}, ${(fs.statSync(out).size / 1024).toFixed(1)} KB)`);

await send('Target.closeTarget', { targetId });
ws.close();
process.exit(0);
