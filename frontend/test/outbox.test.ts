import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { submitQueueable, pending, conflicts, flush, _reset, isQueueable } from '@/lib/outbox';
import { api } from '@/api/client';
import { OfflineError } from '@/api/errors';
import { tokenStore } from '@/api/tokenStore';

function setOnline(value: boolean) {
  Object.defineProperty(navigator, 'onLine', { configurable: true, get: () => value });
}

describe('offline outbox', () => {
  beforeEach(async () => {
    await _reset();
    tokenStore.set('token', 900);
  });
  afterEach(() => {
    setOnline(true);
    vi.unstubAllGlobals();
  });

  it('queues attendance marks and apologies while offline', async () => {
    setOnline(false);
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    expect((await submitQueueable('attendance', 'PUT', '/api/sessions/s/attendance/m', { status: 'PRESENT' }, 'S1')).queued).toBe(true);
    expect((await submitQueueable('apology', 'POST', '/api/sessions/s/apologies', { reason: 'mission' }, 'S1')).queued).toBe(true);
    expect(await pending()).toHaveLength(2);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('refuses to queue legally significant acts', async () => {
    setOnline(false);
    for (const kind of ['vote', 'declaration', 'stageOutcome', 'recommendation']) {
      expect(isQueueable(kind)).toBe(false);
      await expect(submitQueueable(kind, 'POST', '/api/decisions/d/votes', {}, 'x')).rejects.toThrow(/Refusing to queue/);
    }
    expect(await pending()).toHaveLength(0);
  });

  it('blocks votes and declarations offline instead of appearing to record them', async () => {
    setOnline(false);
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    await expect(api.post('/api/decisions/d/votes', { choice: 'FOR' })).rejects.toBeInstanceOf(OfflineError);
    await expect(api.post('/api/agenda-items/a/declarations', { hasInterest: false })).rejects.toBeInstanceOf(OfflineError);
    expect(fetchMock).not.toHaveBeenCalled();
    expect(await pending()).toHaveLength(0);
  });

  it('replays queued writes with their original idempotency key on reconnection', async () => {
    setOnline(false);
    await submitQueueable('attendance', 'PUT', '/api/sessions/s/attendance/m', { status: 'PRESENT' }, 'S1');
    const [entry] = await pending();
    setOnline(true);
    const fetchMock = vi.fn(async () => new Response('{}', { status: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    expect(await flush()).toEqual({ sent: 1, conflicts: 0 });
    const init = (fetchMock.mock.calls[0] as unknown as [string, RequestInit])[1];
    expect((init.headers as Record<string, string>)['Idempotency-Key']).toBe(entry.key);
    expect(await pending()).toHaveLength(0);
  });

  it('parks server conflicts for the officer to resolve', async () => {
    setOnline(false);
    await submitQueueable('attendance', 'PUT', '/api/sessions/s/attendance/m', { status: 'ABSENT_UNJUSTIFIED' }, 'S1');
    setOnline(true);
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({
      code: 'attendance.conflict', message: 'changed', params: { server: { status: 'PRESENT', version: 3 } },
    }), { status: 409 })));
    expect(await flush()).toEqual({ sent: 0, conflicts: 1 });
    const [c] = await conflicts();
    expect(c.code).toBe('attendance.conflict');
    expect(c.server).toEqual({ status: 'PRESENT', version: 3 });
  });
});
