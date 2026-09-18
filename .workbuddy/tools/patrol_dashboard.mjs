/** 取看板接口的真实数字（答辩开场画面上的数字） */
import { apiLogin } from './patrol_lib.mjs';

const login = await apiLogin('labadmin', 'admin123');
const h = { Authorization: 'Bearer ' + login.token };
const s = await fetch('http://127.0.0.1:8080/laboratory/dashboard/summary', { headers: h }).then((r) => r.json());
const c = await fetch('http://127.0.0.1:8080/laboratory/dashboard/charts', { headers: h }).then((r) => r.json());
const out = [];
out.push('=== /laboratory/dashboard/summary ===');
out.push(JSON.stringify(s.data, null, 2));
out.push('');
out.push('=== /laboratory/dashboard/charts 结构 ===');
const d = c.data || {};
for (const k of Object.keys(d)) {
  out.push(`  ${k}: ${Array.isArray(d[k]) ? '数组(' + d[k].length + ') ' + JSON.stringify(d[k]).slice(0, 220) : JSON.stringify(d[k])}`);
}
const fs = await import('node:fs');
fs.writeFileSync('D:/code/Manager_system/.workbuddy/logs/dashboard_numbers.txt', out.join('\n'), 'utf8');
console.log(out.join('\n'));
process.exit(0);
