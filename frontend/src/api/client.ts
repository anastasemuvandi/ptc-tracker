import { ApiError, OfflineError } from './errors';
import { refreshAccessToken } from './refresh';
import { tokenStore } from './tokenStore';

type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

export interface RequestOptions {
  body?: unknown;
  /** Reuse when retrying the same logical action (e.g. after step-up). */
  idempotencyKey?: string;
  /** Send even if the browser reports offline (used by the outbox flush). */
  ignoreOffline?: boolean;
  signal?: AbortSignal;
  headers?: Record<string, string>;
}

/**
 * Registered by the StepUpDialog. Resolves true once the user has
 * re-authenticated, false if they cancelled.
 */
export type StepUpHandler = (params: Record<string, unknown>) => Promise<boolean>;
let stepUpHandler: StepUpHandler | null = null;
export function setStepUpHandler(h: StepUpHandler | null): void {
  stepUpHandler = h;
}

let unauthorizedHandler: (() => void) | null = null;
export function setUnauthorizedHandler(h: (() => void) | null): void {
  unauthorizedHandler = h;
}

export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}

const MUTATING = new Set<Method>(['POST', 'PUT', 'PATCH', 'DELETE']);

export function isOnline(): boolean {
  return typeof navigator === 'undefined' || navigator.onLine !== false;
}

async function parseError(res: Response): Promise<ApiError> {
  let code = `http_${res.status}`;
  let message = res.statusText;
  let params: Record<string, unknown> = {};
  try {
    const body = (await res.json()) as { code?: string; message?: string; params?: Record<string, unknown> };
    code = body.code ?? code;
    message = body.message ?? message;
    params = body.params ?? {};
  } catch {
    // non-JSON error body
  }
  return new ApiError(res.status, code, message, params);
}

/**
 * Low-level request returning the Response. Adds the bearer token and an
 * Idempotency-Key on writes; refreshes once on an expired token; on
 * step-up challenges asks the user to re-authenticate and retries with the
 * SAME idempotency key so the act is recorded exactly once.
 */
export async function send(method: Method, path: string, opts: RequestOptions = {}): Promise<Response> {
  const mutating = MUTATING.has(method);
  if (mutating && !opts.ignoreOffline && !isOnline()) throw new OfflineError();
  const key = mutating ? opts.idempotencyKey ?? newIdempotencyKey() : undefined;

  if (!tokenStore.isFresh() && tokenStore.get() !== null) {
    await refreshAccessToken();
  }

  let refreshed = false;
  let steppedUp = false;
  for (;;) {
    const headers: Record<string, string> = { Accept: 'application/json', ...opts.headers };
    const token = tokenStore.get();
    if (token) headers.Authorization = `Bearer ${token}`;
    if (key) headers['Idempotency-Key'] = key;
    let body: BodyInit | undefined;
    if (opts.body instanceof FormData) {
      body = opts.body;
    } else if (opts.body !== undefined) {
      headers['Content-Type'] = 'application/json';
      body = JSON.stringify(opts.body);
    }
    let res: Response;
    try {
      res = await fetch(path, { method, headers, body, credentials: 'same-origin', signal: opts.signal });
    } catch (e) {
      if (mutating && !isOnline()) throw new OfflineError();
      throw e;
    }
    if (res.ok) return res;

    const err = await parseError(res);
    if (res.status === 401 && (err.code === 'auth.invalid_token' || err.code === 'http_401') && !refreshed) {
      refreshed = true;
      if (await refreshAccessToken()) continue;
      unauthorizedHandler?.();
      throw err;
    }
    if (res.status === 401 && err.code === 'auth.step_up_required' && !steppedUp && stepUpHandler) {
      steppedUp = true;
      if (await stepUpHandler(err.params)) continue;
    }
    throw err;
  }
}

export async function request<T>(method: Method, path: string, opts: RequestOptions = {}): Promise<T> {
  const res = await send(method, path, opts);
  if (res.status === 204 || res.status === 202) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const api = {
  get: <T>(path: string, opts?: RequestOptions) => request<T>('GET', path, opts),
  post: <T>(path: string, body?: unknown, opts?: RequestOptions) => request<T>('POST', path, { ...opts, body }),
  put: <T>(path: string, body?: unknown, opts?: RequestOptions) => request<T>('PUT', path, { ...opts, body }),
  del: <T>(path: string, opts?: RequestOptions) => request<T>('DELETE', path, opts),
};

/** Fetches a server-generated file (minutes .docx, CSV) and saves it. */
export async function download(path: string, fallbackName: string): Promise<void> {
  const res = await send('GET', path);
  const blob = await res.blob();
  const disposition = res.headers.get('Content-Disposition') ?? '';
  const match = /filename="?([^";]+)"?/.exec(disposition);
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = match?.[1] ?? fallbackName;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function qs(params: Record<string, string | number | boolean | undefined | null>): string {
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') p.set(k, String(v));
  }
  const s = p.toString();
  return s ? `?${s}` : '';
}
