/**
 * Access token holder. The token lives ONLY in this module's memory: it is
 * never written to localStorage, sessionStorage, IndexedDB, cookies or any
 * cache, so it disappears when the page is closed. The refresh token is an
 * HttpOnly cookie the script cannot read.
 */
type Listener = (authenticated: boolean) => void;

let accessToken: string | null = null;
let expiresAtMs = 0;
const listeners = new Set<Listener>();

export const tokenStore = {
  get(): string | null {
    return accessToken;
  },
  /** True when a token is held and not within 30 s of expiry. */
  isFresh(now = Date.now()): boolean {
    return accessToken !== null && now < expiresAtMs - 30_000;
  },
  set(token: string, expiresInSeconds: number, now = Date.now()): void {
    accessToken = token;
    expiresAtMs = now + expiresInSeconds * 1000;
    listeners.forEach((l) => l(true));
  },
  clear(): void {
    const had = accessToken !== null;
    accessToken = null;
    expiresAtMs = 0;
    if (had) listeners.forEach((l) => l(false));
  },
  subscribe(listener: Listener): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};
