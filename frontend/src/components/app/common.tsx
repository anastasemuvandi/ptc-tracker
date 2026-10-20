import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { AlertTriangle } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Spinner } from '@/components/ui/spinner';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { useErrorText } from '@/hooks/useErrorText';
import type { ItemType } from '@/api/types';
import { cn } from '@/lib/utils';

export function PageHeader({ title, subtitle, actions }: { title: React.ReactNode; subtitle?: React.ReactNode; actions?: React.ReactNode }) {
  return (
    <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
      <div>
        <h1 className="text-xl font-bold sm:text-2xl">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-muted-foreground">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

/** Loading / error / empty wrapper for TanStack queries. */
export function QueryState<T>({
  query, empty, children,
}: {
  query: { isLoading: boolean; error: unknown; data: T | undefined };
  empty?: (d: T) => boolean;
  children: (d: T) => React.ReactNode;
}) {
  const { t } = useTranslation();
  const errorText = useErrorText();
  if (query.isLoading) return <Spinner label={t('common.loading')} className="p-4" />;
  if (query.error) {
    return (
      <Alert variant="destructive">
        <AlertTriangle className="h-4 w-4" aria-hidden />
        <AlertDescription>{errorText(query.error)}</AlertDescription>
      </Alert>
    );
  }
  if (query.data === undefined) return null;
  if (empty?.(query.data)) return <p className="p-4 text-sm text-muted-foreground">{t('common.empty')}</p>;
  return <>{children(query.data)}</>;
}

const typeColour: Record<ItemType, string> = {
  A: 'bg-primary text-primary-foreground',
  B: 'bg-sky-700 text-white',
  C: 'bg-amber-700 text-white',
  D: 'bg-orange-700 text-white',
  E: 'bg-teal-700 text-white',
  F: 'bg-red-800 text-white',
  G: 'bg-indigo-700 text-white',
  H: 'bg-slate-700 text-white',
  I: 'bg-slate-200 text-slate-900',
};

export function ItemTypeBadge({ type, long = false }: { type: ItemType; long?: boolean }) {
  const { t } = useTranslation();
  return (
    <span
      className={cn('inline-flex shrink-0 items-center gap-1 rounded px-2 py-0.5 text-xs font-bold', typeColour[type])}
      title={t(`itemType.${type}`)}
    >
      {type}
      {long && <span className="font-medium">· {t(`itemType.${type}`)}</span>}
    </span>
  );
}

export function StatusBadge({ ns, value }: { ns: string; value: string | null | undefined }) {
  const { t } = useTranslation();
  if (!value) return null;
  const tone: Record<string, 'default' | 'secondary' | 'success' | 'warning' | 'destructive' | 'outline'> = {
    COMPLETED: 'success', ADOPTED: 'success', ISSUED: 'success', APPROVED: 'success', HELD: 'success', ACTIVE: 'success',
    FINAL: 'success', COMPLIED: 'success', CLARIFIED: 'success', PASS: 'success', DONE: 'success', CLOSED: 'secondary',
    IN_PROGRESS: 'warning', VOTING: 'warning', PENDING: 'warning', IN_REVIEW: 'warning', OPEN: 'warning', PLANNED: 'secondary',
    NOT_ADOPTED: 'destructive', REJECTED: 'destructive', CANCELLED: 'outline', NOT_COMPLIED: 'destructive', FAIL: 'destructive',
    ATTEMPTED_ALTERATION: 'destructive', RETURNED_FOR_CORRECTION: 'destructive', EXPIRED: 'destructive',
  };
  return <Badge variant={tone[value] ?? 'outline'}>{t(`${ns}.${value}`, { defaultValue: value })}</Badge>;
}

export function KeyValue({ items }: { items: [React.ReactNode, React.ReactNode][] }) {
  return (
    <dl className="grid grid-cols-1 gap-x-6 gap-y-2 text-sm sm:grid-cols-2">
      {items.map(([k, v], i) => (
        <div key={i} className="flex flex-col">
          <dt className="text-xs uppercase tracking-wide text-muted-foreground">{k}</dt>
          <dd className="font-medium">{v ?? '—'}</dd>
        </div>
      ))}
    </dl>
  );
}

export function Countdown({ days, label, warnAt = 3 }: { days: number | null | undefined; label: string; warnAt?: number }) {
  const { t } = useTranslation();
  if (days === null || days === undefined) return null;
  const tone = days < 0 ? 'bg-destructive text-destructive-foreground' : days <= warnAt ? 'bg-warning text-warning-foreground' : 'bg-secondary text-secondary-foreground';
  return (
    <div className={cn('inline-flex flex-col rounded-md px-3 py-2', tone)} role="timer" aria-label={label}>
      <span className="text-xs font-medium">{label}</span>
      <span className="text-lg font-bold">
        {days < 0 ? t('time.daysOverdue', { count: -days }) : t('time.daysLeft', { count: days })}
      </span>
    </div>
  );
}
