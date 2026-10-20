import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { Share, PlusSquare, X } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

const DISMISSED_KEY = 'ptc-install-prompt-dismissed';

function dismissedBefore(): boolean {
  try {
    return localStorage.getItem(DISMISSED_KEY) === '1';
  } catch {
    return true;
  }
}
function rememberDismissed() {
  try {
    localStorage.setItem(DISMISSED_KEY, '1');
  } catch {
    // ignore
  }
}

function isStandalone(): boolean {
  return window.matchMedia?.('(display-mode: standalone)').matches || (navigator as { standalone?: boolean }).standalone === true;
}

function isIos(): boolean {
  return /iphone|ipad|ipod/i.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
}

/** Asks once, unobtrusively, and never again once dismissed or installed. */
export function InstallPrompt() {
  const { t } = useTranslation();
  const [event, setEvent] = React.useState<BeforeInstallPromptEvent | null>(null);
  const [showIos, setShowIos] = React.useState(false);

  React.useEffect(() => {
    if (dismissedBefore() || isStandalone()) return;
    const handler = (e: Event) => {
      e.preventDefault();
      setEvent(e as BeforeInstallPromptEvent);
    };
    window.addEventListener('beforeinstallprompt', handler);
    let timer: ReturnType<typeof setTimeout> | undefined;
    if (isIos()) timer = setTimeout(() => setShowIos(true), 15_000);
    return () => {
      window.removeEventListener('beforeinstallprompt', handler);
      if (timer) clearTimeout(timer);
    };
  }, []);

  const close = () => {
    rememberDismissed();
    setEvent(null);
    setShowIos(false);
  };

  if (!event && !showIos) return null;
  return (
    <aside aria-label={t('pwa.installTitle')} className="fixed inset-x-3 bottom-3 z-40 rounded-lg border bg-card p-4 shadow-lg sm:left-auto sm:right-4 sm:w-96">
      <button className="absolute right-2 top-2 opacity-70 hover:opacity-100" onClick={close} aria-label={t('common.close')}>
        <X className="h-4 w-4" aria-hidden />
      </button>
      <p className="pr-6 text-sm font-semibold">{t('pwa.installTitle')}</p>
      {event ? (
        <div className="mt-2 flex items-center justify-between gap-2">
          <p className="text-sm text-muted-foreground">{t('pwa.installBody')}</p>
          <Button
            size="sm"
            onClick={async () => {
              await event.prompt();
              await event.userChoice;
              close();
            }}
          >
            {t('pwa.install')}
          </Button>
        </div>
      ) : (
        <p className="mt-2 text-sm text-muted-foreground">
          {t('pwa.iosStep1')} <Share className="inline h-4 w-4" aria-label="Share" /> {t('pwa.iosStep2')}{' '}
          <PlusSquare className="inline h-4 w-4" aria-hidden /> <strong>{t('pwa.iosAddToHome')}</strong>
        </p>
      )}
    </aside>
  );
}
