export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly params: Record<string, unknown> = {},
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

/** Thrown instead of sending (or queueing) a write while offline. */
export class OfflineError extends Error {
  readonly code = 'offline.blocked';
  constructor() {
    super('No connection');
    this.name = 'OfflineError';
  }
}

export function errorCode(e: unknown): string {
  if (e instanceof ApiError || e instanceof OfflineError) return e.code;
  return 'unexpected';
}
