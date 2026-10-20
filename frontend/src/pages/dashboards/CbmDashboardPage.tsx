import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { dashboards } from '@/api/endpoints';
import { PageHeader, QueryState } from '@/components/app/common';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { DataTable, cols } from '@/components/domain/DataTable';

type Row = Record<string, unknown>;

export function CbmDashboardPage() {
  const { t } = useTranslation();
  const q = useQuery({ queryKey: ['dashboard', 'cbm'], queryFn: dashboards.cbm });
  return (
    <div className="grid gap-4">
      <PageHeader title={t('dashboard.cbmTitle')} />
      <QueryState query={q}>
        {(d) => (
          <div className="grid gap-4 lg:grid-cols-2">
            <Card className="lg:col-span-2">
              <CardHeader><CardTitle>{t('dashboard.committees')}</CardTitle></CardHeader>
              <CardContent>
                <DataTable rows={d.committees as Row[]} columns={cols(
                  { key: 'code', link: (r: Row) => `/committees/${String(r.id)}` }, 'name', 'size', 'activeMembers', 'procurementOfficers', 'openFiles', 'overdueFiles',
                  { key: 'compliant', render: (r: Row) => r.compliant
                    ? <Badge variant="success">{t('composition.compliant')}</Badge>
                    : <span className="text-sm text-destructive">{((r.complianceIssues as string[]) ?? []).map((c) => t(`errors.${c}`, { defaultValue: c })).join('; ')}</span> },
                )} />
              </CardContent>
            </Card>
            <Card className="lg:col-span-2">
              <CardHeader><CardTitle>{t('dashboard.pendingRecommendations')}</CardTitle></CardHeader>
              <CardContent><DataTable rows={d.pendingRecommendations as Row[]} columns={cols(
                { key: 'fileNumber', link: (r: Row) => `/n/recommendation/${String(r.decisionId)}` }, 'itemType', 'title', 'committeeCode', 'sessionNumber', 'issuedAt', 'daysWaiting')} /></CardContent>
            </Card>
            <Card>
              <CardHeader><CardTitle>{t('dashboard.termExpiries')}</CardTitle></CardHeader>
              <CardContent><DataTable rows={d.termExpiries as Row[]} columns={cols({ key: 'fullName', link: (r: Row) => `/n/term/${String(r.membershipId)}` }, 'committeeCode', 'role', 'termEnd', 'daysLeft', 'consecutiveTerm')} /></CardContent>
            </Card>
            <Card>
              <CardHeader><CardTitle>{t('dashboard.workloadByCommittee')}</CardTitle></CardHeader>
              <CardContent><DataTable rows={d.workloadByCommittee as Row[]} columns={cols('committeeCode', 'itemType', 'openFiles')} /></CardContent>
            </Card>
            <Card>
              <CardHeader><CardTitle>{t('dashboard.openTasks')}</CardTitle></CardHeader>
              <CardContent><DataTable rows={d.openTasks as Row[]} columns={cols({ key: 'title', link: (r: Row) => `/n/task/${String(r.id)}` }, 'committeeCode', 'dueDate', 'overdue')} /></CardContent>
            </Card>
            <Card>
              <CardHeader><CardTitle>{t('dashboard.nearCeiling')}</CardTitle></CardHeader>
              <CardContent><DataTable rows={d.contractsNearCeiling as Row[]} columns={cols({ key: 'contractRef', link: (r: Row) => `/contracts/${String(r.id)}` }, 'withPendingPct', 'ceilingPct', 'level')} /></CardContent>
            </Card>
          </div>
        )}
      </QueryState>
    </div>
  );
}
