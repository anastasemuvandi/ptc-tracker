import * as React from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { CalendarPlus, UserPlus, Users } from 'lucide-react';
import { committees, files, sessions } from '@/api/endpoints';
import type { Appointment, CommitteeDetail, CommitteeRole, CompositionResult } from '@/api/types';
import { useSession } from '@/hooks/useSession';
import { useMutate } from '@/hooks/useMutate';
import { useConfig } from '@/lib/config';
import { PageHeader, QueryState, StatusBadge } from '@/components/app/common';
import { Card, CardContent, CardHeader, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { SimpleSelect } from '@/components/ui/select';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { daysUntil, formatDate, formatInstant, todayKigali } from '@/lib/time';
import { CompositionIssues } from './CompositionIssues';
import { PeriodLabel } from './PeriodLabel';

const ROLES: CommitteeRole[] = ['CHAIRPERSON', 'VICE_CHAIRPERSON', 'SECRETARY', 'MEMBER'];

export function CommitteeDetailPage() {
  const { id = '' } = useParams();
  const q = useQuery({ queryKey: ['committee', id], queryFn: () => committees.get(id) });
  return <QueryState query={q}>{(c) => <CommitteeView c={c} />}</QueryState>;
}

function CommitteeView({ c }: { c: CommitteeDetail }) {
  const { t, i18n } = useTranslation();
  const { hasRole, committeeRole } = useSession();
  const isCbm = hasRole('CBM');
  const myRole = committeeRole(c.id);
  const isChair = myRole === 'CHAIRPERSON' || myRole === 'VICE_CHAIRPERSON';
  const composition = useQuery({ queryKey: ['composition', c.id], queryFn: () => committees.composition(c.id) });
  const [appointOpen, setAppointOpen] = React.useState(false);
  const [replaceOpen, setReplaceOpen] = React.useState(false);
  const [periodOpen, setPeriodOpen] = React.useState(false);
  const [ending, setEnding] = React.useState<string | null>(null);
  const active = c.members.filter((m) => m.status === 'ACTIVE');
  return (
    <div className="grid gap-4">
      <PageHeader title={`${c.code} — ${c.name}`}
        subtitle={t('committee.headline', { size: c.size, quorum: c.quorumRequired })}
        actions={isCbm && <>
          <Button onClick={() => setAppointOpen(true)}><UserPlus className="h-4 w-4" aria-hidden />{t('committee.appoint')}</Button>
          <Button variant="outline" onClick={() => setReplaceOpen(true)}><Users className="h-4 w-4" aria-hidden />{t('committee.replace')}</Button>
          <Button variant="outline" onClick={() => setPeriodOpen(true)}><CalendarPlus className="h-4 w-4" aria-hidden />{t('committee.newPeriod')}</Button>
        </>} />
      <p className="text-sm">{t('committee.period')}: <PeriodLabel period={c.currentPeriod} />
        {c.currentPeriod?.appointmentRef && <span className="text-muted-foreground"> · {c.currentPeriod.appointmentRef}</span>}</p>
      {c.regulatorAuthRef && <p className="text-sm text-muted-foreground">{t('committee.authorisationFull', { ref: c.regulatorAuthRef, date: formatDate(c.regulatorAuthDate, i18n.language) })}</p>}
      <Tabs defaultValue="members">
        <TabsList>
          <TabsTrigger value="members">{t('committee.members')}</TabsTrigger>
          <TabsTrigger value="composition">{t('composition.title')}</TabsTrigger>
          {(isChair || isCbm) && <TabsTrigger value="requests">{t('committee.tableRequests')}</TabsTrigger>}
          <TabsTrigger value="periods">{t('committee.periods')}</TabsTrigger>
          {isCbm && <TabsTrigger value="replacements">{t('committee.replacements')}</TabsTrigger>}
        </TabsList>
        <TabsContent value="members">
          <Card>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('common.name')}</TableHead>
                  <TableHead>{t('committee.roleLabel')}</TableHead>
                  <TableHead>{t('committee.term')}</TableHead>
                  <TableHead>{t('committee.consecutiveTerm')}</TableHead>
                  <TableHead>{t('common.status')}</TableHead>
                  {isCbm && <TableHead />}
                </TableRow>
              </TableHeader>
              <TableBody>
                {c.members.map((m) => {
                  const left = daysUntil(m.termEnd);
                  return (
                    <TableRow key={m.membershipId}>
                      <TableCell className="font-medium">
                        {m.fullName} {m.isProcurementOfficer && <Badge variant="secondary">{t('committee.procurementOfficer')}</Badge>}
                      </TableCell>
                      <TableCell>{t(`committee.role.${m.role}`)}</TableCell>
                      <TableCell>
                        {formatDate(m.termStart, i18n.language)} – {formatDate(m.termEnd, i18n.language)}
                        {m.status === 'ACTIVE' && left <= 60 && <Badge variant={left < 0 ? 'destructive' : 'warning'} className="ml-2">{t('committee.termEndsIn', { count: left })}</Badge>}
                      </TableCell>
                      <TableCell>{m.consecutiveTerm}</TableCell>
                      <TableCell><StatusBadge ns="committee.membershipStatus" value={m.status} /></TableCell>
                      {isCbm && <TableCell>{m.status === 'ACTIVE' && <Button size="sm" variant="outline" onClick={() => setEnding(m.membershipId)}>{t('committee.endMembership')}</Button>}</TableCell>}
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </Card>
        </TabsContent>
        <TabsContent value="composition">
          <Card><CardContent className="pt-5"><QueryState query={composition}>{(r) => <CompositionIssues result={r} />}</QueryState></CardContent></Card>
        </TabsContent>
        {(isChair || isCbm) && <TabsContent value="requests"><TableRequests committeeId={c.id} canDecide={isChair} /></TabsContent>}
        <TabsContent value="periods"><Periods c={c} /></TabsContent>
        {isCbm && <TabsContent value="replacements"><Replacements committeeId={c.id} /></TabsContent>}
      </Tabs>
      {isCbm && <AppointDialog c={c} open={appointOpen} onClose={() => setAppointOpen(false)} />}
      {isCbm && <ReplaceDialog c={c} active={active} open={replaceOpen} onClose={() => setReplaceOpen(false)} />}
      {isCbm && <EndDialog committeeId={c.id} membershipId={ending} onClose={() => setEnding(null)} />}
      {isCbm && <NewPeriodDialog c={c} open={periodOpen} onClose={() => setPeriodOpen(false)} />}
    </div>
  );
}

function AppointmentFields({ c, value, onChange, idPrefix }: { c: CommitteeDetail; value: Appointment; onChange: (a: Appointment) => void; idPrefix: string }) {
  const { t } = useTranslation();
  const candidates = useQuery({ queryKey: ['candidates', c.id], queryFn: () => committees.candidates(c.id) });
  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <Field id={`${idPrefix}-user`} label={t('committee.person')} className="sm:col-span-2">
        <SimpleSelect id={`${idPrefix}-user`} value={value.userId} onChange={(v) => onChange({ ...value, userId: v })} placeholder={t('common.choose')}
          options={(candidates.data ?? []).map((u) => ({
            value: u.userId,
            label: `${u.fullName}${u.isProcurementOfficer ? ` (${t('committee.procurementOfficer')})` : ''}${u.eligible ? '' : ` — ${t(`errors.${u.ineligibleReason}`, { defaultValue: t('committee.ineligible') })}`}`,
          }))} />
      </Field>
      <Field id={`${idPrefix}-role`} label={t('committee.roleLabel')}>
        <SimpleSelect id={`${idPrefix}-role`} value={value.role} onChange={(v) => onChange({ ...value, role: v as CommitteeRole })}
          options={ROLES.map((r) => ({ value: r, label: t(`committee.role.${r}`) }))} />
      </Field>
      <Field id={`${idPrefix}-ref`} label={t('committee.appointmentRef')}>
        <Input id={`${idPrefix}-ref`} value={value.appointmentRef ?? ''} onChange={(e) => onChange({ ...value, appointmentRef: e.target.value })} />
      </Field>
      <Field id={`${idPrefix}-start`} label={t('committee.termStart')}>
        <Input id={`${idPrefix}-start`} type="date" value={value.termStart} onChange={(e) => onChange({ ...value, termStart: e.target.value })} />
      </Field>
      <Field id={`${idPrefix}-end`} label={t('committee.termEnd')} hint={t('committee.termEndHint')}>
        <Input id={`${idPrefix}-end`} type="date" value={value.termEnd ?? ''} onChange={(e) => onChange({ ...value, termEnd: e.target.value || undefined })} />
      </Field>
    </div>
  );
}

/** Appointment with a live composition check before anything is saved. */
function AppointDialog({ c, open, onClose }: { c: CommitteeDetail; open: boolean; onClose: () => void }) {
  const { t } = useTranslation();
  const [a, setA] = React.useState<Appointment>({ userId: '', role: 'MEMBER', termStart: todayKigali() });
  const [check, setCheck] = React.useState<CompositionResult | null>(null);
  React.useEffect(() => {
    if (!open || !a.userId) {
      setCheck(null);
      return;
    }
    let cancelled = false;
    committees.checkAppointment(c.id, a).then((r) => !cancelled && setCheck(r)).catch(() => !cancelled && setCheck(null));
    return () => {
      cancelled = true;
    };
  }, [open, a, c.id]);
  const blocked = check?.issues.some((i) => i.severity === 'ERROR') ?? false;
  const appoint = useMutate(() => committees.appoint(c.id, a), {
    invalidate: [['committee', c.id], ['composition', c.id], ['committees']], onSuccess: () => { onClose(); setA({ userId: '', role: 'MEMBER', termStart: todayKigali() }); },
    success: t('committee.appointed'),
  });
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-2xl" closeLabel={t('common.close')}>
        <DialogHeader>
          <DialogTitle>{t('committee.appoint')}</DialogTitle>
          <DialogDescription>{t('committee.appointHelp')}</DialogDescription>
        </DialogHeader>
        <AppointmentFields c={c} value={a} onChange={setA} idPrefix="ap" />
        {check && <CompositionIssues result={check} title={t('composition.liveCheck')} />}
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={!a.userId || blocked || appoint.isPending} onClick={() => appoint.mutate(undefined)}>{t('committee.appoint')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function ReplaceDialog({ c, active, open, onClose }: { c: CommitteeDetail; active: CommitteeDetail['members']; open: boolean; onClose: () => void }) {
  const { t } = useTranslation();
  const [scope, setScope] = React.useState<'SINGLE' | 'ALL'>('SINGLE');
  const [outgoing, setOutgoing] = React.useState<string[]>([]);
  const [reason, setReason] = React.useState('');
  const [appointments, setAppointments] = React.useState<Appointment[]>([{ userId: '', role: 'MEMBER', termStart: todayKigali() }]);
  // The Secretary is permanent and stays when all other members are replaced.
  const outIds = scope === 'ALL' ? active.filter((m) => m.role !== 'SECRETARY').map((m) => m.membershipId) : outgoing;
  const replace = useMutate(() => committees.replace(c.id, { scope, reason, outgoingMembershipIds: outIds, appointments: appointments.filter((x) => x.userId) }), {
    invalidate: [['committee', c.id], ['composition', c.id], ['tasks']],
    onSuccess: (r) => { onClose(); if (r.regulatorTask) alert(t('committee.regulatorTaskCreated', { due: r.regulatorTask.dueDate ?? '' })); },
    success: t('committee.replaced'),
  });
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-3xl" closeLabel={t('common.close')}>
        <DialogHeader>
          <DialogTitle>{t('committee.replace')}</DialogTitle>
          <DialogDescription>{t('committee.replaceHelp')}</DialogDescription>
        </DialogHeader>
        <Field id="rp-scope" label={t('committee.replaceScope')}>
          <SimpleSelect id="rp-scope" value={scope} onChange={(v) => setScope(v as 'SINGLE' | 'ALL')}
            options={[{ value: 'SINGLE', label: t('committee.scope.SINGLE') }, { value: 'ALL', label: t('committee.scope.ALL') }]} />
        </Field>
        {scope === 'ALL' ? (
          <Alert variant="warning"><AlertTitle>{t('committee.allReplaced')}</AlertTitle><AlertDescription>{t('committee.regulatorNotice')}</AlertDescription></Alert>
        ) : (
          <fieldset className="grid gap-2">
            <legend className="mb-1 text-sm font-medium">{t('committee.outgoing')}</legend>
            {active.map((m) => (
              <div key={m.membershipId} className="flex items-center gap-2">
                <Checkbox id={`out-${m.membershipId}`} checked={outgoing.includes(m.membershipId)}
                  onCheckedChange={(v) => setOutgoing((s) => (v === true ? [...s, m.membershipId] : s.filter((x) => x !== m.membershipId)))} />
                <Label htmlFor={`out-${m.membershipId}`}>{m.fullName} — {t(`committee.role.${m.role}`)}</Label>
              </div>
            ))}
          </fieldset>
        )}
        <Field id="rp-reason" label={t('committee.replaceReason')} hint={t('committee.replaceReasonHint')} required>
          <Textarea id="rp-reason" value={reason} onChange={(e) => setReason(e.target.value)} />
        </Field>
        <div className="grid gap-3">
          <p className="text-sm font-medium">{t('committee.incoming')}</p>
          {appointments.map((a, i) => (
            <div key={i} className="rounded-md border p-3">
              <AppointmentFields c={c} value={a} idPrefix={`rp${i}`} onChange={(v) => setAppointments((s) => s.map((x, j) => (j === i ? v : x)))} />
            </div>
          ))}
          <Button variant="outline" className="justify-self-start" onClick={() => setAppointments((s) => [...s, { userId: '', role: 'MEMBER', termStart: todayKigali() }])}>
            {t('committee.addIncoming')}
          </Button>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={replace.isPending || reason.trim().length < 10 || outIds.length === 0} onClick={() => replace.mutate(undefined)}>{t('committee.replace')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function EndDialog({ committeeId, membershipId, onClose }: { committeeId: string; membershipId: string | null; onClose: () => void }) {
  const { t } = useTranslation();
  const [reason, setReason] = React.useState('');
  const end = useMutate(() => committees.endMembership(committeeId, membershipId!, reason), {
    invalidate: [['committee', committeeId], ['composition', committeeId]], onSuccess: onClose, success: t('common.saved'),
  });
  return (
    <Dialog open={!!membershipId} onOpenChange={(o) => !o && onClose()}>
      <DialogContent closeLabel={t('common.close')}>
        <DialogHeader><DialogTitle>{t('committee.endMembership')}</DialogTitle></DialogHeader>
        <Field id="end-reason" label={t('common.reason')} required><Textarea id="end-reason" value={reason} onChange={(e) => setReason(e.target.value)} /></Field>
        <DialogFooter><Button disabled={!reason.trim() || end.isPending} onClick={() => end.mutate(undefined)}>{t('committee.endMembership')}</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function Periods({ c }: { c: CommitteeDetail }) {
  const { t } = useTranslation();
  return (
    <Card>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t('committee.period')}</TableHead>
            <TableHead>{t('committee.appointmentRef')}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {c.periods.map((p) => (
            <TableRow key={p.id}>
              <TableCell><PeriodLabel period={p} /></TableCell>
              <TableCell>{p.appointmentRef ?? '—'}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Card>
  );
}

/** Reappoint the committee: same name, a new period ending automatically. */
function NewPeriodDialog({ c, open, onClose }: { c: CommitteeDetail; open: boolean; onClose: () => void }) {
  const { t } = useTranslation();
  const cfg = useConfig();
  const months = Number(cfg.data?.values['committee.periodMonths'] ?? 36);
  const [startsOn, setStartsOn] = React.useState(todayKigali());
  const [appointmentRef, setAppointmentRef] = React.useState('');
  const save = useMutate(() => committees.reappoint(c.id, { startsOn, appointmentRef: appointmentRef || undefined }), {
    invalidate: [['committee', c.id], ['committees']], onSuccess: onClose, success: t('common.saved'),
  });
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent closeLabel={t('common.close')}>
        <DialogHeader>
          <DialogTitle>{t('committee.newPeriod')}</DialogTitle>
          <DialogDescription>{t('committee.newPeriodHelp', { name: c.name })}</DialogDescription>
        </DialogHeader>
        <div className="grid gap-3">
          <Field id="np-start" label={t('committee.periodStart')} hint={t('committee.periodStartHint', { months })} required>
            <Input id="np-start" type="date" value={startsOn} onChange={(e) => setStartsOn(e.target.value)} />
          </Field>
          <Field id="np-ref" label={t('committee.appointmentRef')}><Input id="np-ref" value={appointmentRef} onChange={(e) => setAppointmentRef(e.target.value)} /></Field>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={!startsOn || save.isPending} onClick={() => save.mutate(undefined)}>{t('committee.newPeriod')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function TableRequests({ committeeId, canDecide }: { committeeId: string; canDecide: boolean }) {
  const { t, i18n } = useTranslation();
  const q = useQuery({ queryKey: ['table-requests', committeeId], queryFn: () => committees.tableRequests(committeeId) });
  const upcoming = useQuery({ queryKey: ['sessions', committeeId, 'planned'], queryFn: () => sessions.list({ committeeId }), enabled: canDecide });
  const planned = (upcoming.data ?? []).filter((s) => s.status === 'PLANNED');
  const [sessionId, setSessionId] = React.useState('');
  const decide = useMutate((v: { id: string; accept: boolean }) => files.decideTableRequest(v.id, { accept: v.accept, sessionId: v.accept && sessionId ? sessionId : undefined }),
    { invalidate: [['table-requests', committeeId]], success: t('common.saved') });
  return (
    <Card>
      <CardHeader><CardDescription>{t('committee.tableRequestsHelp')}</CardDescription></CardHeader>
      <CardContent>
        <QueryState query={q} empty={(d) => d.length === 0}>
          {(list) => (
            <ul className="grid gap-3">
              {list.map((r) => (
                <li key={r.id} className="grid gap-2 rounded-md border p-3 text-sm">
                  <div className="flex flex-wrap items-center gap-2">
                    <Link className="font-medium text-primary underline" to={`/files/${r.fileId}`}>{r.fileNumber}</Link>
                    <span>{r.title}</span>
                    <StatusBadge ns="committee.requestStatus" value={r.status} />
                  </div>
                  <p className="text-muted-foreground">{r.requestedByName} · {formatInstant(r.requestedAt, i18n.language)}</p>
                  {r.reason && <p>{r.reason}</p>}
                  {canDecide && r.status === 'PENDING' && (
                    <div className="flex flex-wrap items-end gap-2">
                      <div className="min-w-[200px]">
                        <SimpleSelect value={sessionId} onChange={setSessionId} allowEmpty emptyLabel={t('committee.noSessionYet')}
                          options={planned.map((s) => ({ value: s.id, label: s.sessionNumber }))} />
                      </div>
                      <Button size="sm" onClick={() => decide.mutate({ id: r.id, accept: true })}>{t('committee.accept')}</Button>
                      <Button size="sm" variant="outline" onClick={() => decide.mutate({ id: r.id, accept: false })}>{t('committee.decline')}</Button>
                    </div>
                  )}
                </li>
              ))}
            </ul>
          )}
        </QueryState>
      </CardContent>
    </Card>
  );
}

function Replacements({ committeeId }: { committeeId: string }) {
  const { t, i18n } = useTranslation();
  const q = useQuery({ queryKey: ['replacements', committeeId], queryFn: () => committees.replacements(committeeId) });
  return (
    <Card>
      <CardContent className="pt-5">
        <QueryState query={q} empty={(d) => d.length === 0}>
          {(list) => (
            <ul className="grid gap-2 text-sm">
              {list.map((r) => (
                <li key={r.id} className="rounded-md border p-3">
                  <p className="font-medium">{t(`committee.scope.${r.scope}`)} · {formatInstant(r.performedAt, i18n.language)}</p>
                  <p>{r.reason}</p>
                  {r.regulatorTaskId && <Link to={`/n/task/${r.regulatorTaskId}`} className="text-primary underline">{t('committee.regulatorTask')}</Link>}
                </li>
              ))}
            </ul>
          )}
        </QueryState>
      </CardContent>
    </Card>
  );
}
