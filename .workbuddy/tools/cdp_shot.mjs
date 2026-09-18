/**
 * 极简 CDP 浏览器驱动 —— 用于给本项目的前端页面截图 / 做目视验收。
 *
 * 为什么不用 playwright / puppeteer：
 *   本机是离线环境，npm 装不上任何依赖（项目 AGENT 记录里也明确禁止新增依赖）。
 *   Node 22 自带全局 WebSocket + fetch，足以直接对话 Chrome DevTools Protocol。
 *
 * 用法：
 *   1) 启动浏览器（另开一个进程）：
 *      chrome.exe --headless=new --remote-debugging-port=9222 --remote-allow-origins=*
 *                --user-data-dir=<临时目录> --window-size=1920,1080 about:blank
 *   2) node cdp_shot.mjs <steps.json> <logFile>
 *
 * steps.json 是一个数组，每项支持：
 *   { "goto": "http://localhost/index", "waitFor": "表达式" }
 *   { "waitFor": "表达式", "timeout": 20000 }
 *   { "eval": "表达式", "save": "名字" }      // 结果会写进日志
 *   { "sleep": 1000 }
 *   { "viewport": [1920, 1080] }
 *   { "shot": "绝对路径.png", "fullPage": true|false }
 *   { "note": "说明文字" }
 */

import fs from 'node:fs';

const DEBUG_PORT = process.env.CDP_PORT || 9222;
const stepsFile = process.argv[2];
const logFile = process.argv[3] || 'cdp_run.log';

const logs = [];
function log(msg) {
  const line = `[${new Date().toISOString().slice(11, 19)}] ${msg}`;
  logs.push(line);
  fs.writeFileSync(logFile, logs.join('\n'), 'utf8');
}

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
        if (m.error) rej(new Error(`${m.method || ''} ${JSON.stringify(m.error)}`));
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

async function main() {
  const steps = JSON.parse(fs.readFileSync(stepsFile, 'utf8'));

  // 1. 找到浏览器
  const ver = await fetch(`http://127.0.0.1:${DEBUG_PORT}/json/version`).then((r) => r.json());
  log(`connected: ${ver.Browser}`);

  const ws = new WebSocket(ver.webSocketDebuggerUrl);
  await new Promise((res, rej) => {
    ws.onopen = res;
    ws.onerror = () => rej(new Error('WS connect failed'));
  });
  const cdp = new CDP(ws);

  // 2. 新开一个标签页
  const { targetId } = await cdp.send('Target.createTarget', { url: 'about:blank' });
  const { sessionId } = await cdp.send('Target.attachToTarget', { targetId, flatten: true });
  await cdp.send('Page.enable', {}, sessionId);
  await cdp.send('Runtime.enable', {}, sessionId);

  // 控制台报错收集（有帮助于发现前端异常）
  const pageErrors = [];
  cdp.handlers.set('Runtime.consoleAPICalled', []);
  const onMsg = (p) => {
    if (p.type === 'error') {
      pageErrors.push((p.args || []).map((a) => a.value ?? a.description ?? '').join(' '));
    }
  };
  cdp.handlers.get('Runtime.consoleAPICalled').push(onMsg);

  const evaluate = async (expr) => {
    const r = await cdp.send(
      'Runtime.evaluate',
      { expression: expr, awaitPromise: true, returnByValue: true, userGesture: true },
      sessionId
    );
    if (r.exceptionDetails) {
      throw new Error('eval error: ' + (r.exceptionDetails.exception?.description || r.exceptionDetails.text));
    }
    return r.result?.value;
  };

  const saved = {};

  for (let i = 0; i < steps.length; i++) {
    const s = steps[i];
    try {
      if (s.note) log(`#${i} note: ${s.note}`);

      if (s.viewport) {
        await cdp.send(
          'Emulation.setDeviceMetricsOverride',
          {
            width: s.viewport[0],
            height: s.viewport[1],
            deviceScaleFactor: s.deviceScaleFactor || 1,
            mobile: false,
          },
          sessionId
        );
        log(`#${i} viewport -> ${s.viewport[0]}x${s.viewport[1]} dsf=${s.deviceScaleFactor || 1}`);
      }

      if (s.goto) {
        await cdp.send('Page.navigate', { url: s.goto }, sessionId);
        log(`#${i} goto ${s.goto}`);
      }

      if (s.sleep) {
        await new Promise((r) => setTimeout(r, s.sleep));
      }

      if (s.waitFor) {
        const timeout = s.timeout || 25000;
        const t0 = Date.now();
        let ok = false;
        while (Date.now() - t0 < timeout) {
          try {
            if (await evaluate(`(() => { try { return !!(${s.waitFor}); } catch(e) { return false; } })()`)) {
              ok = true;
              break;
            }
          } catch {
            /* 页面跳转中途 evaluate 可能失败，忽略继续等 */
          }
          await new Promise((r) => setTimeout(r, 400));
        }
        log(`#${i} waitFor ${ok ? 'OK' : 'TIMEOUT'} (${Date.now() - t0}ms): ${s.waitFor.slice(0, 90)}`);
        if (!ok) throw new Error('waitFor timeout: ' + s.waitFor.slice(0, 120));
      }

      if (s.eval) {
        const v = await evaluate(s.eval);
        const name = s.save || `eval${i}`;
        saved[name] = v;
        log(`#${i} eval[${name}] = ${typeof v === 'object' ? JSON.stringify(v) : v}`);
      }

      if (s.shot) {
        let params = { format: 'png', captureBeyondViewport: !!s.fullPage };
        if (s.fullPage) {
          const m = await cdp.send('Page.getLayoutMetrics', {}, sessionId);
          const cs = m.cssContentSize || m.contentSize;
          params.clip = { x: 0, y: 0, width: cs.width, height: cs.height, scale: 1 };
        }
        const { data } = await cdp.send('Page.captureScreenshot', params, sessionId);
        fs.mkdirSync(s.shot.replace(/[\\/][^\\/]*$/, ''), { recursive: true });
        fs.writeFileSync(s.shot, Buffer.from(data, 'base64'));
        log(`#${i} shot -> ${s.shot} (${(fs.statSync(s.shot).size / 1024).toFixed(1)} KB)`);
      }
    } catch (e) {
      log(`#${i} FAILED: ${e.message}`);
      throw e;
    }
  }

  if (pageErrors.length) {
    log(`console errors (${pageErrors.length}):`);
    pageErrors.slice(0, 15).forEach((e) => log('  ! ' + e.slice(0, 300)));
  } else {
    log('console errors: none');
  }

  log('DONE');
  await cdp.send('Target.closeTarget', { targetId });
  ws.close();
  process.exit(0);
}

main().catch((e) => {
  log('ABORTED: ' + e.stack);
  process.exit(1);
});
