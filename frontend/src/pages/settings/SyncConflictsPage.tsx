import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { conflicts, discardConflict, overrideConflict, onOutboxChange, type OutboxConflict } from '@/lib/outbox';
import { useErrorText } from '@/hooks/useErrorText';
import { PageHeader } from '@/components/app/common';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { toast } from '@/components/ui/toast';
import { formatInstant } from '@/lib/time';

/** Offline marks the server could not apply as-is: show both values and let the officer choose. */
export function SyncConflictsPage() {
  const { t, i18n } = useTranslation();
  const errorText = useErrorText();
  const [items, setItems] = React.useState<OutboxConflict[]>([]);
  React.useEffect(() => {
    const load = () => void conflicts().then(setItems);
    load();
    return onOutboxChange(load);
  }, []);
  return (
    <div className="mx-auto max-w-2xl">
      <PageHeader title={t('offline.conflictsTitle')} subtitle={t('offline.conflictsHelp')} />
      {items.length === 0 && <p className="text-sm text-muted-foreground">{t('offline.noConflicts')}</p>}
      <div className="grid gap-3">
        {items.map((c) => {
          const server = (c.server ?? {}) as { status?: string; recordedAt?: string; recordedByName?: string; version?: number };
          return (
            <Card key={c.key}>
              <CardHeader><CardTitle className="text-base">{c.label}</CardTitle></CardHeader>
              <CardContent className="grid gap-3 text-sm">
                <p>{t('offline.yourValue')}: <strong>{c.kind === 'attendance' ? t(`attendance.status.${String(c.body.status)}`) : String(c.body.reason ?? '')}</strong> · {formatInstant(String(c.body.clientRecordedAt ?? c.createdAt), i18n.language)}</p>
                <p>{t('offline.serverValue')}: <strong>{server.status ? t(`attendance.status.${server.status}`) : '—'}</strong>
                  {server.recordedAt && ` · ${formatInstant(server.recordedAt, i18n.language)}`}{server.recordedByName && ` · ${server.recordedByName}`}</p>
                <p className="text-muted-foreground">{t(`errors.${c.code}`, { defaultValue: c.code })}</p>
                <div className="flex flex-wrap gap-2">
                  <Button variant="outline" onClick={() => void discardConflict(c.key)}>{t('offline.keepServer')}</Button>
                  {c.kind === 'attendance' && (
                    <Button onClick={() => void overrideConflict(c, server.version).then(() => toast({ title: t('common.saved'), tone: 'success' }))
                      .catch((e) => toast({ title: errorText(e), tone: 'destructive' }))}>{t('offline.useMine')}</Button>
                  )}
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>
    </div>
  );
}
