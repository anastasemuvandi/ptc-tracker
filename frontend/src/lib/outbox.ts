import { openDB, type IDBPDatabase } from 'idb';
import { send, newIdempotencyKey, isOnline } from '@/api/client';
import { ApiError } from '@/api/errors';

/**
 * Offline outbox. ONLY attendance marks and absence apologies may be queued
 * while offline. Legally significant acts (votes, conflict-of-interest
 * declarations, evaluation stage outcomes, recommendations) are refused by
 * the API client when offline and can never enter this queue.
 *
 * Stored: the request path, body and its idempotency key (so a replay after
 * reconnection cannot duplicate the record). Never tokens.
 */
export const QUEUEABLE_KINDS = ['attendance', 'apology'] as const;
export type QueueableKind = (typeof QUEUEABLE_KINDS)[number];

export interface OutboxEntry {
  key: string;
  kind: QueueableKind;
  method: 'PUT' | 'POST';
  path: string;
  body: Record<string, unknown>;
  label: string;
  createdAt: string;
}

export interface OutboxConflict extends OutboxEntry {
  code: string;
  server: unknown;
}

const DB = 'ptc-outbox';
let dbPromise: Promise<IDBPDatabase> | null = null;
function db() {
  dbPromise ??= openDB(DB, 1, {
    upgrade(d) {
      d.createObjectStore('outbox', { keyPath: 'key' });
      d.createObjectStore('conflicts', { keyPath: 'key' });
    },
  });
  return dbPromise;
}

type Listener = () => void;
const listeners = new Set<Listener>();
export function onOutboxChange(l: Listener): () => void {
  listeners.add(l);
  return () => listeners.delete(l);
}
const notify = () => listeners.forEach((l) => l());

export function isQueueable(kind: string): kind is QueueableKind {
  return (QUEUEABLE_KINDS as readonly string[]).includes(kind);
}

/**
 * Sends now if online, otherwise queues. Throws for any kind other than
 * attendance or apology — those must be confirmed by the server.
 */
export async function submitQueueable(
  kind: string, method: 'PUT' | 'POST', path: string, body: Record<string, unknown>, label: string,
): Promise<{ queued: boolean; response?: unknown }> {
  if (!isQueueable(kind)) {
    throw new Error(`Refusing to queue "${kind}": only attendance and apologies may be recorded offline`);
  }
  const entry: OutboxEntry = { key: newIdempotencyKey(), kind, method, path, body, label, createdAt: new Date().toISOString() };
  if (isOnline()) {
    try {
      const res = await send(method, path, { body, idempotencyKey: entry.key });
      const text = await res.text();
      return { queued: false, response: text ? JSON.parse(text) : undefined };
    } catch (e) {
      if (e instanceof ApiError) throw e; // real server answer: not an offline case
      // network dropped mid-request: fall through and queue with the same key
    }
  }
  await (await db()).put('outbox', entry);
  notify();
  return { queued: true };
}

export async function pending(): Promise<OutboxEntry[]> {
  return (await db()).getAll('outbox');
}

export async function conflicts(): Promise<OutboxConflict[]> {
  return (await db()).getAll('conflicts');
}

export async function discardConflict(key: string): Promise<void> {
  await (await db()).delete('conflicts', key);
  notify();
}

/** Re-sends a conflicting mark, deliberately overriding the server value. */
export async function overrideConflict(c: OutboxConflict, baseVersion: number | undefined): Promise<void> {
  await send(c.method, c.path, { body: { ...c.body, baseVersion }, idempotencyKey: newIdempotencyKey() });
  await discardConflict(c.key);
}

let flushing = false;
/** Replays queued writes in order; stops at the first network failure. */
export async function flush(): Promise<{ sent: number; conflicts: number }> {
  if (flushing || !isOnline()) return { sent: 0, conflicts: 0 };
  flushing = true;
  let sent = 0;
  let conflicted = 0;
  try {
    const d = await db();
    const entries = (await d.getAll('outbox')) as OutboxEntry[];
    entries.sort((a, b) => a.createdAt.localeCompare(b.createdAt));
    for (const entry of entries) {
      try {
        await send(entry.method, entry.path, { body: entry.body, idempotencyKey: entry.key, ignoreOffline: true });
        await d.delete('outbox', entry.key);
        sent++;
      } catch (e) {
        if (e instanceof ApiError) {
          // The server has answered: park conflicts for the officer to resolve.
          await d.put('conflicts', { ...entry, code: e.code, server: e.params.server ?? e.params } satisfies OutboxConflict);
          await d.delete('outbox', entry.key);
          conflicted++;
        } else {
          break;
        }
      }
    }
  } finally {
    flushing = false;
    notify();
  }
  return { sent, conflicts: conflicted };
}

/** Test hook. */
export async function _reset(): Promise<void> {
  const d = await db();
  await d.clear('outbox');
  await d.clear('conflicts');
}
