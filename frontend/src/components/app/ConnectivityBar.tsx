import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { WifiOff, RefreshCw } from 'lucide-react';
import { useOnline } from '@/hooks/useOnline';
import { flush, onOutboxChange, pending, conflicts } from '@/lib/outbox';
import { toast } from '@/components/ui/toast';
import { Link } from 'react-router-dom';

/**
 * Persistent offline indicator plus the state of the offline outbox
 * (attendance marks and apologies awaiting sync, conflicts to resolve).
 */
export function ConnectivityBar() {
  const { t } = useTranslation();
  const online = useOnline();
  const [queued, setQueued] = React.useState(0);
  const [conflicted, setConflicted] = React.useState(0);

  const refresh = React.useCallback(async () => {
    setQueued((await pending()).length);
    setConflicted((await conflicts()).length);
  }, []);

  React.useEffect(() => {
    void refresh();
    return onOutboxChange(() => void refresh());
  }, [refresh]);

  React.useEffect(() => {
    if (!online) return;
    void flush().then((r) => {
      if (r.sent > 0) toast({ title: t('offline.synced', { count: r.sent }), tone: 'success' });
      if (r.conflicts > 0) toast({ title: t('offline.conflicts', { count: r.conflicts }), tone: 'warning' });
    });
  }, [online, t]);

  if (online && queued === 0 && conflicted === 0) return null;
  return (
    <div
      role="status"
      aria-live="polite"
      className={online ? 'bg-accent px-4 py-2 text-sm text-accent-foreground' : 'bg-foreground px-4 py-2 text-sm text-background'}
    >
      <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-x-4 gap-y-1">
        {!online && (
          <span className="flex items-center gap-2 font-semibold">
            <WifiOff className="h-4 w-4" aria-hidden /> {t('offline.banner')}
          </span>
        )}
        {!online && <span className="opacity-90">{t('offline.explanation')}</span>}
        {queued > 0 && (
          <span className="flex items-center gap-1">
            <RefreshCw className="h-4 w-4" aria-hidden /> {t('offline.queued', { count: queued })}
          </span>
        )}
        {conflicted > 0 && (
          <Link to="/sync-conflicts" className="font-semibold underline">
            {t('offline.conflicts', { count: conflicted })}
          </Link>
        )}
      </div>
    </div>
  );
}
