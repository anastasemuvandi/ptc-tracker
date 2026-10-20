import { useTranslation } from 'react-i18next';
import { CheckCircle2, AlertTriangle, XCircle } from 'lucide-react';
import type { Quorum } from '@/api/types';
import { quorumTone } from '@/lib/quorum';
import { cn } from '@/lib/utils';

/**
 * Live quorum against the committee's own size. Three distinct states:
 * met, EXACTLY at quorum (warning — one departure breaks it), not met.
 */
export function QuorumIndicator({ quorum, compact = false }: { quorum: Quorum; compact?: boolean }) {
  const { t } = useTranslation();
  const tone = quorumTone(quorum);
  const styles = {
    met: 'bg-success text-success-foreground',
    exact: 'bg-warning text-warning-foreground ring-2 ring-warning-foreground/40',
    notMet: 'bg-destructive text-destructive-foreground',
  }[tone];
  const Icon = tone === 'met' ? CheckCircle2 : tone === 'exact' ? AlertTriangle : XCircle;
  return (
    <div role="status" aria-live="polite" className={cn('flex items-center gap-3 rounded-lg px-4 py-3', styles)}>
      <Icon className="h-7 w-7 shrink-0" aria-hidden />
      <div className="min-w-0 flex-1">
        <p className="font-bold">
          {tone === 'met' && t('quorum.met')}
          {tone === 'exact' && t('quorum.exact')}
          {tone === 'notMet' && t('quorum.notMet', { count: quorum.needed })}
        </p>
        {!compact && (
          <p className="text-sm opacity-95">
            {t('quorum.detail', { present: quorum.present, required: quorum.required, size: quorum.committeeSize, rule: quorum.rule })}
          </p>
        )}
      </div>
      <span className="text-2xl font-extrabold tabular-nums" aria-hidden>
        {quorum.present}/{quorum.required}
      </span>
    </div>
  );
}
