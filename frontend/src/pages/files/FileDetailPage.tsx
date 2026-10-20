import * as React from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { files, committees, identity } from '@/api/endpoints';
import type { FileDetail } from '@/api/types';
import { useSession } from '@/hooks/useSession';
import { useMutate } from '@/hooks/useMutate';
import { ItemTypeBadge, KeyValue, PageHeader, QueryState, StatusBadge } from '@/components/app/common';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { SimpleSelect } from '@/components/ui/select';
import { Badge } from '@/components/ui/badge';
import { formatDate, formatInstant } from '@/lib/time';
import { EvaluationView } from '@/pages/evaluation/EvaluationView';
import { ChecklistView } from '@/pages/evaluation/ChecklistView';
import { AmendmentPanel } from '@/pages/contracts/AmendmentPanel';
import { ExtensionPanel } from '@/pages/contracts/ExtensionPanel';

export function FileDetailPage() {
  const { id = '' } = useParams();
  const q = useQuery({ queryKey: ['file', id], queryFn: () => files.get(id) });
  return <QueryState query={q}>{(f) => <FileView file={f} />}</QueryState>;
}

function FileView({ file }: { file: FileDetail }) {
  const { t } = useTranslation();
  const hasChecklist = ['B', 'E', 'F', 'G'].includes(file.itemType);
  const special = file.itemType === 'A' ? 'evaluation' : file.itemType === 'C' ? 'amendment' : file.itemType === 'D' ? 'extension' : hasChecklist ? 'checklist' : null;
  return (
    <div>
      <PageHeader
        title={<span className="flex flex-wrap items-center gap-2"><ItemTypeBadge type={file.itemType} /> {file.fileNumber}</span>}
        subtitle={file.title}
        actions={<>
          <Badge variant="outline">{t(`stage.${file.stage}`, { defaultValue: file.stage })}</Badge>
          {file.overdue && <Badge variant="destructive">{t('files.overdue')}</Badge>}
        </>}
      />
      <Tabs defaultValue={special ?? 'overview'}>
        <TabsList>
          {special === 'evaluation' && <TabsTrigger value="evaluation">{t('evaluation.tab')}</TabsTrigger>}
          {special === 'amendment' && <TabsTrigger value="amendment">{t('amendment.tab')}</TabsTrigger>}
          {special === 'extension' && <TabsTrigger value="extension">{t('extension.tab')}</TabsTrigger>}
          {hasChecklist && <TabsTrigger value="checklist">{t('checklist.tab')}</TabsTrigger>}
          <TabsTrigger value="overview">{t('files.overview')}</TabsTrigger>
          <TabsTrigger value="history">{t('files.history')}</TabsTrigger>
        </TabsList>
        {special === 'evaluation' && <TabsContent value="evaluation"><EvaluationView file={file} /></TabsContent>}
        {special === 'amendment' && <TabsContent value="amendment"><AmendmentPanel file={file} /></TabsContent>}
        {special === 'extension' && <TabsContent value="extension"><ExtensionPanel file={file} /></TabsContent>}
        {hasChecklist && <TabsContent value="checklist"><ChecklistView file={file} /></TabsContent>}
        <TabsContent value="overview"><Overview file={file} /></TabsContent>
        <TabsContent value="history"><History file={file} /></TabsContent>
      </Tabs>
    </div>
  );
}

function Overview({ file }: { file: FileDetail }) {
  const { t, i18n } = useTranslation();
  const { hasRole, committeeRole } = useSession();
  const role = committeeRole(file.committeeId);
  const isOfficer = !!role && role !== 'MEMBER';
  const [dialog, setDialog] = React.useState<null | 'stage' | 'reassign' | 'officer' | 'table'>(null);
  return (
    <div className="grid gap-4 lg:grid-cols-3">
      <Card className="lg:col-span-2">
        <CardHeader><CardTitle>{t('files.overview')}</CardTitle></CardHeader>
        <CardContent>
          <KeyValue items={[
            [t('common.committee'), file.committeeCode],
            [t('files.type'), t(`itemType.${file.itemType}`)],
            [t('files.tenderRef'), file.tenderRef],
            [t('files.contract'), file.contractId ? <Link className="text-primary underline" to={`/contracts/${file.contractId}`}>{file.contractRef}</Link> : '—'],
            [t('files.procurementType'), file.procurementType ? t(`procurementType.${file.procurementType}`) : '—'],
            [t('files.method'), file.procurementMethod],
            [t('files.officer'), file.officerName],
            [t('files.received'), formatDate(file.dateReceived, i18n.language)],
            [t('files.target'), formatDate(file.targetDate, i18n.language)],
            ...(file.itemType === 'A' ? [[t('files.bidValidityExpiry'), formatDate(file.bidValidityExpiry, i18n.language)]] as [string, string][] : []),
            ...(file.itemType === 'H' ? [[t('files.referralRef'), file.referralRef], [t('files.referralReason'), file.referralReason]] as [string, string | null][] : []),
          ]} />
        </CardContent>
      </Card>
      <Card>
        <CardHeader><CardTitle>{t('common.actions')}</CardTitle></CardHeader>
        <CardContent className="grid gap-2">
          {isOfficer && <Button variant="outline" onClick={() => setDialog('stage')}>{t('files.changeStage')}</Button>}
          {hasRole('CBM') && <Button variant="outline" onClick={() => setDialog('reassign')}>{t('files.reassign')}</Button>}
          {hasRole('CBM') && <Button variant="outline" onClick={() => setDialog('officer')}>{t('files.assignOfficer')}</Button>}
          {role === 'MEMBER' && <Button variant="outline" onClick={() => setDialog('table')}>{t('files.requestTabling')}</Button>}
          {!isOfficer && !hasRole('CBM') && role !== 'MEMBER' && <p className="text-sm text-muted-foreground">{t('common.readOnlyView')}</p>}
        </CardContent>
      </Card>
      <Card className="lg:col-span-3">
        <CardHeader><CardTitle>{t('files.decisions')}</CardTitle></CardHeader>
        <CardContent>
          {file.decisions.length === 0 && <p className="text-sm text-muted-foreground">{t('decision.noneYet')}</p>}
          <ul className="grid gap-2">
            {file.decisions.map((d) => (
              <li key={d.id} className="flex flex-wrap items-center gap-2 text-sm">
                <StatusBadge ns="decision.status" value={d.status} />
                <span className="font-medium">{t(`recommendation.${d.recommendationCode}`, { defaultValue: d.recommendationCode })}</span>
                {d.sessionNumber && <span className="text-muted-foreground">· {t('sessions.session')} {d.sessionNumber}</span>}
                {d.issuedAt && <span className="text-muted-foreground">· {formatInstant(d.issuedAt, i18n.language)}</span>}
              </li>
            ))}
          </ul>
          {file.cbmResponses.length > 0 && (
            <div className="mt-4">
              <h3 className="mb-2 font-semibold">{t('cbm.responses')}</h3>
              <ul className="grid gap-2 text-sm">
                {file.cbmResponses.map((r, i) => (
                  <li key={r.id ?? i}>
                    <StatusBadge ns="cbm.response" value={r.response} /> {formatInstant(r.respondedAt, i18n.language)}
                    {r.reasons && <p className="mt-1 text-muted-foreground">{r.reasons}</p>}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </CardContent>
      </Card>
      <StageDialog file={file} open={dialog === 'stage'} onClose={() => setDialog(null)} />
      <ReassignDialog file={file} open={dialog === 'reassign'} onClose={() => setDialog(null)} />
      <OfficerDialog file={file} open={dialog === 'officer'} onClose={() => setDialog(null)} />
      <TablingDialog file={file} open={dialog === 'table'} onClose={() => setDialog(null)} />
    </div>
  );
}

function History({ file }: { file: FileDetail }) {
  const { t, i18n } = useTranslation();
  return (
    <Card>
      <CardContent className="pt-5">
        <ol className="grid gap-3 border-l-2 pl-4">
          {file.stageHistory.map((h, i) => (
            <li key={i} className="text-sm">
              <p className="font-medium">{h.fromStage ? `${t(`stage.${h.fromStage}`, { defaultValue: h.fromStage })} → ` : ''}{t(`stage.${h.toStage}`, { defaultValue: h.toStage })}</p>
              <p className="text-muted-foreground">{formatInstant(h.changedAt, i18n.language)}{h.changedByName ? ` · ${h.changedByName}` : ''}</p>
              {h.note && <p>{h.note}</p>}
            </li>
          ))}
          {file.reassignments.map((r, i) => (
            <li key={`r${i}`} className="text-sm">
              <p className="font-medium">{t('files.reassigned', { from: r.fromCommitteeCode ?? '', to: r.toCommitteeCode ?? '' })}</p>
              <p className="text-muted-foreground">{formatInstant(r.performedAt, i18n.language)}</p>
              <p>{r.reason}</p>
            </li>
          ))}
        </ol>
      </CardContent>
    </Card>
  );
}

function StageDialog({ file, open, onClose }: { file: FileDetail; open: boolean; onClose: () => void }) {
  const { t } = useTranslation();
  const [stage, setStage] = React.useState('');
  const [note, setNote] = React.useState('');
  const m = useMutate(() => files.setStage(file.id, stage, note || undefined), { invalidate: [['file', file.id]], onSuccess: onClose, success: t('common.saved') });
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent closeLabel={t('common.close')}>
        <DialogHeader><DialogTitle>{t('files.changeStage')}</DialogTitle></DialogHeader>
        <Field id="stage" label={t('files.stage')}>
          <SimpleSelect id="stage" value={stage} onChange={setStage} placeholder={t('common.choose')}
            options={file.allowedStages.map((s) => ({ value: s, label: t(`stage.${s}`, { defaultValue: s }) }))} />
        </Field>
        <Field id="stage-note" label={t('common.note')}><Textarea id="stage-note" value={note} onChange={(e) => setNote(e.target.value)} /></Field>
        <DialogFooter><Button disabled={!stage || m.isPending} onClick={() => m.mutate(undefined)}>{t('common.save')}</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function ReassignDialog({ file, open, onClose }: { file: FileDetail; open: boolean; onClose: () => void }) {
  const { t } = useTranslation();
  const cs = useQuery({ queryKey: ['committees'], queryFn: committees.list, enabled: open });
  const [to, setTo] = React.useState('');
  const [reason, setReason] = React.useState('');
  const m = useMutate(() => files.reassign(file.id, to, reason), { invalidate: [['file', file.id]], onSuccess: onClose, success: t('common.saved') });
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent closeLabel={t('common.close')}>
        <DialogHeader><DialogTitle>{t('files.reassign')}</DialogTitle></DialogHeader>
        <Field id="to" label={t('files.toCommittee')}>
          <SimpleSelect id="to" value={to} onChange={setTo} placeholder={t('common.choose')}
            options={(cs.data ?? []).filter((c) => c.id !== file.committeeId && c.status === 'ACTIVE').map((c) => ({ value: c.id, label: `${c.code} — ${c.name}` }))} />
        </Field>
        <Field id="reassign-reason" label={t('common.reason')} hint={t('files.reassignHint')} required>
          <Textarea id="reassign-reason" value={reason} onChange={(e) => setReason(e.target.value)} />
        </Field>
        <DialogFooter><Button disabled={!to || !reason.trim() || m.isPending} onClick={() => m.mutate(undefined)}>{t('files.reassign')}</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function currentFinancialYear(): string {
  // Rwanda's fiscal year runs 1 July – 30 June.
  const now = new Date();
  const y = now.getUTCFullYear();
  return now.getUTCMonth() >= 6 ? `${y}/${y + 1}` : `${y - 1}/${y}`;
}

function OfficerDialog({ file, open, onClose }: { file: FileDetail; open: boolean; onClose: () => void }) {
  const { t } = useTranslation();
  const users = useQuery({ queryKey: ['users'], queryFn: identity.users, enabled: open });
  const [officer, setOfficer] = React.useState(file.procurementOfficerId ?? '');
  const [fy, setFy] = React.useState(currentFinancialYear());
  const m = useMutate(() => files.assignOfficer(file.id, officer, fy), { invalidate: [['file', file.id]], onSuccess: onClose, success: t('common.saved') });
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent closeLabel={t('common.close')}>
        <DialogHeader><DialogTitle>{t('files.assignOfficer')}</DialogTitle></DialogHeader>
        <Field id="officer" label={t('files.officer')}>
          <SimpleSelect id="officer" value={officer} onChange={setOfficer} placeholder={t('common.choose')}
            options={(users.data ?? []).filter((u) => u.isProcurementOfficer).map((u) => ({ value: u.id, label: u.fullName }))} />
        </Field>
        <Field id="fy" label={t('files.financialYear')}><Input id="fy" value={fy} onChange={(e) => setFy(e.target.value)} /></Field>
        <DialogFooter><Button disabled={!officer || m.isPending} onClick={() => m.mutate(undefined)}>{t('common.save')}</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function TablingDialog({ file, open, onClose }: { file: FileDetail; open: boolean; onClose: () => void }) {
  const { t } = useTranslation();
  const [reason, setReason] = React.useState('');
  const m = useMutate(() => files.requestTabling(file.id, reason), { onSuccess: onClose, success: t('files.tablingRequested') });
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent closeLabel={t('common.close')}>
        <DialogHeader><DialogTitle>{t('files.requestTabling')}</DialogTitle></DialogHeader>
        <p className="text-sm text-muted-foreground">{t('files.tablingHelp')}</p>
        <Field id="tabling-reason" label={t('common.reason')}><Textarea id="tabling-reason" value={reason} onChange={(e) => setReason(e.target.value)} /></Field>
        <DialogFooter><Button disabled={m.isPending} onClick={() => m.mutate(undefined)}>{t('files.requestTabling')}</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
