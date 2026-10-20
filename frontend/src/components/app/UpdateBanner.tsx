import { useTranslation } from 'react-i18next';
import { useRegisterSW } from 'virtual:pwa-register/react';
import { Button } from '@/components/ui/button';

/**
 * Service-worker update flow: a waiting new version is announced and only
 * activated (followed by a reload) when the user confirms, so nobody is
 * stranded on a stale version and nobody loses a half-filled form.
 */
export function UpdateBanner() {
  const { t } = useTranslation();
  const {
    needRefresh: [needRefresh, setNeedRefresh],
    updateServiceWorker,
  } = useRegisterSW({
    onRegisteredSW(_url, registration) {
      // Check for new versions hourly while the app stays open.
      if (registration) setInterval(() => void registration.update(), 60 * 60 * 1000);
    },
  });
  if (!needRefresh) return null;
  return (
    <div role="alert" className="fixed inset-x-0 bottom-0 z-50 p-3 sm:bottom-4 sm:left-auto sm:right-4 sm:w-96">
      <div className="flex items-center gap-3 rounded-lg border bg-card p-4 shadow-lg">
        <p className="flex-1 text-sm">{t('pwa.updateAvailable')}</p>
        <Button size="sm" variant="ghost" onClick={() => setNeedRefresh(false)}>{t('pwa.later')}</Button>
        <Button size="sm" onClick={() => void updateServiceWorker(true)}>{t('pwa.reload')}</Button>
      </div>
    </div>
  );
}
