import { tokenStore } from './tokenStore';

export const CLIENT_HEADER = { 'X-PTC-Client': 'web' } as const;

interface LockManagerLike {
  request<T>(name: string, cb: () => Promise<T>): Promise<T>;
}

let inFlight: Promise<boolean> | null = null;

function locks(): LockManagerLike | undefined {
  const nav = (globalThis as { navigator?: { locks?: LockManagerLike } }).navigator;
  return nav?.locks;
}

/**
 * Exchanges the refresh cookie for a new access token.
 *
 * Single-flight within the tab (concurrent callers share one request) and
 * across tabs (Web Locks): the server rotates the refresh token on every use
 * and treats presentation of an already-consumed token as theft, so two
 * tabs must never race with the same cookie. Inside the lock each tab sends
 * whatever cookie is current, which is the one the previous holder rotated.
 *
 * @returns true when a fresh access token is now held.
 */
export function refreshAccessToken(): Promise<boolean> {
  if (inFlight) return inFlight;
  const run = async (): Promise<boolean> => {
    try {
      const res = await fetch('/api/auth/refresh', {
        method: 'POST',
        credentials: 'include',
        headers: { ...CLIENT_HEADER },
      });
      if (!res.ok) {
        tokenStore.clear();
        return false;
      }
      const body = (await res.json()) as { accessToken: string; expiresIn: number };
      tokenStore.set(body.accessToken, body.expiresIn);
      return true;
    } catch {
      // Network failure: keep any token we have; caller decides.
      return false;
    }
  };
  const lockManager = locks();
  inFlight = (lockManager ? lockManager.request('ptc-token-refresh', run) : run()).finally(() => {
    inFlight = null;
  });
  return inFlight;
}
