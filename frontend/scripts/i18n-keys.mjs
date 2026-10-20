// Lists translation keys used in src/ (static) and dynamic key prefixes,
// and reports keys missing from each locale. Usage: node scripts/i18n-keys.mjs [--check]
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

function walk(dir, out = []) {
  for (const f of readdirSync(dir)) {
    const p = join(dir, f);
    if (statSync(p).isDirectory()) walk(p, out);
    else if (/\.(tsx?|ts)$/.test(f)) out.push(p);
  }
  return out;
}
const statics = new Set();
const dynamics = new Set();
for (const file of walk('src')) {
  const src = readFileSync(file, 'utf8');
  for (const m of src.matchAll(/\bt\(\s*'([^']+)'/g)) statics.add(m[1]);
  for (const m of src.matchAll(/\bt\(\s*`([^`]+)`/g)) dynamics.add(m[1].replace(/\$\{[^}]+\}/g, '*'));
}
function flatten(obj, prefix = '', out = {}) {
  for (const [k, v] of Object.entries(obj)) {
    const key = prefix ? `${prefix}.${k}` : k;
    if (v && typeof v === 'object') flatten(v, key, out);
    else out[key] = v;
  }
  return out;
}
const check = process.argv.includes('--check');
let missingTotal = 0;
const locales = ['en', 'fr', 'rw'];
const flat = Object.fromEntries(locales.map((l) => [l, flatten(JSON.parse(readFileSync(`src/i18n/locales/${l}.json`, 'utf8')))]));
const has = (l, key) => key in flat[l] || `${key}_one` in flat[l] || `${key}_other` in flat[l];
for (const l of locales) {
  const missing = [...statics].filter((k) => !has(l, k)).sort();
  if (missing.length) { missingTotal += missing.length; console.log(`[${l}] missing ${missing.length}:`, check ? missing.slice(0, 400).join('\n  ') : missing.length); }
  const enKeys = Object.keys(flat.en);
  const notInL = enKeys.filter((k) => !(k in flat[l]));
  if (notInL.length) { missingTotal += notInL.length; console.log(`[${l}] keys present in en but not here: ${notInL.length}`, check ? notInL.slice(0, 200).join(', ') : ''); }
}
if (!check) {
  console.log('STATIC', [...statics].sort().join('\n'));
  console.log('DYNAMIC', [...dynamics].sort().join('\n'));
}
process.exit(check && missingTotal ? 1 : 0);
