import * as React from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { dashboards } from '@/api/endpoints';
import { useSession } from '@/hooks/useSession';
import { PageHeader, QueryState } from '@/components/app/common';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { SimpleSelect } from '@/components/ui/select';
import { Badge } from '@/components/ui/badge';
import { DataTable, cols } from '@/components/domain/DataTable';

type Row = Record<string, unknown>;

export function ChairDashboardPage() {
  const { t } = useTranslation();
  const { me } = useSession();
  const mine = (me?.committees ?? []).filter((c) => c.role !== 'MEMBER');
  const [committeeId, setCommitteeId] = React.useState(mine[0]?.committeeId ?? '');
  const q = useQuery({ queryKey: ['dashboard', 'chair', committeeId], queryFn: () => dashboards.chair(committeeId), enabled: !!committeeId });
  return (
    <div className="grid gap-4">
      <PageHeader title={t('dashboard.chairTitle')} actions={mine.length > 1 && (
        <div className="w-56"><SimpleSelect value={committeeId} onChange={setCommitteeId} options={mine.map((c) => ({ value: c.committeeId, label: c.code }))} /></div>
      )} />
      <QueryState query={q}>
        {(d) => (
          <div className="grid gap-4 lg:grid-cols-2">
            <Card className="lg:col-span-2">
              <CardHeader><CardTitle>{t('dashboard.quorumRisk')}</CardTitle></CardHeader>
              <CardContent>
                <DataTable rows={d.quorumRisk as Row[]} columns={cols(
                  { key: 'sessionNumber', link: (r: Row) => `/sessions/${String(r.id)}` }, 'scheduledAt', 'activeMembers', 'apologies', 'expectedPresent', 'required',
                  { key: 'risk', render: (r: Row) => <Badge variant={r.risk === 'OK' ? 'success' : r.risk === 'EXACTLY_AT_QUORUM' ? 'warning' : 'destructive'}>{t(`dashboard.risk.${String(r.risk)}`)}</Badge> },
                )} empty={t('dashboard.noUpcoming')} />
              </CardContent>
            </Card>
            <Card>
              <CardHeader><CardTitle>{t('dashboard.workloadPerOfficer')}</CardTitle></CardHeader>
              <CardContent><DataTable rows={d.workloadPerOfficer as Row[]} columns={cols('officerName', 'openFiles', 'overdueFiles')} /></CardContent>
            </Card>
            <Card>
              <CardHeader><CardTitle>{t('dashboard.overdueByStageType')}</CardTitle></CardHeader>
              <CardContent><DataTable rows={d.overdueByStageAndType as Row[]} columns={cols('stage', 'itemType', 'overdue')} /></CardContent>
            </Card>
            <Card className="lg:col-span-2">
              <CardHeader><CardTitle>{t('dashboard.overdueFiles')}</CardTitle></CardHeader>
              <CardContent><DataTable rows={d.overdueFiles as Row[]} columns={cols({ key: 'fileNumber', link: (r: Row) => `/files/${String(r.id)}` }, 'itemType', 'title', 'stage', 'targetDate', 'daysOverdue')} /></CardContent>
            </Card>
            <Card>
              <CardHeader><CardTitle>{t('dashboard.turnaround')}</CardTitle></CardHeader>
              <CardContent><DataTable rows={d.turnaroundByType as Row[]} columns={cols('itemType', 'files', 'avgDays', 'maxDays')} /></CardContent>
            </Card>
            <Card>
              <CardHeader><CardTitle>{t('dashboard.bidValidity')}</CardTitle></CardHeader>
              <CardContent><DataTable rows={d.bidValidityExpiringSoon as Row[]} columns={cols({ key: 'fileNumber', link: (r: Row) => `/n/bid-validity/${String(r.id)}` }, 'bidValidityExpiry', 'regulatorRequestDeadline', 'daysToExpiry')} /></CardContent>
            </Card>
            <Card className="lg:col-span-2">
              <CardHeader><CardTitle>{t('dashboard.nearCeiling')}</CardTitle></CardHeader>
              <CardContent><DataTable rows={d.contractsNearCeiling as Row[]} columns={cols({ key: 'contractRef', link: (r: Row) => `/contracts/${String(r.id)}` }, 'title', 'currency', 'approvedPct', 'withPendingPct', 'ceilingPct', 'level')} /></CardContent>
            </Card>
          </div>
        )}
      </QueryState>
    </div>
  );
}
