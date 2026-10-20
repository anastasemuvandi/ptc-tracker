import { useTranslation } from 'react-i18next';
import { ApiError, OfflineError } from '@/api/errors';

/** Translates API errors through errors.<code>, with the server's English text as fallback. */
export function useErrorText(): (e: unknown) => string {
  const { t } = useTranslation();
  return (e: unknown) => {
    if (e instanceof OfflineError) return t('errors.offline.blocked');
    if (e instanceof ApiError) {
      const key = `errors.${e.code}`;
      const params: Record<string, unknown> = {};
      for (const [k, v] of Object.entries(e.params ?? {})) {
        params[k] = typeof v === 'object' ? JSON.stringify(v) : v;
      }
      return t(key, { ...params, defaultValue: e.message || t('errors.unexpected') });
    }
    return t('errors.unexpected');
  };
}
