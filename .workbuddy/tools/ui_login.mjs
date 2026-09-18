/**
 * 登录辅助脚本（Node 直连后端，不走浏览器，因此没有跨域问题）。
 *
 * 用途：本机若依的验证码开关是开着的（sys_config: sys.account.captchaEnabled = true），
 * 所以自动登录必须先把验证码图片取出来、由人看一眼再回填。
 * 本脚本把这一步拆成两个命令，方便中间人工插入：
 *
 *   node ui_login.mjs captcha             # 取验证码图片 + uuid，写入 logs/captcha.png、logs/captcha_uuid.txt
 *   node ui_login.mjs login <验证码>       # 用上面那个 uuid 登录，token 写入 logs/token.txt
 *
 * 注意：验证码有效期约 2 分钟，取完要尽快回填。
 */

import fs from 'node:fs';
import path from 'node:path';

const BASE = process.env.API_BASE || 'http://127.0.0.1:8080';
const USER = process.env.LOGIN_USER || 'labadmin';
const PASS = process.env.LOGIN_PASS || '123456';
const LOG_DIR = process.env.LOG_DIR || 'D:/code/Manager_system/.workbuddy/logs';

const mode = process.argv[2];
const arg = process.argv[3];

function out(s) {
  fs.appendFileSync(path.join(LOG_DIR, 'ui_login.log'), s + '\n', 'utf8');
  console.log(s);
}

async function getCaptcha() {
  const r = await fetch(`${BASE}/captchaImage`).then((r) => r.json());
  fs.mkdirSync(LOG_DIR, { recursive: true });
  out(`captchaImage -> code=${r.code} captchaEnabled=${r.captchaEnabled} imgLen=${(r.img || '').length} uuid=${r.uuid}`);
  if (!r.captchaEnabled) {
    fs.writeFileSync(path.join(LOG_DIR, 'captcha_uuid.txt'), r.uuid || '', 'utf8');
    out('captcha disabled (无需人工识别)');
    return;
  }
  const b64 = String(r.img).replace(/^data:image\/\w+;base64,/, '');
  fs.writeFileSync(path.join(LOG_DIR, 'captcha.png'), Buffer.from(b64, 'base64'));
  fs.writeFileSync(path.join(LOG_DIR, 'captcha_uuid.txt'), r.uuid, 'utf8');
  out(`captcha.png written (${fs.statSync(path.join(LOG_DIR, 'captcha.png')).size} bytes), uuid=${r.uuid}`);
}

async function login(code) {
  const uuid = fs.readFileSync(path.join(LOG_DIR, 'captcha_uuid.txt'), 'utf8').trim();
  const res = await fetch(`${BASE}/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: USER, password: PASS, code, uuid }),
  });
  const j = await res.json();
  if (j.code === 200 && j.token) {
    fs.writeFileSync(path.join(LOG_DIR, 'token.txt'), j.token, 'utf8');
    out(`LOGIN OK user=${USER} tokenLen=${j.token.length}`);
    const roles = await fetch(`${BASE}/getInfo`, {
      headers: { Authorization: 'Bearer ' + j.token },
    }).then((r) => r.json());
    out(`getInfo -> code=${codes(roles)} user=${JSON.stringify(roles.user)} roles=${JSON.stringify(roles.roles)}`);
  } else {
    out(`LOGIN FAILED -> ${JSON.stringify(j)}`);
    process.exitCode = 2;
  }
}

function codes(o) {
  return o && o.code;
}

if (mode === 'captcha') await getCaptcha();
else if (mode === 'login') await login(arg);
else out('usage: node ui_login.mjs captcha | login <code>');
