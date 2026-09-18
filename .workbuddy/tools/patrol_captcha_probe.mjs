/**
 * 探针：验证「抓验证码 → 从 Redis 直接读答案」这条免读图登录路径是否可行。
 * 若可行，整个演示路径巡检就能全自动化（不必逐张人工读验证码图）。
 */
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';

const LOG = 'D:/code/Manager_system/.workbuddy/logs';
const REDIS = 'D:/Study_Running/Redis/redis-cli.exe';
const BASE = 'http://127.0.0.1:8080';

const out = [];
const log = (m) => { out.push(m); fs.writeFileSync(path.join(LOG, 'patrol_captcha_probe.log'), out.join('\n'), 'utf8'); };
const rc = (args) => { try { return execFileSync(REDIS, args, { encoding: 'utf8' }).trim(); } catch (e) { return 'ERR:' + e.message; } };

log('=== 1) 抓一个验证码 ===');
const cap = await fetch(BASE + '/captchaImage').then((r) => r.json());
log('  code=' + cap.code + ' captchaEnabled=' + cap.captchaEnabled + ' uuid=' + cap.uuid);
log('  img 前缀=' + String(cap.img).slice(0, 30) + '  长度=' + String(cap.img).length);

log('');
log('=== 2) Redis 里现在有哪些 captcha 相关 key ===');
const keys = rc(['keys', '*captcha*']);
log('  keys *captcha* → ' + JSON.stringify(keys));

log('');
log('=== 3) 猜 key 名并取值 ===');
const candidates = [
  'captcha_codes:' + cap.uuid,
  'captcha_codes:' + cap.uuid.toUpperCase(),
  'captcha_codes' + cap.uuid,
];
for (const k of candidates) {
  const v = rc(['get', k]);
  log(`  get ${k} → ${JSON.stringify(v)}`);
}

log('');
log('=== 4) 全库 key 抽样（看命名风格） ===');
log('  dbsize = ' + rc(['dbsize']));
log('  keys * (前 40) = ' + JSON.stringify(rc(['keys', '*']).split('\n').slice(0, 40)));

log('');
log('=== 5) 用读到的答案直接登录 ===');
let answer = null;
for (const k of candidates) {
  const v = rc(['get', k]);
  if (v && !v.startsWith('ERR:') && v !== '') { answer = v; break; }
}
if (answer == null) {
  log('  ✗ 未从 Redis 读到答案 → 该路径不可行，回退到「读图」方式');
} else {
  log('  ✓ 读到答案 = ' + answer);
  const body = new URLSearchParams({ username: 'labadmin', password: 'admin123', code: answer, uuid: cap.uuid });
  const res = await fetch(BASE + '/login', { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body });
  const j = await res.json();
  log('  POST /login → HTTP ' + res.status + ' code=' + j.code + ' msg=' + j.msg);
  if (j.token) {
    fs.writeFileSync(path.join(LOG, 'token_labadmin.txt'), j.token, 'utf8');
    log('  ✓ 登录成功，token 已存 token_labadmin.txt（长度 ' + j.token.length + '）');
  }
  // 保存一张图备用（万一以后要人工读）
  if (cap.img && cap.img.startsWith('data:image')) {
    fs.writeFileSync(path.join(LOG, 'captcha_last.png'), Buffer.from(cap.img.split(',')[1], 'base64'));
    log('  验证码图已存 captcha_last.png（备用）');
  }
}
log('');
log('PROBE DONE');
process.exit(0);
