import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { notifications } from '@/api/endpoints';
import { useMutate } from '@/hooks/useMutate';
import { PageHeader, QueryState } from '@/components/app/common';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { formatInstant } from '@/lib/time';

export function NotificationsPage() {
  const { t, i18n } = useTranslation();
  const q = useQuery({ queryKey: ['notifications', 'all'], queryFn: () => notifications.list() });
  const readAll = useMutate(() => notifications.markAllRead(), { invalidate: [['notifications']] });
  const read = useMutate((id: string) => notifications.markRead(id), { invalidate: [['notifications']] });
  return (
    <div className="mx-auto max-w-2xl">
      <PageHeader title={t('notifications.title')} subtitle={t('settings.emailGuaranteed')}
        actions={<Button variant="outline" onClick={() => readAll.mutate(undefined)}>{t('notifications.markAllRead')}</Button>} />
      <QueryState query={q} empty={(d) => d.length === 0}>
        {(list) => (
          <ul className="grid gap-2">
            {list.map((n) => (
              <li key={n.id}>
                <Card className={cn(!n.readAt && 'border-l-4 border-l-primary')}>
                  <Link to={n.link} onClick={() => !n.readAt && read.mutate(n.id)} className="block p-4">
                    <p className={cn('font-medium', !n.readAt && 'font-bold')}>{n.title}</p>
                    <p className="text-sm text-muted-foreground">{n.body}</p>
                    <p className="mt-1 text-xs text-muted-foreground">{formatInstant(n.createdAt, i18n.language)}</p>
                  </Link>
                </Card>
              </li>
            ))}
          </ul>
        )}
      </QueryState>
    </div>
  );
}
