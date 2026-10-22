import * as React from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Plus } from 'lucide-react';
import { committees } from '@/api/endpoints';
import { useSession } from '@/hooks/useSession';
import { useMutate } from '@/hooks/useMutate';
import { useConfig } from '@/lib/config';
import { PageHeader, QueryState, StatusBadge } from '@/components/app/common';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { formatDate } from '@/lib/time';
import { PeriodLabel } from './PeriodLabel';

export function CommitteesPage() {
  const { t } = useTranslation();
  const { hasRole } = useSession();
  const q = useQuery({ queryKey: ['committees'], queryFn: committees.list });
  const [open, setOpen] = React.useState(false);
  return (
    <div>
      <PageHeader title={t('committee.title')} subtitle={t('committee.subtitle')}
        actions={hasRole('CBM') && <Button onClick={() => setOpen(true)}><Plus className="h-4 w-4" aria-hidden />{t('committee.create')}</Button>} />
      <QueryState query={q} empty={(d) => d.length === 0}>
        {(list) => (
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {list.map((c) => (
              <Card key={c.id} className="hover:border-primary">
                <Link to={`/committees/${c.id}`} className="block">
                  <CardHeader>
                    <div className="flex items-center justify-between gap-2">
                      <CardTitle>{c.code}</CardTitle>
                      <StatusBadge ns="committee.status" value={c.status} />
                    </div>
                    <p className="text-sm text-muted-foreground">{c.name}</p>
                    <p className="text-sm">{t('committee.period')}: <PeriodLabel period={c.currentPeriod} /></p>
                  </CardHeader>
                  <CardContent className="grid gap-1 text-sm">
                    <p>{t('committee.membersOfSize', { members: c.activeMembers, size: c.size })}</p>
                    <p>{t('committee.quorumRequired', { count: c.quorumRequired })}</p>
                    {c.myRole && <p className="font-medium text-primary">{t('committee.yourRole', { role: t(`committee.role.${c.myRole}`) })}</p>}
                    {c.regulatorAuthRef && <p className="text-muted-foreground">{t('committee.authorisation', { ref: c.regulatorAuthRef })}</p>}
                  </CardContent>
                </Link>
              </Card>
            ))}
          </div>
        )}
      </QueryState>
      <CreateCommitteeDialog open={open} onClose={() => setOpen(false)} />
    </div>
  );
}

/** The period's last day: start + the configured months − 1 day (as the server computes it). */
function periodEnd(start: string, months: number): string {
  const [y, m, d] = start.split('-').map(Number);
  const target = new Date(Date.UTC(y, m - 1 + months, 1));
  const lastDay = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate();
  target.setUTCDate(Math.min(d, lastDay) - 1);
  return target.toISOString().slice(0, 10);
}

/** The CBM names the committee and schedules its start; the end date follows from the period length. */
function CreateCommitteeDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t, i18n } = useTranslation();
  const cfg = useConfig();
  const [name, setName] = React.useState('');
  const [periodStart, setPeriodStart] = React.useState('');
  const months = Number(cfg.data?.values['committee.periodMonths'] ?? 36);
  const create = useMutate(() => committees.create({ name: name.trim(), periodStart }),
    { invalidate: [['committees']], onSuccess: onClose, success: t('committee.created') });
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent closeLabel={t('common.close')}>
        <DialogHeader>
          <DialogTitle>{t('committee.create')}</DialogTitle>
          <DialogDescription>{t('committee.createHelp', { months })}</DialogDescription>
        </DialogHeader>
        <div className="grid gap-3">
          <Field id="cm-name" label={t('common.name')} hint={t('committee.nameUniqueHint')} required>
            <Input id="cm-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="PTC A" />
          </Field>
          <Field id="cm-start" label={t('committee.periodStart')} required
            hint={periodStart ? t('committee.periodEndsOn', { date: formatDate(periodEnd(periodStart, months), i18n.language), months }) : t('committee.periodStartHint', { months })}>
            <Input id="cm-start" type="date" value={periodStart} onChange={(e) => setPeriodStart(e.target.value)} />
          </Field>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={create.isPending || !name.trim() || !periodStart} onClick={() => create.mutate(undefined)}>{t('committee.create')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
