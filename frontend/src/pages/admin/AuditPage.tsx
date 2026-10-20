import * as React from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { audit } from '@/api/endpoints';
import { PageHeader, QueryState } from '@/components/app/common';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Field } from '@/components/ui/field';
import { Button } from '@/components/ui/button';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatInstant } from '@/lib/time';

/** Read-only, append-only audit trail for internal audit and administrators. */
export function AuditPage() {
  const { t, i18n } = useTranslation();
  const [action, setAction] = React.useState('');
  const [before, setBefore] = React.useState<string | undefined>();
  const q = useQuery({ queryKey: ['audit', action, before], queryFn: () => audit.list({ action: action || undefined, before, limit: 100 }) });
  return (
    <div className="grid gap-4">
      <PageHeader title={t('audit.title')} subtitle={t('audit.subtitle')} />
      <Card>
        <CardContent className="flex flex-wrap items-end gap-3 pt-5">
          <Field id="au-action" label={t('audit.action')}><Input id="au-action" value={action} onChange={(e) => { setAction(e.target.value.toUpperCase()); setBefore(undefined); }} placeholder="LOGIN_FAILURE" /></Field>
        </CardContent>
      </Card>
      <QueryState query={q} empty={(d) => d.length === 0}>
        {(rows) => (
          <Card>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('audit.when')}</TableHead>
                  <TableHead>{t('audit.actor')}</TableHead>
                  <TableHead>{t('audit.action')}</TableHead>
                  <TableHead>{t('audit.entity')}</TableHead>
                  <TableHead>{t('audit.change')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((r) => (
                  <TableRow key={r.id}>
                    <TableCell className="whitespace-nowrap text-sm">{formatInstant(r.occurredAt, i18n.language)}</TableCell>
                    <TableCell className="text-sm">{r.actorName ?? t('audit.system')}</TableCell>
                    <TableCell className="font-mono text-xs">{r.action}</TableCell>
                    <TableCell className="text-sm">{r.entityType}</TableCell>
                    <TableCell className="max-w-md">
                      {(r.previousValue || r.newValue) && (
                        <details>
                          <summary className="cursor-pointer text-sm text-primary">{t('audit.details')}</summary>
                          {r.previousValue && <pre className="mt-1 max-h-48 overflow-auto rounded bg-muted p-2 text-xs">{t('audit.previous')}: {r.previousValue}</pre>}
                          {r.newValue && <pre className="mt-1 max-h-48 overflow-auto rounded bg-muted p-2 text-xs">{t('audit.new')}: {r.newValue}</pre>}
                        </details>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
            {rows.length === 100 && (
              <div className="p-3"><Button variant="outline" onClick={() => setBefore(rows[rows.length - 1].occurredAt)}>{t('audit.older')}</Button></div>
            )}
          </Card>
        )}
      </QueryState>
    </div>
  );
}
