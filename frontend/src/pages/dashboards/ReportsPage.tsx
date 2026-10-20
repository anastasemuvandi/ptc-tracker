import * as React from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Download } from 'lucide-react';
import { dashboards, committees } from '@/api/endpoints';
import { useErrorText } from '@/hooks/useErrorText';
import { PageHeader, QueryState } from '@/components/app/common';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Field } from '@/components/ui/field';
import { SimpleSelect } from '@/components/ui/select';
import { toast } from '@/components/ui/toast';
import { DataTable, cols } from '@/components/domain/DataTable';
import { todayKigali } from '@/lib/time';

type Row = Record<string, unknown>;

export function ReportsPage() {
  const { t } = useTranslation();
  const errorText = useErrorText();
  const today = todayKigali();
  const [from, setFrom] = React.useState(`${today.slice(0, 4)}-01-01`);
  const [to, setTo] = React.useState(today);
  const [committeeId, setCommitteeId] = React.useState('');
  const cs = useQuery({ queryKey: ['committees'], queryFn: committees.list });
  const params = { from, to, committeeId: committeeId || undefined };
  const q = useQuery({ queryKey: ['report', params], queryFn: () => dashboards.report(params), enabled: !!from && !!to });
  return (
    <div className="grid gap-4">
      <PageHeader title={t('reports.title')} subtitle={t('reports.subtitle')}
        actions={<Button variant="outline" onClick={() => void dashboards.reportCsv(params).catch((e) => toast({ title: errorText(e), tone: 'destructive' }))}>
          <Download className="h-4 w-4" aria-hidden />{t('reports.csv')}</Button>} />
      <Card>
        <CardContent className="grid gap-3 pt-5 sm:grid-cols-3">
          <Field id="r-from" label={t('reports.from')}><Input id="r-from" type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></Field>
          <Field id="r-to" label={t('reports.to')}><Input id="r-to" type="date" value={to} onChange={(e) => setTo(e.target.value)} /></Field>
          <Field id="r-c" label={t('common.committee')}>
            <SimpleSelect id="r-c" value={committeeId} onChange={setCommitteeId} allowEmpty emptyLabel={t('common.allCommittees')}
              options={(cs.data ?? []).map((c) => ({ value: c.id, label: c.code }))} />
          </Field>
        </CardContent>
      </Card>
      <QueryState query={q}>
        {(d) => (
          <>
            <Card><CardHeader><CardTitle>{t('reports.filesByType')}</CardTitle></CardHeader>
              <CardContent><DataTable rows={d.filesByType as Row[]} columns={cols('itemType', 'received', 'recommendationsIssued', 'closed')} /></CardContent></Card>
            <Card><CardHeader><CardTitle>{t('reports.sessions')}</CardTitle></CardHeader>
              <CardContent><DataTable rows={d.sessions as Row[]} columns={cols('committeeCode', 'held', 'cancelled', 'extraordinaryHeld', 'shortNoticeHeld')} /></CardContent></Card>
            <Card><CardHeader><CardTitle>{t('reports.attendance')}</CardTitle></CardHeader>
              <CardContent><DataTable rows={d.attendance as Row[]} columns={cols('fullName', 'committeeCode', 'role', 'sessionsHeld', 'present', 'absentWithPermission', 'onLeave', 'absentUnjustified', 'ratePct')} /></CardContent></Card>
          </>
        )}
      </QueryState>
    </div>
  );
}
