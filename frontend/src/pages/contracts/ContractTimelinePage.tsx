import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { contracts } from '@/api/endpoints';
import { KeyValue, PageHeader, QueryState, StatusBadge } from '@/components/app/common';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatDate, formatMoney, formatPct } from '@/lib/time';
import { cn } from '@/lib/utils';

export function ContractTimelinePage() {
  const { id = '' } = useParams();
  const { t, i18n } = useTranslation();
  const q = useQuery({ queryKey: ['contract-timeline', id], queryFn: () => contracts.timeline(id) });
  return (
    <QueryState query={q}>
      {(tl) => {
        const c = tl.contract;
        const pct = Number(tl.cumulativePct);
        const ceiling = Number(tl.ceilingPct);
        const width = Math.min(100, ceiling ? (pct / ceiling) * 100 : 0);
        const ps = tl.perfSecurity.state;
        return (
          <div className="grid gap-4">
            <PageHeader title={`${c.contractRef} — ${c.title}`} subtitle={t('contracts.timelineSubtitle')} />
            <div className="grid gap-4 lg:grid-cols-3">
              <Card className="lg:col-span-2">
                <CardHeader><CardTitle>{t('contracts.summary')}</CardTitle></CardHeader>
                <CardContent>
                  <KeyValue items={[
                    [t('contracts.supplier'), c.supplierRef],
                    [t('files.tenderRef'), c.tenderRef],
                    [t('contracts.originalValue'), formatMoney(c.originalValue, c.currency, i18n.language)],
                    [t('contracts.cumulativeApproved'), formatMoney(tl.cumulativeApproved, c.currency, i18n.language)],
                    [t('contracts.originalCompletion'), formatDate(c.originalCompletionDate, i18n.language)],
                    [t('contracts.currentCompletion'), formatDate(tl.currentCompletionDate, i18n.language)],
                    [t('contracts.perfSecurityExpiry'), <span key="ps">{formatDate(tl.perfSecurity.expiry, i18n.language)} <Badge variant={ps === 'VALID' ? 'success' : ps === 'NONE' ? 'outline' : ps === 'EXPIRING' ? 'warning' : 'destructive'}>{t(`contracts.perfSecurityState.${ps}`)}</Badge></span>],
                  ]} />
                </CardContent>
              </Card>
              <Card>
                <CardHeader><CardTitle>{t('contracts.ceilingUse')}</CardTitle></CardHeader>
                <CardContent className="grid gap-2">
                  <p className="text-3xl font-extrabold">{formatPct(tl.cumulativePct)}</p>
                  <p className="text-sm text-muted-foreground">{t('contracts.ofCeiling', { ceiling: formatPct(tl.ceilingPct) })}</p>
                  <div className="h-3 w-full overflow-hidden rounded-full bg-muted" role="meter" aria-valuemin={0} aria-valuemax={ceiling} aria-valuenow={pct} aria-label={t('contracts.ceilingUse')}>
                    <div className={cn('h-full', width >= 100 ? 'bg-destructive' : width >= 75 ? 'bg-warning' : 'bg-success')} style={{ width: `${width}%` }} />
                  </div>
                  <p className="text-xs text-muted-foreground">{t('contracts.noConversion', { currency: c.currency })}</p>
                </CardContent>
              </Card>
            </div>
            <Card>
              <CardHeader><CardTitle>{t('contracts.timeline')}</CardTitle></CardHeader>
              <CardContent>
                {tl.events.length === 0 ? <p className="text-sm text-muted-foreground">{t('contracts.noChanges')}</p> : (
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>{t('common.date')}</TableHead>
                        <TableHead>{t('contracts.change')}</TableHead>
                        <TableHead>{t('files.number')}</TableHead>
                        <TableHead>{t('common.status')}</TableHead>
                        <TableHead>{t('contracts.value')}</TableHead>
                        <TableHead>{t('contracts.runningCumulative')}</TableHead>
                        <TableHead>{t('contracts.newCompletion')}</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {tl.events.map((e) => (
                        <TableRow key={e.fileId}>
                          <TableCell>{formatDate(e.date, i18n.language)}</TableCell>
                          <TableCell>{t(`contracts.eventKind.${e.kind}`)}</TableCell>
                          <TableCell><Link to={`/files/${e.fileId}`} className="text-primary underline">{e.fileNumber}</Link></TableCell>
                          <TableCell><StatusBadge ns="contractChange.status" value={e.status} /></TableCell>
                          <TableCell>{e.value != null ? formatMoney(e.value, c.currency, i18n.language) : '—'}</TableCell>
                          <TableCell>
                            {e.runningCumulative != null ? `${formatMoney(e.runningCumulative, c.currency, i18n.language)} (${formatPct(e.runningPct)})`
                              : e.projectedCumulative != null ? <span className="text-muted-foreground">{t('contracts.projected', { value: formatMoney(e.projectedCumulative, c.currency, i18n.language), pct: formatPct(e.projectedPct) })}</span> : '—'}
                          </TableCell>
                          <TableCell>{formatDate(e.newCompletionDate ?? null, i18n.language)}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                )}
              </CardContent>
            </Card>
          </div>
        );
      }}
    </QueryState>
  );
}
