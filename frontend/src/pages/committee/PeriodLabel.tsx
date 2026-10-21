import { useTranslation } from 'react-i18next';
import type { CommitteePeriod } from '@/api/types';
import { Badge } from '@/components/ui/badge';
import { daysUntil, formatDate } from '@/lib/time';

/** "01 Nov 2024 – 31 Oct 2027", flagged when it has lapsed or ends soon. */
export function PeriodLabel({ period, alertDays = 60 }: { period: CommitteePeriod | null; alertDays?: number }) {
  const { t, i18n } = useTranslation();
  if (!period) return <span className="text-muted-foreground">{t('committee.noPeriod')}</span>;
  const end = period.closedOn ?? period.endsOn;
  const left = daysUntil(period.endsOn);
  return (
    <span>
      {formatDate(period.startsOn, i18n.language)} – {formatDate(end, i18n.language)}
      {!period.closedOn && left < 0 && <Badge variant="destructive" className="ml-2">{t('committee.periodLapsed')}</Badge>}
      {!period.closedOn && left >= 0 && left <= alertDays && <Badge variant="warning" className="ml-2">{t('committee.periodEndsIn', { count: left })}</Badge>}
    </span>
  );
}
