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
import { SimpleSelect } from '@/components/ui/select';
import { Alert, AlertDescription } from '@/components/ui/alert';

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
      <CreateCommitteeDialog open={open} onClose={() => setOpen(false)} existing={q.data?.filter((c) => c.status === 'ACTIVE').length ?? 0} />
    </div>
  );
}

function CreateCommitteeDialog({ open, onClose, existing }: { open: boolean; onClose: () => void; existing: number }) {
  const { t } = useTranslation();
  const cfg = useConfig();
  const sizes = (cfg.data?.values['committee.allowedSizes'] as number[] | undefined) ?? [5, 7];
  const minimum = Number(cfg.data?.values['committee.minimumSize'] ?? 3);
  const options = Array.from(new Set([...sizes, minimum])).sort((a, b) => a - b);
  const [name, setName] = React.useState('');
  const [code, setCode] = React.useState('');
  const [size, setSize] = React.useState(String(sizes[0] ?? 5));
  const [ref, setRef] = React.useState('');
  const [date, setDate] = React.useState('');
  const needsAuth = existing >= 1;
  const create = useMutate(() => committees.create({ name, code, size: Number(size), regulatorAuthRef: ref || undefined, regulatorAuthDate: date || undefined }),
    { invalidate: [['committees']], onSuccess: onClose, success: t('committee.created') });
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent closeLabel={t('common.close')}>
        <DialogHeader>
          <DialogTitle>{t('committee.create')}</DialogTitle>
          <DialogDescription>{t('committee.createHelp')}</DialogDescription>
        </DialogHeader>
        <div className="grid gap-3">
          <Field id="cm-name" label={t('common.name')} required><Input id="cm-name" value={name} onChange={(e) => setName(e.target.value)} placeholder="PTC A" /></Field>
          <Field id="cm-code" label={t('committee.code')} required><Input id="cm-code" value={code} onChange={(e) => setCode(e.target.value.toUpperCase())} placeholder="PTC-A" /></Field>
          <Field id="cm-size" label={t('committee.size')} hint={t('committee.sizeHint', { sizes: sizes.join(', '), minimum })}>
            <SimpleSelect id="cm-size" value={size} onChange={setSize} options={options.map((s) => ({ value: String(s), label: String(s) }))} />
          </Field>
          {needsAuth && <Alert variant="warning"><AlertDescription>{t('committee.authorisationRequired')}</AlertDescription></Alert>}
          <Field id="cm-ref" label={t('committee.regulatorAuthRef')} required={needsAuth}><Input id="cm-ref" value={ref} onChange={(e) => setRef(e.target.value)} /></Field>
          <Field id="cm-date" label={t('committee.regulatorAuthDate')} required={needsAuth}><Input id="cm-date" type="date" value={date} onChange={(e) => setDate(e.target.value)} /></Field>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={create.isPending || !name || !code || (needsAuth && (!ref || !date))} onClick={() => create.mutate(undefined)}>{t('committee.create')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
