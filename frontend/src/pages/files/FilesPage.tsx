import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Plus } from 'lucide-react';
import { files, committees, identity, contracts } from '@/api/endpoints';
import type { ItemType } from '@/api/types';
import { useSession } from '@/hooks/useSession';
import { ItemTypeBadge, PageHeader, QueryState } from '@/components/app/common';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent } from '@/components/ui/card';
import { SimpleSelect } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { formatDate } from '@/lib/time';

const TYPES: ItemType[] = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I'];
const STAGES = ['RECEIVED', 'SCHEDULED', 'UNDER_REVIEW', 'RECOMMENDATION_ISSUED', 'SUBMITTED_TO_CBM', 'CLOSED', 'RETURNED_FOR_CORRECTION'];
const PROC = ['WORKS', 'GOODS', 'CONSULTANCY', 'NON_CONSULTANCY'];

export function FilesPage() {
  const { t, i18n } = useTranslation();
  const { hasRole, isOfficerAnywhere } = useSession();
  const [params, setParams] = useSearchParams();
  const f = {
    committeeId: params.get('committeeId') ?? '',
    type: params.get('type') ?? '',
    officerId: params.get('officerId') ?? '',
    stage: params.get('stage') ?? '',
    procurementType: params.get('procurementType') ?? '',
    contractId: params.get('contractId') ?? '',
    overdue: params.get('overdue') === 'true',
  };
  const set = (k: string, v: string | boolean) => {
    const next = new URLSearchParams(params);
    if (v === '' || v === false) next.delete(k);
    else next.set(k, String(v));
    setParams(next, { replace: true });
  };
  const q = useQuery({ queryKey: ['files', f], queryFn: () => files.list({ ...f, overdue: f.overdue || undefined }) });
  const cs = useQuery({ queryKey: ['committees'], queryFn: committees.list });
  const users = useQuery({ queryKey: ['users'], queryFn: identity.users });
  const cts = useQuery({ queryKey: ['contracts'], queryFn: contracts.list });
  const officers = (users.data ?? []).filter((u) => u.isProcurementOfficer);

  return (
    <div>
      <PageHeader
        title={t('files.title')}
        subtitle={t('files.subtitle')}
        actions={(hasRole('CBM') || isOfficerAnywhere) && (
          <Button asChild><Link to="/files/new"><Plus className="h-4 w-4" aria-hidden />{t('files.new')}</Link></Button>
        )}
      />
      <Card className="mb-4">
        <CardContent className="grid gap-3 pt-4 sm:grid-cols-2 lg:grid-cols-4">
          <SimpleSelect value={f.committeeId} onChange={(v) => set('committeeId', v)} allowEmpty emptyLabel={t('common.allCommittees')}
            options={(cs.data ?? []).map((c) => ({ value: c.id, label: c.code }))} />
          <SimpleSelect value={f.type} onChange={(v) => set('type', v)} allowEmpty emptyLabel={t('files.allTypes')}
            options={TYPES.map((x) => ({ value: x, label: `${x} — ${t(`itemType.${x}`)}` }))} />
          <SimpleSelect value={f.stage} onChange={(v) => set('stage', v)} allowEmpty emptyLabel={t('files.allStages')}
            options={STAGES.map((s) => ({ value: s, label: t(`stage.${s}`) }))} />
          <SimpleSelect value={f.officerId} onChange={(v) => set('officerId', v)} allowEmpty emptyLabel={t('files.allOfficers')}
            options={officers.map((u) => ({ value: u.id, label: u.fullName }))} />
          <SimpleSelect value={f.procurementType} onChange={(v) => set('procurementType', v)} allowEmpty emptyLabel={t('files.allProcurementTypes')}
            options={PROC.map((p) => ({ value: p, label: t(`procurementType.${p}`) }))} />
          <SimpleSelect value={f.contractId} onChange={(v) => set('contractId', v)} allowEmpty emptyLabel={t('files.allContracts')}
            options={(cts.data ?? []).map((c) => ({ value: c.id, label: c.contractRef }))} />
          <div className="flex items-center gap-2">
            <Checkbox id="overdue" checked={f.overdue} onCheckedChange={(c) => set('overdue', c === true)} />
            <Label htmlFor="overdue">{t('files.overdueOnly')}</Label>
          </div>
        </CardContent>
      </Card>
      <QueryState query={q} empty={(d) => d.length === 0}>
        {(list) => (
          <Card>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('files.number')}</TableHead>
                  <TableHead>{t('files.type')}</TableHead>
                  <TableHead>{t('files.titleCol')}</TableHead>
                  <TableHead>{t('files.reference')}</TableHead>
                  <TableHead>{t('files.officer')}</TableHead>
                  <TableHead>{t('files.stage')}</TableHead>
                  <TableHead>{t('files.received')}</TableHead>
                  <TableHead>{t('files.target')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {list.map((r) => (
                  <TableRow key={r.id}>
                    <TableCell className="font-medium"><Link className="text-primary underline" to={`/files/${r.id}`}>{r.fileNumber}</Link></TableCell>
                    <TableCell><ItemTypeBadge type={r.itemType} /></TableCell>
                    <TableCell className="max-w-xs truncate">{r.title}</TableCell>
                    <TableCell>{r.contractRef ?? r.tenderRef ?? '—'}</TableCell>
                    <TableCell>{r.officerName ?? '—'}</TableCell>
                    <TableCell>{t(`stage.${r.stage}`, { defaultValue: r.stage })}</TableCell>
                    <TableCell>{formatDate(r.dateReceived, i18n.language)}</TableCell>
                    <TableCell>
                      {formatDate(r.targetDate, i18n.language)} {r.overdue && <Badge variant="destructive">{t('files.overdue')}</Badge>}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Card>
        )}
      </QueryState>
    </div>
  );
}
