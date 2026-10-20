import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { refreshAccessToken } from '@/api/refresh';
import { tokenStore } from '@/api/tokenStore';

function okResponse(token: string) {
  return new Response(JSON.stringify({ status: 'OK', accessToken: token, expiresIn: 900 }), { status: 200 });
}

describe('refreshAccessToken', () => {
  beforeEach(() => tokenStore.clear());
  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('is single-flight: concurrent callers share one refresh request', async () => {
    let calls = 0;
    vi.stubGlobal('fetch', vi.fn(async () => {
      calls++;
      await new Promise((r) => setTimeout(r, 10));
      return okResponse(`t${calls}`);
    }));
    const results = await Promise.all(Array.from({ length: 5 }, () => refreshAccessToken()));
    expect(results).toEqual([true, true, true, true, true]);
    expect(calls).toBe(1);
    expect(tokenStore.get()).toBe('t1');
  });

  it('sends the client header with credentials so the HttpOnly cookie travels', async () => {
    const fetchMock = vi.fn(async () => okResponse('x'));
    vi.stubGlobal('fetch', fetchMock);
    await refreshAccessToken();
    const [url, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('/api/auth/refresh');
    expect(init.credentials).toBe('include');
    expect((init.headers as Record<string, string>)['X-PTC-Client']).toBe('web');
  });

  it('serialises across tabs through the Web Locks API', async () => {
    const order: string[] = [];
    let held = Promise.resolve();
    const locks = {
      request: vi.fn(<T,>(_name: string, cb: () => Promise<T>) => {
        const run = held.then(() => {
          order.push('acquire');
          return cb().finally(() => order.push('release'));
        });
        held = run.then(() => undefined, () => undefined);
        return run;
      }),
    };
    vi.stubGlobal('navigator', { ...navigator, locks });
    vi.stubGlobal('fetch', vi.fn(async () => okResponse('locked')));
    await refreshAccessToken();
    expect(locks.request).toHaveBeenCalledWith('ptc-token-refresh', expect.any(Function));
    expect(order).toEqual(['acquire', 'release']);
  });

  it('clears the token when the server refuses the refresh', async () => {
    tokenStore.set('old', 900);
    vi.stubGlobal('fetch', vi.fn(async () => new Response('{"code":"auth.session_revoked"}', { status: 401 })));
    expect(await refreshAccessToken()).toBe(false);
    expect(tokenStore.get()).toBeNull();
  });
});
