import { describe, it, expect, vi, afterEach } from 'vitest';
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { tokenStore } from '@/api/tokenStore';

describe('tokenStore', () => {
  afterEach(() => {
    tokenStore.clear();
    vi.restoreAllMocks();
  });

  it('holds the access token in memory and reports freshness', () => {
    const now = 1_000_000;
    tokenStore.set('abc', 900, now);
    expect(tokenStore.get()).toBe('abc');
    expect(tokenStore.isFresh(now + 60_000)).toBe(true);
    expect(tokenStore.isFresh(now + 880_000)).toBe(false); // inside the 30 s margin
    tokenStore.clear();
    expect(tokenStore.get()).toBeNull();
  });

  it('never touches web storage or IndexedDB', () => {
    const ls = vi.spyOn(Storage.prototype, 'setItem');
    const idb = vi.spyOn(indexedDB, 'open');
    tokenStore.set('secret-token', 900);
    tokenStore.get();
    tokenStore.clear();
    expect(ls).not.toHaveBeenCalled();
    expect(idb).not.toHaveBeenCalled();
  });

  it('auth modules contain no persistent-storage API at all', () => {
    const dir = join(__dirname, '..', 'src', 'api');
    for (const f of readdirSync(dir)) {
      // Code only: the doc comments deliberately name what is NOT used.
      const src = readFileSync(join(dir, f), 'utf8').replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/.*$/gm, '');
      expect(src, f).not.toMatch(/localStorage|sessionStorage|indexedDB|caches\./);
    }
  });

  it('notifies listeners when the session ends', () => {
    const listener = vi.fn();
    const unsubscribe = tokenStore.subscribe(listener);
    tokenStore.set('t', 900);
    tokenStore.clear();
    expect(listener).toHaveBeenLastCalledWith(false);
    unsubscribe();
  });
});
