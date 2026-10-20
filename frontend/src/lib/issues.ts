import type { TFunction } from 'i18next';

/** Renders a server issue {code, params} through errors.<code>, flattening params for interpolation. */
export function issueText(t: TFunction | ((k: string, o?: Record<string, unknown>) => string), issue: { code: string; params?: Record<string, unknown> }): string {
  const params: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(issue.params ?? {})) {
    params[k] = Array.isArray(v) ? v.join(', ') : v !== null && typeof v === 'object' ? JSON.stringify(v) : v;
  }
  return (t as (k: string, o?: Record<string, unknown>) => string)(`errors.${issue.code}`, { ...params, defaultValue: issue.code });
}
