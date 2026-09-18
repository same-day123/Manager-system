/**
 * 演示路径巡检 · 共享库
 *  - CDP 直驱（零依赖），带 Network 状态采集（自动抓 401/403/500）
 *  - 登录：抓验证码 → 从 Redis 直接读答案（免读图）→ POST /login（JSON）
 */
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync, spawn } from 'node:child_process';

export const LOG_DIR = 'D:/code/Manager_system/.workbuddy/logs';
export const REDIS = 'D:/Study_Running/Redis/redis-cli.exe';
export const API = 'http://127.0.0.1:8080';
export const WEB = 'http://127.0.0.1:5173';
export const CDP_HOST = 'http://127.0.0.1:9222';

export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export const rc = (args) => {
  try {
    return execFileSync(REDIS, args, { encoding: 'utf8' }).trim();
  } catch (e) {
    return 'ERR:' + e.message;
  }
};

const MYSQL = 'D:/Study_Running/MySQL/mysql-8.0.34-winx64/bin/mysql.exe';

/** 只读 SQL（-N -B 纯值输出）。用于巡检中的确定性定位与复原比对。 */
export function dbQuery(sql) {
  try {
    return execFileSync(
      MYSQL,
      [
        '--host=127.0.0.1',
        '--port=3306',
        '--user=root',
        '--password=123456',
        '--default-character-set=utf8mb4',
        '--batch',
        '--skip-column-names',
        'education_system',
        '--execute=' + sql,
      ],
      { encoding: 'utf8' }
    ).trim();
  } catch (e) {
    return 'ERR:' + (e.stderr || e.message);
  }
}

/** 写 SQL（巡检复原用，返回受影响行数或 ERR） */
export function dbExec(sql) {
  return dbQuery(sql);
}

/** 抓一个验证码并返回 {uuid, answer}（答案直接取自 Redis） */
export async function freshCaptcha(user, pass) {
  const cap = await fetch(API + '/captchaImage').then((r) => r.json());
  const raw = rc(['get', 'captcha_codes:' + cap.uuid]);
  const answer = raw.replace(/^"|"$/g, '');
  return { uuid: cap.uuid, answer, img: cap.img };
}

/** POST /login（若依收 JSON） */
export async function apiLogin(username, password) {
  const { uuid, answer } = await freshCaptcha();
  const res = await fetch(API + '/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password, code: answer, uuid }),
  });
  const j = await res.json().catch(() => ({}));
  return { http: res.status, code: j.code, msg: j.msg, token: j.token, captcha: answer };
}

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const PROFILE = 'C:/Users/王旻辉/.workbuddy/tmp/ui_chrome_profile';

/** 9222 上的 CDP 是否可用 */
export async function cdpAlive() {
  try {
    const c = new AbortController();
    const t = setTimeout(() => c.abort(), 1500);
    const r = await fetch(CDP_HOST + '/json/version', { signal: c.signal });
    clearTimeout(t);
    return r.ok;
  } catch {
    return false;
  }
}

/**
 * 确保调试浏览器在跑。
 * ⚠️ 不依赖「后台任务」——本项目 bash shim 的 PATH 是坏的，后台任务的退出码处理
 *    会把 Chrome 一起收掉（实测两次）。改为脚本自己 detached+unref 拉起，
 *    Chrome 就不会随父命令退出而被回收。
 */
export async function ensureChrome(log = () => {}) {
  if (await cdpAlive()) {
    log('  Chrome 已在 9222 运行，复用');
    return null;
  }
  log('  9222 不可达 → 由脚本拉起 headless Chrome');
  const args = [
    '--headless=new',
    '--disable-gpu',
    '--no-first-run',
    '--no-default-browser-check',
    '--remote-debugging-port=9222',
    '--remote-allow-origins=*',
    `--user-data-dir=${PROFILE}`,
    '--window-size=1920,1080',
    'about:blank',
  ];
  const p = spawn(CHROME, args, { detached: true, stdio: 'ignore' });
  p.unref();
  for (let i = 0; i < 60; i++) {
    await sleep(500);
    if (await cdpAlive()) {
      log('  Chrome 已就绪（PID ' + p.pid + '，detached，不随父命令退出）');
      return p.pid;
    }
  }
  throw new Error('Chrome 拉起后 9222 仍不可达');
}

/** 关闭调试浏览器（默认不关，留给后续会话复用） */
export function killChrome(pid) {
  if (!pid) return;
  try {
    execFileSync('taskkill', ['/PID', String(pid), '/T', '/F'], { stdio: 'ignore' });
  } catch {}
}

// ------------------------------------------------------------------ CDP
export class CDP {
  constructor(ws) {
    this.ws = ws;
    this.id = 0;
    this.pending = new Map();
    this.events = [];
    this.net = []; // {url, status, method}
    ws.onmessage = (e) => {
      const m = JSON.parse(e.data);
      if (m.id && this.pending.has(m.id)) {
        const { res, rej } = this.pending.get(m.id);
        this.pending.delete(m.id);
        if (m.error) rej(new Error(JSON.stringify(m.error)));
        else res(m.result);
        return;
      }
      this.events.push(m);
      if (m.method === 'Network.responseReceived') {
        const r = m.params.response;
        if (/\/dev-api\//.test(r.url) || /:8080\//.test(r.url)) {
          this.net.push({ url: r.url.replace(API, '').replace(WEB + '/dev-api', ''), status: r.status, type: m.params.type });
        }
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

export async function connect(log = () => {}) {
  await ensureChrome(log);
  const ver = await fetch(CDP_HOST + '/json/version').then((r) => r.json());
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
  await cdp.send('Network.enable', {}, sessionId);
  await cdp.send('Emulation.setDeviceMetricsOverride', { width: 1920, height: 1080, deviceScaleFactor: 1, mobile: false }, sessionId);
  return { cdp, sessionId, targetId, ws };
}

export function makeEval(cdp, sessionId) {
  const raw = async (expr) =>
    (await cdp.send('Runtime.evaluate', { expression: expr, returnByValue: true, userGesture: true }, sessionId)).result?.value;
  const ev = async (body) =>
    (
      await cdp.send(
        'Runtime.evaluate',
        { expression: `(async()=>{${body}})()`, awaitPromise: true, returnByValue: true, userGesture: true },
        sessionId
      )
    ).result?.value;
  const waitFor = async (expr, label, t = 30000) => {
    const t0 = Date.now();
    while (Date.now() - t0 < t) {
      try {
        if (await raw(`(()=>{try{return !!(${expr})}catch(e){return false}})()`)) return true;
      } catch {}
      await sleep(300);
    }
    return false; // 不抛，交给调用方判定
  };
  return { raw, ev, waitFor };
}

/** 造一个写日志的 logger */
export function makeLogger(file) {
  const out = [];
  const log = (m) => {
    out.push(m);
    fs.writeFileSync(path.join(LOG_DIR, file), out.join('\n'), 'utf8');
    console.log(m);
  };
  log.file = file;
  return log;
}

/** 用 token 打开一个前端页面 */
export async function openWithToken(cdp, sessionId, token, route, readyExpr, log, readyLabel = 'page') {
  await cdp.send('Page.navigate', { url: WEB + '/login' }, sessionId);
  const { raw, ev, waitFor } = makeEval(cdp, sessionId);
  await waitFor(`document.querySelector('#app')`, 'app');
  await sleep(500);
  await ev(`document.cookie='Admin-Token=${token}; path=/'; return 1;`);
  await cdp.send('Page.navigate', { url: WEB + route }, sessionId);
  const ok = await waitFor(readyExpr, readyLabel);
  await sleep(1200);
  return { ok, raw, ev, waitFor };
}
