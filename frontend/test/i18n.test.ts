import { describe, it, expect } from 'vitest';
import en from '@/i18n/locales/en.json';
import fr from '@/i18n/locales/fr.json';
import rw from '@/i18n/locales/rw.json';

function flatten(obj: Record<string, unknown>, prefix = ''): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(obj)) {
    const key = prefix ? `${prefix}.${k}` : k;
    if (v && typeof v === 'object') Object.assign(out, flatten(v as Record<string, unknown>, key));
    else out[key] = String(v);
  }
  return out;
}

describe('locales', () => {
  const E = flatten(en);
  it.each([['fr', flatten(fr)], ['rw', flatten(rw)]])('%s has every English key, translated', (_l, L) => {
    expect(Object.keys(L).sort()).toEqual(Object.keys(E).sort());
    for (const [k, v] of Object.entries(L)) expect(v.trim(), k).not.toBe('');
  });
});
