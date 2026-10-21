import * as React from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Download, FilePlus2, Paperclip, Trash2, Upload } from 'lucide-react';
import { committees, contracts, identity, requests } from '@/api/endpoints';
import type { ItemType, RequestDetail, RequestDocumentRow, RequestInput, RequestRow } from '@/api/types';
import { useSession } from '@/hooks/useSession';
import { useMutate } from '@/hooks/useMutate';
import { useErrorText } from '@/hooks/useErrorText';
import { configList, useConfig } from '@/lib/config';
import { formatInstant } from '@/lib/time';
import { ItemTypeBadge, PageHeader, QueryState, StatusBadge } from '@/components/app/common';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { SimpleSelect } from '@/components/ui/select';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { toast } from '@/components/ui/toast';

const NEEDS_CONTRACT: ItemType[] = ['C', 'D'];
const EDITABLE = ['DRAFT', 'RETURNED'];
/** What a requester follows once the request is a file. */
const PROGRESS = ['RECEIVED', 'SCHEDULED', 'UNDER_REVIEW', 'RECOMMENDATION_ISSUED', 'SUBMITTED_TO_CBM', 'CLOSED'];

function formatSize(bytes: number): string {
  return bytes >= 1024 * 1024 ? `${(bytes / (1024 * 1024)).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

function RequestTable({ list, showRequester }: { list: RequestRow[]; showRequester: boolean }) {
  const { t, i18n } = useTranslation();
  return (
    <Card>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>{t('requests.number')}</TableHead>
            <TableHead>{t('requests.titleLabel')}</TableHead>
            {showRequester && <TableHead>{t('requests.requester')}</TableHead>}
            <TableHead>{t('requests.submitted')}</TableHead>
            <TableHead>{t('common.status')}</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {list.map((r) => (
            <TableRow key={r.id}>
              <TableCell className="whitespace-nowrap"><Link className="font-medium text-primary underline-offset-4 hover:underline" to={`/requests/${r.id}`}>{r.requestNumber}</Link></TableCell>
              <TableCell><ItemTypeBadge type={r.itemType} /> {r.title}</TableCell>
              {showRequester && <TableCell>{r.requesterName}</TableCell>}
              <TableCell className="whitespace-nowrap">{r.submittedAt ? formatInstant(r.submittedAt, i18n.language) : '—'}</TableCell>
              <TableCell>
                <StatusBadge ns="requests.status" value={r.status} />
                {r.fileNumber && <span className="ml-2 text-xs text-muted-foreground">{r.fileNumber} · {t(`stage.${r.fileStage}`)}</span>}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Card>
  );
}

export function MyRequestsPage() {
  const { t } = useTranslation();
  const q = useQuery({ queryKey: ['requests', 'mine'], queryFn: requests.mine });
  return (
    <div>
      <PageHeader title={t('requests.mineTitle')} subtitle={t('requests.mineSubtitle')}
        actions={<Button asChild><Link to="/requests/new"><FilePlus2 className="h-4 w-4" aria-hidden />{t('requests.new')}</Link></Button>} />
      <QueryState query={q} empty={(d) => d.length === 0}>{(list) => <RequestTable list={list} showRequester={false} />}</QueryState>
    </div>
  );
}

export function RequestInboxPage() {
  const { t } = useTranslation();
  const [status, setStatus] = React.useState('SUBMITTED');
  const q = useQuery({ queryKey: ['requests', 'inbox', status], queryFn: () => requests.inbox(status || undefined) });
  return (
    <div>
      <PageHeader title={t('requests.inboxTitle')} subtitle={t('requests.inboxSubtitle')}
        actions={<SimpleSelect id="rq-status" value={status} onChange={setStatus} allowEmpty emptyLabel={t('requests.allStatuses')}
          options={['SUBMITTED', 'ACCEPTED', 'RETURNED', 'WITHDRAWN'].map((s) => ({ value: s, label: t(`requests.status.${s}`) }))} />} />
      <QueryState query={q} empty={(d) => d.length === 0}>{(list) => <RequestTable list={list} showRequester />}</QueryState>
    </div>
  );
}

function RequestForm({ initial, onSave, saving, submitLabel }: {
  initial: RequestInput; onSave: (v: RequestInput) => void; saving: boolean; submitLabel: string;
}) {
  const { t } = useTranslation();
  const cfg = useConfig();
  const types = (configList(cfg.data?.values, 'request.types') as ItemType[]);
  const [f, setF] = React.useState<RequestInput>(initial);
  const set = <K extends keyof RequestInput>(k: K, v: RequestInput[K]) => setF((s) => ({ ...s, [k]: v }));
  const needsContract = NEEDS_CONTRACT.includes(f.itemType);
  return (
    <form className="grid gap-4 sm:grid-cols-2" onSubmit={(e) => { e.preventDefault(); onSave(f); }}>
      <Field id="rq-type" label={t('requests.type')} hint={t(`requests.typeHelp.${f.itemType}`, { defaultValue: '' })} required className="sm:col-span-2">
        <SimpleSelect id="rq-type" value={f.itemType} onChange={(v) => set('itemType', v as ItemType)}
          options={(types.length ? types : ['C', 'D', 'H'] as ItemType[]).map((x) => ({ value: x, label: t(`requests.types.${x}`, { defaultValue: t(`itemType.${x}`) }) }))} />
      </Field>
      <Field id="rq-title" label={t('requests.titleLabel')} required className="sm:col-span-2">
        <Input id="rq-title" value={f.title} onChange={(e) => set('title', e.target.value)} />
      </Field>
      <Field id="rq-desc" label={t('requests.description')} hint={t('requests.descriptionHint')} required className="sm:col-span-2">
        <Textarea id="rq-desc" rows={5} value={f.description} onChange={(e) => set('description', e.target.value)} />
      </Field>
      <Field id="rq-contract" label={t('requests.contractRef')} hint={t('requests.contractRefHint')} required={needsContract}>
        <Input id="rq-contract" value={f.contractRef ?? ''} onChange={(e) => set('contractRef', e.target.value)} />
      </Field>
      <Field id="rq-tender" label={t('files.tenderRef')}>
        <Input id="rq-tender" value={f.tenderRef ?? ''} onChange={(e) => set('tenderRef', e.target.value)} />
      </Field>
      <div className="sm:col-span-2">
        <Button type="submit" disabled={saving || !f.title.trim() || !f.description.trim() || (needsContract && !f.contractRef?.trim())}>{submitLabel}</Button>
      </div>
    </form>
  );
}

export function NewRequestPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const create = useMutate((v: RequestInput) => requests.create(v), {
    invalidate: [['requests']], onSuccess: (r) => navigate(`/requests/${r.id}`), success: t('requests.draftSaved'),
  });
  return (
    <div className="max-w-3xl">
      <PageHeader title={t('requests.new')} subtitle={t('requests.newHelp')} />
      <Alert variant="info" className="mb-4"><AlertDescription>{t('requests.scopeNotice')}</AlertDescription></Alert>
      <Card><CardContent className="pt-5">
        <RequestForm initial={{ itemType: 'C', title: '', description: '' }} saving={create.isPending}
          submitLabel={t('requests.saveDraft')} onSave={(v) => create.mutate(v)} />
      </CardContent></Card>
    </div>
  );
}

export function RequestPage() {
  const { id = '' } = useParams();
  const q = useQuery({ queryKey: ['request', id], queryFn: () => requests.get(id) });
  return <QueryState query={q}>{(r) => <RequestView r={r} />}</QueryState>;
}

function RequestView({ r }: { r: RequestDetail }) {
  const { t, i18n } = useTranslation();
  const { isOfficerAnywhere } = useSession();
  const editable = r.mine && EDITABLE.includes(r.status);
  const inv = [['request', r.id], ['requests']];
  const update = useMutate((v: RequestInput) => requests.update(r.id, { ...v, version: r.version }), { invalidate: inv, success: t('common.saved') });
  const withdraw = useMutate(() => requests.withdraw(r.id), { invalidate: inv, success: t('requests.withdrawn') });
  const [acceptOpen, setAcceptOpen] = React.useState(false);
  const [returnOpen, setReturnOpen] = React.useState(false);
  return (
    <div className="mx-auto grid max-w-3xl gap-4">
      <PageHeader title={<span className="flex flex-wrap items-center gap-2"><ItemTypeBadge type={r.itemType} />{r.requestNumber} — {r.title}</span>}
        subtitle={r.mine ? t(`requests.types.${r.itemType}`, { defaultValue: t(`itemType.${r.itemType}`) })
          : t('requests.fromRequester', { name: r.requesterName, job: r.requesterJobTitle ?? '' })}
        actions={<StatusBadge ns="requests.status" value={r.status} />} />

      {r.status === 'RETURNED' && r.decisionNote && (
        <Alert variant="warning">
          <AlertTitle>{t('requests.returnedBy', { name: r.decidedByName ?? '' })}</AlertTitle>
          <AlertDescription>{r.decisionNote}</AlertDescription>
        </Alert>
      )}

      {r.file && <ProgressCard r={r} />}

      <Card>
        <CardHeader><CardTitle>{t('requests.details')}</CardTitle></CardHeader>
        <CardContent>
          {editable ? (
            <RequestForm initial={{ itemType: r.itemType, title: r.title, description: r.description, contractRef: r.contractRef ?? '', tenderRef: r.tenderRef ?? '' }}
              saving={update.isPending} submitLabel={t('common.save')} onSave={(v) => update.mutate(v)} />
          ) : (
            <dl className="grid gap-3 text-sm sm:grid-cols-2">
              <div className="sm:col-span-2"><dt className="text-muted-foreground">{t('requests.description')}</dt><dd className="whitespace-pre-wrap">{r.description}</dd></div>
              <div><dt className="text-muted-foreground">{t('requests.contractRef')}</dt><dd>{r.contractRef ?? '—'}</dd></div>
              <div><dt className="text-muted-foreground">{t('files.tenderRef')}</dt><dd>{r.tenderRef ?? '—'}</dd></div>
              <div><dt className="text-muted-foreground">{t('requests.submitted')}</dt><dd>{r.submittedAt ? formatInstant(r.submittedAt, i18n.language) : '—'}</dd></div>
              {!r.mine && <div><dt className="text-muted-foreground">{t('requests.requester')}</dt><dd>{r.requesterName} · {r.requesterEmail}</dd></div>}
            </dl>
          )}
        </CardContent>
      </Card>

      <DocumentsCard requestId={r.id} documents={r.documents} editable={editable} />

      {editable && <SubmitCard r={r} />}

      {r.mine && ['DRAFT', 'SUBMITTED', 'RETURNED'].includes(r.status) && (
        <div><Button variant="outline" onClick={() => window.confirm(t('requests.withdrawConfirm')) && withdraw.mutate(undefined)}>{t('requests.withdraw')}</Button></div>
      )}

      {!r.mine && r.status === 'SUBMITTED' && isOfficerAnywhere && (
        <Card>
          <CardHeader><CardTitle>{t('requests.decide')}</CardTitle><p className="text-sm text-muted-foreground">{t('requests.decideHelp')}</p></CardHeader>
          <CardContent className="flex flex-wrap gap-2">
            <Button onClick={() => setAcceptOpen(true)}>{t('requests.accept')}</Button>
            <Button variant="outline" onClick={() => setReturnOpen(true)}>{t('requests.return')}</Button>
          </CardContent>
        </Card>
      )}
      {!r.mine && r.file && (
        <div><Button variant="outline" asChild><Link to={`/files/${r.file.id}`}>{t('requests.openFile', { file: r.file.fileNumber })}</Link></Button></div>
      )}
      <AcceptDialog r={r} open={acceptOpen} onClose={() => setAcceptOpen(false)} />
      <ReturnDialog r={r} open={returnOpen} onClose={() => setReturnOpen(false)} />
    </div>
  );
}

function ProgressCard({ r }: { r: RequestDetail }) {
  const { t, i18n } = useTranslation();
  const file = r.file!;
  const returned = file.stage === 'RETURNED_FOR_CORRECTION';
  const at = PROGRESS.indexOf(file.stage);
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('requests.progress')}</CardTitle>
        <p className="text-sm text-muted-foreground">{t('requests.registeredAs', { file: file.fileNumber, committee: file.committeeCode })}</p>
      </CardHeader>
      <CardContent className="grid gap-3">
        <ol className="grid gap-2 sm:grid-cols-3">
          {PROGRESS.map((s, i) => (
            <li key={s} className={`rounded-md border px-3 py-2 text-sm ${i <= at ? 'border-primary bg-primary/5 font-medium' : 'text-muted-foreground'}`}
              aria-current={i === at ? 'step' : undefined}>
              {i + 1}. {t(`stage.${s}`)}
            </li>
          ))}
        </ol>
        {returned && <Alert variant="warning"><AlertDescription>{t('stage.RETURNED_FOR_CORRECTION')}</AlertDescription></Alert>}
        {file.nextSession && (
          <p className="text-sm">{t('requests.nextSession', { session: file.nextSession.sessionNumber, date: formatInstant(file.nextSession.scheduledAt, i18n.language) })}</p>
        )}
      </CardContent>
    </Card>
  );
}

function DocumentsCard({ requestId, documents, editable }: { requestId: string; documents: RequestDocumentRow[]; editable: boolean }) {
  const { t, i18n } = useTranslation();
  const errorText = useErrorText();
  const inputRef = React.useRef<HTMLInputElement>(null);
  const inv = [['request', requestId]];
  const upload = useMutate((file: File) => requests.upload(requestId, file), { invalidate: inv, success: t('requests.documentAdded') });
  const remove = useMutate((docId: string) => requests.deleteDocument(requestId, docId), { invalidate: inv });
  const download = (d: RequestDocumentRow) =>
    void requests.downloadDocument(requestId, d).catch((e) => toast({ title: errorText(e), tone: 'destructive' }));
  return (
    <Card>
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <CardTitle>{t('requests.documents')}</CardTitle>
        {editable && (
          <>
            <input ref={inputRef} type="file" className="sr-only" aria-label={t('requests.addDocument')}
              accept=".pdf,.doc,.docx,.xlsx,.jpg,.jpeg,.png"
              onChange={(e) => { const f = e.target.files?.[0]; if (f) upload.mutate(f); e.target.value = ''; }} />
            <Button size="sm" disabled={upload.isPending} onClick={() => inputRef.current?.click()}><Upload className="h-4 w-4" aria-hidden />{t('requests.addDocument')}</Button>
          </>
        )}
      </CardHeader>
      <CardContent>
        {editable && <p className="mb-3 text-xs text-muted-foreground">{t('requests.documentsHint')}</p>}
        {documents.length === 0 ? <p className="text-sm text-muted-foreground">{t('requests.noDocuments')}</p> : (
          <ul className="grid gap-2">
            {documents.map((d) => (
              <li key={d.id} className="flex flex-wrap items-center gap-2 rounded-md border p-2 text-sm">
                <Paperclip className="h-4 w-4 text-muted-foreground" aria-hidden />
                <span className="min-w-0 flex-1 truncate">{d.fileName}</span>
                <span className="text-xs text-muted-foreground">{formatSize(d.sizeBytes)} · {formatInstant(d.uploadedAt, i18n.language)}</span>
                <Button size="sm" variant="ghost" aria-label={`${t('requests.download')}: ${d.fileName}`} onClick={() => download(d)}><Download className="h-4 w-4" aria-hidden /></Button>
                {editable && (
                  <Button size="sm" variant="ghost" aria-label={`${t('requests.removeDocument')}: ${d.fileName}`} onClick={() => remove.mutate(d.id)}>
                    <Trash2 className="h-4 w-4" aria-hidden />
                  </Button>
                )}
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}

function SubmitCard({ r }: { r: RequestDetail }) {
  const { t } = useTranslation();
  const [declared, setDeclared] = React.useState(false);
  const submit = useMutate(() => requests.submit(r.id), { invalidate: [['request', r.id], ['requests']], success: t('requests.submittedToast') });
  return (
    <Card className="border-primary">
      <CardHeader><CardTitle>{r.status === 'RETURNED' ? t('requests.resubmit') : t('requests.submit')}</CardTitle></CardHeader>
      <CardContent className="grid gap-3">
        <div className="flex items-start gap-2">
          <Checkbox id="rq-declare" checked={declared} onCheckedChange={(v) => setDeclared(v === true)} />
          <Label htmlFor="rq-declare" className="font-normal leading-snug">{t('requests.declaration')}</Label>
        </div>
        <div>
          <Button disabled={!declared || submit.isPending || r.documents.length === 0} onClick={() => submit.mutate(undefined)}>
            {r.status === 'RETURNED' ? t('requests.resubmit') : t('requests.submit')}
          </Button>
          {r.documents.length === 0 && <p className="mt-1 text-xs text-muted-foreground">{t('requests.attachFirst')}</p>}
        </div>
      </CardContent>
    </Card>
  );
}

function AcceptDialog({ r, open, onClose }: { r: RequestDetail; open: boolean; onClose: () => void }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const cs = useQuery({ queryKey: ['committees'], queryFn: committees.list, enabled: open });
  const needsContract = NEEDS_CONTRACT.includes(r.itemType);
  const cts = useQuery({ queryKey: ['contracts'], queryFn: contracts.list, enabled: open && needsContract });
  const users = useQuery({ queryKey: ['users'], queryFn: identity.users, enabled: open });
  const mine = (cs.data ?? []).filter((c) => c.status === 'ACTIVE' && c.myRole && c.myRole !== 'MEMBER');
  const [committeeId, setCommitteeId] = React.useState('');
  const [contractId, setContractId] = React.useState('');
  const [officerId, setOfficerId] = React.useState('');
  const [targetDate, setTargetDate] = React.useState('');
  React.useEffect(() => {
    if (!committeeId && mine.length === 1) setCommitteeId(mine[0].id);
  }, [mine, committeeId]);
  React.useEffect(() => {
    const match = (cts.data ?? []).find((c) => c.contractRef.trim().toLowerCase() === (r.contractRef ?? '').trim().toLowerCase());
    if (match && !contractId) setContractId(match.id);
  }, [cts.data, r.contractRef, contractId]);
  const accept = useMutate(() => requests.accept(r.id, {
    committeeId, contractId: contractId || undefined, procurementOfficerId: officerId || undefined, targetDate: targetDate || undefined,
  }), { invalidate: [['request', r.id], ['requests'], ['files']], onSuccess: (res) => { onClose(); navigate(`/files/${res.fileId}`); }, success: t('requests.acceptedToast') });
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent closeLabel={t('common.close')}>
        <DialogHeader>
          <DialogTitle>{t('requests.accept')}</DialogTitle>
          <DialogDescription>{t('requests.acceptHelp')}</DialogDescription>
        </DialogHeader>
        <div className="grid gap-3">
          <Field id="ac-committee" label={t('common.committee')} required>
            <SimpleSelect id="ac-committee" value={committeeId} onChange={setCommitteeId} placeholder={t('common.choose')}
              options={mine.map((c) => ({ value: c.id, label: `${c.code} — ${c.name}` }))} />
          </Field>
          {needsContract && (
            <Field id="ac-contract" label={t('files.contract')} hint={t('requests.contractMatchHint', { ref: r.contractRef ?? '' })} required>
              <SimpleSelect id="ac-contract" value={contractId} onChange={setContractId} placeholder={t('common.choose')}
                options={(cts.data ?? []).map((c) => ({ value: c.id, label: `${c.contractRef} — ${c.title}` }))} />
            </Field>
          )}
          {needsContract && (cts.data ?? []).length > 0 && !contractId && (
            <p className="text-xs"><Link className="text-primary underline" to="/contracts">{t('requests.registerContract')}</Link></p>
          )}
          <Field id="ac-officer" label={t('files.officer')}>
            <SimpleSelect id="ac-officer" value={officerId} onChange={setOfficerId} allowEmpty emptyLabel={t('requests.noOfficer')}
              options={(users.data ?? []).filter((u) => u.isProcurementOfficer).map((u) => ({ value: u.id, label: u.fullName }))} />
          </Field>
          <Field id="ac-target" label={t('files.target')} hint={t('requests.targetHint')}>
            <Input id="ac-target" type="date" value={targetDate} onChange={(e) => setTargetDate(e.target.value)} />
          </Field>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={!committeeId || (needsContract && !contractId) || accept.isPending} onClick={() => accept.mutate(undefined)}>{t('requests.accept')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function ReturnDialog({ r, open, onClose }: { r: RequestDetail; open: boolean; onClose: () => void }) {
  const { t } = useTranslation();
  const [reason, setReason] = React.useState('');
  const ret = useMutate(() => requests.returnToRequester(r.id, reason), {
    invalidate: [['request', r.id], ['requests']], onSuccess: onClose, success: t('requests.returnedToast'),
  });
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent closeLabel={t('common.close')}>
        <DialogHeader>
          <DialogTitle>{t('requests.return')}</DialogTitle>
          <DialogDescription>{t('requests.returnHelp')}</DialogDescription>
        </DialogHeader>
        <Field id="rt-reason" label={t('common.reason')} required>
          <Textarea id="rt-reason" value={reason} onChange={(e) => setReason(e.target.value)} />
        </Field>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={!reason.trim() || ret.isPending} onClick={() => ret.mutate(undefined)}>{t('requests.return')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** On a file registered from a request: who asked, and the supporting documents. */
export function FileRequestCard({ fileId }: { fileId: string }) {
  const { t, i18n } = useTranslation();
  const errorText = useErrorText();
  const q = useQuery({ queryKey: ['file-request', fileId], queryFn: () => requests.forFile(fileId) });
  const r = q.data;
  if (!r) return null;
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('requests.fromRequest', { number: r.requestNumber })}</CardTitle>
        <p className="text-sm text-muted-foreground">
          {t('requests.submittedBy', { name: r.requesterName, date: formatInstant(r.submittedAt, i18n.language) })} · <Link className="text-primary underline" to={`/requests/${r.id}`}>{t('requests.openRequest')}</Link>
        </p>
      </CardHeader>
      <CardContent>
        <ul className="grid gap-1 text-sm">
          {r.documents.map((d) => (
            <li key={d.id}>
              <button type="button" className="inline-flex items-center gap-1 text-primary underline-offset-4 hover:underline"
                onClick={() => void requests.downloadDocument(r.id, d).catch((e) => toast({ title: errorText(e), tone: 'destructive' }))}>
                <Paperclip className="h-4 w-4" aria-hidden />{d.fileName}
              </button>
              <span className="ml-2 text-xs text-muted-foreground">{formatSize(d.sizeBytes)}</span>
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}
