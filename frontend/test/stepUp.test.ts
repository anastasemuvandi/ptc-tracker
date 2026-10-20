import { describe, it, expect, vi, afterEach } from 'vitest';
import { api, setStepUpHandler } from '@/api/client';
import { tokenStore } from '@/api/tokenStore';

describe('step-up retry', () => {
  afterEach(() => {
    setStepUpHandler(null);
    vi.unstubAllGlobals();
  });

  it('retries the act after step-up with the same Idempotency-Key', async () => {
    tokenStore.set('token', 900);
    const keys: string[] = [];
    let call = 0;
    vi.stubGlobal('fetch', vi.fn(async (_url: string, init: RequestInit) => {
      keys.push((init.headers as Record<string, string>)['Idempotency-Key']);
      call++;
      if (call === 1) {
        return new Response(JSON.stringify({ code: 'auth.step_up_required', message: 'confirm', params: { act: 'VOTE' } }), { status: 401 });
      }
      return new Response(JSON.stringify({ choice: 'FOR' }), { status: 200 });
    }));
    const handler = vi.fn(async () => true);
    setStepUpHandler(handler);
    const result = await api.post<{ choice: string }>('/api/decisions/d/votes', { choice: 'FOR' }, { idempotencyKey: 'f5b1c2a8-0000-4000-8000-000000000001' });
    expect(result.choice).toBe('FOR');
    expect(handler).toHaveBeenCalledOnce();
    expect(handler).toHaveBeenCalledWith({ act: 'VOTE' });
    expect(keys).toEqual(['f5b1c2a8-0000-4000-8000-000000000001', 'f5b1c2a8-0000-4000-8000-000000000001']);
  });

  it('generates one key per logical action and reuses it on the expired-token retry', async () => {
    tokenStore.set('token', 900);
    const keys: string[] = [];
    let call = 0;
    vi.stubGlobal('fetch', vi.fn(async (url: string, init: RequestInit) => {
      if (url === '/api/auth/refresh') return new Response(JSON.stringify({ accessToken: 'new', expiresIn: 900 }), { status: 200 });
      keys.push((init.headers as Record<string, string>)['Idempotency-Key']);
      call++;
      if (call === 1) return new Response(JSON.stringify({ code: 'auth.invalid_token', message: 'expired', params: {} }), { status: 401 });
      return new Response('{}', { status: 200 });
    }));
    await api.put('/api/me', { phone: '1' });
    expect(keys).toHaveLength(2);
    expect(keys[0]).toMatch(/^[0-9a-f-]{36}$/);
    expect(keys[0]).toBe(keys[1]);
  });

  it('does not report success when the user cancels step-up', async () => {
    tokenStore.set('token', 900);
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ code: 'auth.step_up_required', message: 'x', params: {} }), { status: 401 })));
    setStepUpHandler(async () => false);
    await expect(api.post('/api/agenda-items/a/declarations', { hasInterest: false })).rejects.toMatchObject({ code: 'auth.step_up_required' });
  });
});
