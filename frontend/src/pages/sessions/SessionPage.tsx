import * as React from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { ChevronRight, FileDown, FileText, Plus, Trash2, WifiOff } from 'lucide-react';
import { sessions, files, decisions } from '@/api/endpoints';
import type { AttendanceStatus, ItemType, SessionDetail } from '@/api/types';
import { submitQueueable } from '@/lib/outbox';
import { liveQuorum } from '@/lib/quorum';
import { formatInstant } from '@/lib/time';
import { useErrorText } from '@/hooks/useErrorText';
import { useMutate } from '@/hooks/useMutate';
import { useOnline } from '@/hooks/useOnline';
import { ItemTypeBadge, PageHeader, QueryState, StatusBadge } from '@/components/app/common';
import { QuorumIndicator } from '@/components/domain/QuorumIndicator';
import { AttendanceToggle } from '@/components/domain/AttendanceToggle';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { SimpleSelect } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { toast } from '@/components/ui/toast';

const ITEM_TYPES: ItemType[] = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I'];

export function SessionPage() {
  const { id = '' } = useParams();
  const q = useQuery({ queryKey: ['session', id], queryFn: () => sessions.get(id), refetchInterval: 20_000 });
  return <QueryState query={q}>{(d) => <SessionView detail={d} />}</QueryState>;
}

function SessionView({ detail }: { detail: SessionDetail }) {
  const { t, i18n } = useTranslation();
  const errorText = useErrorText();
  const qc = useQueryClient();
  const online = useOnline();
  const { session, agenda, members, myRole } = detail;
  const isOfficer = !!myRole && myRole !== 'MEMBER';
  // Marks recorded on this device (possibly still queued offline) overlay the server state.
  const [localMarks, setLocalMarks] = React.useState<Record<string, AttendanceStatus>>({});
  const [addOpen, setAddOpen] = React.useState(false);

  const markFor = (membershipId: string) => localMarks[membershipId] ?? members.find((m) => m.membershipId === membershipId)?.attendance?.status ?? null;
  const present = members.filter((m) => markFor(m.membershipId) === 'PRESENT').length;
  const quorum = liveQuorum(detail.quorum, present);

  const mark = async (membershipId: string, name: string, status: AttendanceStatus) => {
    setLocalMarks((s) => ({ ...s, [membershipId]: status }));
    const base = members.find((m) => m.membershipId === membershipId)?.attendance?.version;
    try {
      const r = await submitQueueable('attendance', 'PUT', `/api/sessions/${session.id}/attendance/${membershipId}`,
        { status, clientRecordedAt: new Date().toISOString(), baseVersion: base }, `${session.sessionNumber} · ${name}`);
      if (r.queued) toast({ title: t('offline.attendanceQueued'), tone: 'warning' });
      else void qc.invalidateQueries({ queryKey: ['session', session.id] });
    } catch (e) {
      setLocalMarks((s) => {
        const copy = { ...s };
        delete copy[membershipId];
        return copy;
      });
      toast({ title: t('common.actionFailed'), description: errorText(e), tone: 'destructive' });
    }
  };

  const setStatus = useMutate((status: string) => sessions.setStatus(session.id, status), { invalidate: [['session', session.id]] });
  const removeItem = useMutate((itemId: string) => sessions.removeAgendaItem(session.id, itemId), { invalidate: [['session', session.id]] });

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title={<span>{t('sessions.session')} {session.sessionNumber}</span>}
        subtitle={`${detail.committee.code} — ${detail.committee.name} · ${formatInstant(session.scheduledAt, i18n.language)}${session.location ? ` · ${session.location}` : ''}`}
        actions={<>
          <StatusBadge ns="sessions.status" value={session.status} />
          <Badge variant="outline">{t(`sessions.kind.${session.kind}`)}</Badge>
        </>}
      />
      {session.shortNotice && (
        <Alert variant="warning" className="mb-3">
          <AlertDescription>{t('sessions.convenedShortNotice', { reason: session.shortNoticeReason ?? '' })}</AlertDescription>
        </Alert>
      )}

      <div className="sticky top-14 z-20 -mx-3 mb-4 bg-background/95 px-3 py-2 backdrop-blur sm:static sm:mx-0 sm:p-0">
        <QuorumIndicator quorum={quorum} />
      </div>

      {/* Agenda ---------------------------------------------------------- */}
      <Card className="mb-4">
        <CardHeader className="flex-row items-center justify-between space-y-0">
          <CardTitle>{t('sessions.agenda')}</CardTitle>
          {isOfficer && session.status !== 'HELD' && session.status !== 'CANCELLED' && (
            <Button size="sm" onClick={() => setAddOpen(true)}><Plus className="h-4 w-4" aria-hidden />{t('sessions.addItem')}</Button>
          )}
        </CardHeader>
        <CardContent>
          {agenda.length === 0 && <p className="text-sm text-muted-foreground">{t('sessions.noAgenda')}</p>}
          <ol className="grid gap-2">
            {[...agenda].sort((a, b) => a.position - b.position).map((item) => (
              <li key={item.id} className="flex min-w-0 items-stretch gap-1 rounded-md border">
                <Link to={item.itemType === 'I' ? '#' : `/sessions/${session.id}/items/${item.id}`}
                  className="flex min-h-[56px] min-w-0 flex-1 items-center gap-3 p-3 hover:bg-muted"
                  onClick={(e) => item.itemType === 'I' && e.preventDefault()}>
                  <span className="w-6 text-sm text-muted-foreground">{item.position}.</span>
                  <ItemTypeBadge type={item.itemType} />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-medium">
                      {item.itemType === 'I' ? t(`agenda.adminKind.${item.adminKind}`) : `${item.fileNumber} — ${item.title ?? ''}`}
                    </span>
                    <span className="block text-xs text-muted-foreground">{t(`itemType.${item.itemType}`)}</span>
                  </span>
                  {item.decisionStatus && <StatusBadge ns="decision.status" value={item.decisionStatus} />}
                  {item.itemType !== 'I' && <ChevronRight className="h-4 w-4 text-muted-foreground" aria-hidden />}
                </Link>
                {isOfficer && session.status === 'PLANNED' && (
                  <Button variant="ghost" size="icon" className="h-auto" aria-label={t('sessions.removeItem')}
                    onClick={() => removeItem.mutate(item.id)}>
                    <Trash2 className="h-4 w-4" aria-hidden />
                  </Button>
                )}
              </li>
            ))}
          </ol>
        </CardContent>
      </Card>

      {/* Attendance ------------------------------------------------------ */}
      <Card className="mb-4">
        <CardHeader>
          <CardTitle>{t('attendance.title')}</CardTitle>
          {!online && isOfficer && (
            <p className="flex items-center gap-2 text-sm text-muted-foreground"><WifiOff className="h-4 w-4" aria-hidden />{t('offline.attendanceAllowed')}</p>
          )}
        </CardHeader>
        <CardContent className="grid gap-4">
          {members.map((m) => {
            const status = markFor(m.membershipId);
            const unnotified = m.unnotifiedAbsence ?? (!!status && status !== 'PRESENT' && !m.apology?.timely);
            return (
              <div key={m.membershipId} className="grid gap-2 border-b pb-4 last:border-0 last:pb-0">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium">{m.fullName}</span>
                  <span className="text-xs text-muted-foreground">{t(`committee.role.${m.role}`)}</span>
                  {m.apology && (
                    <Badge variant={m.apology.timely ? 'secondary' : 'warning'}>
                      {m.apology.timely ? t('attendance.apologyReceived') : t('attendance.apologyLate')}
                    </Badge>
                  )}
                  {unnotified && <Badge variant="destructive">{t('attendance.unnotified')}</Badge>}
                </div>
                {m.apology && <p className="text-sm text-muted-foreground">“{m.apology.reason}”</p>}
                {isOfficer ? (
                  <AttendanceToggle name={m.fullName} value={status} disabled={session.status === 'CANCELLED'}
                    onChange={(s) => void mark(m.membershipId, m.fullName, s)} />
                ) : (
                  <span className="text-sm">{status ? t(`attendance.status.${status}`) : t('attendance.notRecorded')}</span>
                )}
              </div>
            );
          })}
        </CardContent>
      </Card>

      {myRole && session.status === 'PLANNED' && <ApologyCard detail={detail} />}

      {isOfficer && (
        <Card>
          <CardHeader><CardTitle>{t('sessions.manage')}</CardTitle></CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            {session.status === 'PLANNED' && <Button onClick={() => setStatus.mutate('IN_PROGRESS')}>{t('sessions.open')}</Button>}
            {session.status === 'IN_PROGRESS' && <Button onClick={() => setStatus.mutate('HELD')}>{t('sessions.close')}</Button>}
            {session.status === 'PLANNED' && (
              <Button variant="outline" onClick={() => window.confirm(t('sessions.cancelConfirm')) && setStatus.mutate('CANCELLED')}>{t('sessions.cancel')}</Button>
            )}
            <Button variant="outline" asChild><Link to={`/sessions/${session.id}/minutes`}><FileText className="h-4 w-4" aria-hidden />{t('minutes.title')}</Link></Button>
            <Button variant="outline" onClick={() => void decisions.exportDeclarations(session.id).catch((e) => toast({ title: errorText(e), tone: 'destructive' }))}>
              <FileDown className="h-4 w-4" aria-hidden />{t('coi.export')}
            </Button>
          </CardContent>
        </Card>
      )}
      {!isOfficer && (
        <div className="mt-2"><Button variant="outline" asChild><Link to={`/sessions/${session.id}/minutes`}>{t('minutes.title')}</Link></Button></div>
      )}

      {isOfficer && <AddAgendaItemDialog open={addOpen} onOpenChange={setAddOpen} detail={detail} />}
    </div>
  );
}

export function ApologyCard({ detail }: { detail: SessionDetail }) {
  const { t, i18n } = useTranslation();
  const errorText = useErrorText();
  const [kind, setKind] = React.useState<'ABSENT_WITH_PERMISSION' | 'ON_LEAVE'>('ABSENT_WITH_PERMISSION');
  const [reason, setReason] = React.useState('');
  const [sent, setSent] = React.useState(false);
  const qc = useQueryClient();
  if (detail.myApology) {
    return (
      <Alert variant="info" className="mb-4">
        <AlertDescription>{t('attendance.yourApology', { at: formatInstant(detail.myApology.submittedAt, i18n.language) })}</AlertDescription>
      </Alert>
    );
  }
  return (
    <Card className="mb-4">
      <CardHeader>
        <CardTitle>{t('attendance.sendApology')}</CardTitle>
        <p className="text-sm text-muted-foreground">{t('attendance.apologyHelp')}</p>
      </CardHeader>
      <CardContent>
        {sent ? <p className="text-sm">{t('attendance.apologySent')}</p> : (
          <form className="grid gap-3" onSubmit={async (e) => {
            e.preventDefault();
            try {
              const r = await submitQueueable('apology', 'POST', `/api/sessions/${detail.session.id}/apologies`,
                { kind, reason, clientRecordedAt: new Date().toISOString() }, `${detail.session.sessionNumber} · ${t('attendance.apology')}`);
              setSent(true);
              toast({ title: r.queued ? t('offline.apologyQueued') : t('attendance.apologySent'), tone: r.queued ? 'warning' : 'success' });
              if (!r.queued) void qc.invalidateQueries({ queryKey: ['session', detail.session.id] });
            } catch (err) {
              toast({ title: t('common.actionFailed'), description: errorText(err), tone: 'destructive' });
            }
          }}>
            <Field id="apology-kind" label={t('attendance.apologyKind')}>
              <SimpleSelect id="apology-kind" value={kind} onChange={(v) => setKind(v as typeof kind)}
                options={(['ABSENT_WITH_PERMISSION', 'ON_LEAVE'] as const).map((k) => ({ value: k, label: t(`attendance.status.${k}`) }))} />
            </Field>
            <Field id="apology-reason" label={t('attendance.reason')} required>
              <Textarea id="apology-reason" value={reason} onChange={(e) => setReason(e.target.value)} required />
            </Field>
            <Button type="submit" size="lg">{t('attendance.sendApology')}</Button>
          </form>
        )}
      </CardContent>
    </Card>
  );
}

function AddAgendaItemDialog({ open, onOpenChange, detail }: { open: boolean; onOpenChange: (o: boolean) => void; detail: SessionDetail }) {
  const { t } = useTranslation();
  const [itemType, setItemType] = React.useState<ItemType>('A');
  const [fileId, setFileId] = React.useState('');
  const [adminKind, setAdminKind] = React.useState('ADOPT_MINUTES');
  const [adminNote, setAdminNote] = React.useState('');
  const fileList = useQuery({
    queryKey: ['files', { committeeId: detail.committee.id, type: itemType }],
    queryFn: () => files.list({ committeeId: detail.committee.id, type: itemType }),
    enabled: open && itemType !== 'I',
  });
  const add = useMutate(
    () => sessions.addAgendaItem(detail.session.id, itemType === 'I'
      ? { itemType, adminKind, adminNote: adminNote || undefined }
      : { itemType, fileId }),
    { invalidate: [['session', detail.session.id]], onSuccess: () => onOpenChange(false), success: t('sessions.itemAdded') },
  );
  const candidates = (fileList.data ?? []).filter((f) => !['CLOSED'].includes(f.stage));
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent closeLabel={t('common.close')}>
        <DialogHeader>
          <DialogTitle>{t('sessions.addItem')}</DialogTitle>
          <DialogDescription>{t('agenda.scopeNotice')}</DialogDescription>
        </DialogHeader>
        <div className="grid gap-4">
          <Field id="item-type" label={t('agenda.itemType')}>
            <SimpleSelect id="item-type" value={itemType} onChange={(v) => { setItemType(v as ItemType); setFileId(''); }}
              options={ITEM_TYPES.map((x) => ({ value: x, label: `${x} — ${t(`itemType.${x}`)}` }))} />
          </Field>
          <p className="text-xs text-muted-foreground">{t(`itemTypeHelp.${itemType}`)}</p>
          {itemType === 'I' ? (
            <>
              <Field id="admin-kind" label={t('agenda.adminKindLabel')}>
                <SimpleSelect id="admin-kind" value={adminKind} onChange={setAdminKind}
                  options={['ADOPT_MINUTES', 'WORK_PLANNING', 'MEMBER_INDUCTION'].map((k) => ({ value: k, label: t(`agenda.adminKind.${k}`) }))} />
              </Field>
              <Field id="admin-note" label={t('common.note')}>
                <Textarea id="admin-note" value={adminNote} onChange={(e) => setAdminNote(e.target.value)} />
              </Field>
              <Alert variant="info"><AlertDescription>{t('agenda.adminNoRecommendation')}</AlertDescription></Alert>
            </>
          ) : (
            <Field id="file" label={t('common.file')} hint={candidates.length === 0 ? t('agenda.noFilesOfType') : undefined}>
              <SimpleSelect id="file" value={fileId} onChange={setFileId} placeholder={t('agenda.chooseFile')}
                disabled={candidates.length === 0}
                options={candidates.map((f) => ({ value: f.id, label: `${f.fileNumber} — ${f.title}` }))} />
              {candidates.length === 0 && !fileList.isLoading && (
                <Link className="mt-2 inline-flex items-center gap-1 text-sm font-medium text-primary underline-offset-4 hover:underline"
                  to={`/files/new?committeeId=${encodeURIComponent(detail.committee.id)}&type=${itemType}`}>
                  <Plus className="h-4 w-4" aria-hidden />{t('agenda.registerFile')}
                </Link>
              )}
            </Field>
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>{t('common.cancel')}</Button>
          <Button disabled={add.isPending || (itemType !== 'I' && !fileId)} onClick={() => add.mutate(undefined)}>{t('sessions.addItem')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
