import * as React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { contracts, files, requests, sessions } from '@/api/endpoints';
import type { RequestRow } from '@/api/types';
import { ApiError } from '@/api/errors';
import { newIdempotencyKey } from '@/api/client';
import { useSession } from '@/hooks/useSession';
import { useErrorText } from '@/hooks/useErrorText';
import { useConfig } from '@/lib/config';
import { ItemTypeBadge, PageHeader } from '@/components/app/common';
import { ActivityChecklist, ActivityPicker } from '@/components/domain/AgendaActivities';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { SimpleSelect } from '@/components/ui/select';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { formatInstant, kigaliLocalToIso } from '@/lib/time';

type PlannedRequest = { contractId: string; activities: string[] };

export function NewSessionPage() {
  const { t, i18n } = useTranslation();
  const errorText = useErrorText();
  const navigate = useNavigate();
  const { me } = useSession();
  const officerOf = (me?.committees ?? []).filter((c) => c.role !== 'MEMBER');
  const [committeeId, setCommitteeId] = React.useState(officerOf[0]?.committeeId ?? '');
  const [kind, setKind] = React.useState('ORDINARY');
  const [when, setWhen] = React.useState('');
  const [location, setLocation] = React.useState('');
  const [shortNotice, setShortNotice] = React.useState<{ earliest?: string } | null>(null);
  const [reason, setReason] = React.useState('');
  const [error, setError] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);
  // The first agenda, chosen while scheduling: files of the committee, or submitted requests.
  const [source, setSource] = React.useState<'normal' | 'request'>('normal');
  const [items, setItems] = React.useState<Record<string, string[]>>({});
  const [planned, setPlanned] = React.useState<Record<string, PlannedRequest>>({});
  const waiting = useQuery({ queryKey: ['requests', 'inbox', 'SUBMITTED'], queryFn: () => requests.inbox('SUBMITTED'), enabled: officerOf.length > 0 });
  // One logical "schedule" action: retries reuse the key.
  const key = React.useRef(newIdempotencyKey());

  const itemIds = Object.keys(items);
  const requestIds = Object.keys(planned);
  const chosenRequests = (waiting.data ?? []).filter((r) => requestIds.includes(r.id));
  const incomplete = itemIds.some((id) => items[id].length === 0)
    || chosenRequests.some((r) => planned[r.id].activities.length === 0 || (['C', 'D'].includes(r.itemType) && !planned[r.id].contractId));

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const s = await sessions.create(
        {
          committeeId, kind, scheduledAt: kigaliLocalToIso(when), location, shortNoticeReason: shortNotice ? reason : undefined,
          items: itemIds.map((fileId) => ({ fileId, activities: items[fileId] })),
          requests: requestIds.map((requestId) => ({ requestId, contractId: planned[requestId].contractId || undefined, activities: planned[requestId].activities })),
        },
        { idempotencyKey: key.current },
      );
      navigate(`/sessions/${s.id}`);
    } catch (err) {
      // Nothing was saved: the corrected attempt is a new action.
      key.current = newIdempotencyKey();
      if (err instanceof ApiError && err.code === 'session.short_notice_reason_required') {
        setShortNotice({ earliest: err.params.earliestWithoutShortNotice as string | undefined });
      } else {
        setError(errorText(err));
      }
    } finally {
      setBusy(false);
    }
  };

  if (officerOf.length === 0) {
    return <Alert variant="warning"><AlertDescription>{t('sessions.onlyOfficers')}</AlertDescription></Alert>;
  }

  return (
    <div className="max-w-3xl">
      <PageHeader title={t('sessions.schedule')} subtitle={t('sessions.scheduleHelp')} />
      <form onSubmit={submit} className="grid gap-4">
        <Card>
          <CardContent className="grid gap-4 pt-5">
            <Field id="committee" label={t('common.committee')} required>
              <SimpleSelect id="committee" value={committeeId} onChange={(v) => { setCommitteeId(v); setItems({}); }}
                options={officerOf.map((c) => ({ value: c.committeeId, label: `${c.code} — ${c.name}` }))} />
            </Field>
            <Field id="kind" label={t('sessions.kindLabel')} required>
              <SimpleSelect id="kind" value={kind} onChange={(v) => { setKind(v); setShortNotice(null); }}
                options={['ORDINARY', 'EXTRAORDINARY'].map((k) => ({ value: k, label: t(`sessions.kind.${k}`) }))} />
            </Field>
            <Field id="when" label={t('sessions.when')} hint={t('time.kigaliNote')} required>
              <Input id="when" type="datetime-local" value={when} onChange={(e) => { setWhen(e.target.value); setShortNotice(null); }} required />
            </Field>
            <Field id="location" label={t('sessions.location')}>
              <Input id="location" value={location} onChange={(e) => setLocation(e.target.value)} />
            </Field>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{t('sessions.agendaToSchedule')}</CardTitle>
            <p className="text-sm text-muted-foreground">{t('sessions.agendaToScheduleHelp')}</p>
          </CardHeader>
          <CardContent>
            <Tabs value={source} onValueChange={(v) => setSource(v as 'normal' | 'request')}>
              <TabsList className="mb-3 grid w-full grid-cols-2">
                <TabsTrigger value="normal">
                  {t('agenda.sourceNormal')}{itemIds.length > 0 && <Badge variant="secondary" className="ml-2">{itemIds.length}</Badge>}
                </TabsTrigger>
                <TabsTrigger value="request">
                  {t('agenda.sourceRequest')}
                  {(waiting.data?.length ?? 0) > 0 && <Badge variant={requestIds.length ? 'secondary' : 'warning'} className="ml-2">
                    {requestIds.length ? `${requestIds.length}/${waiting.data!.length}` : waiting.data!.length}
                  </Badge>}
                </TabsTrigger>
              </TabsList>
              <TabsContent value="normal">
                <NormalActivities committeeId={committeeId} items={items} onChange={setItems} />
              </TabsContent>
              <TabsContent value="request">
                <SubmittedRequests list={waiting.data ?? []} loading={waiting.isLoading} planned={planned} onChange={setPlanned} />
              </TabsContent>
            </Tabs>
            <p className="mt-3 text-sm font-medium">{t('sessions.agendaSummary', { files: itemIds.length, requests: requestIds.length })}</p>
          </CardContent>
        </Card>

        {shortNotice && (
          <Alert variant="warning">
            <AlertTitle>{t('sessions.shortNoticeTitle')}</AlertTitle>
            <AlertDescription>
              {t('sessions.shortNoticeBody', { earliest: shortNotice.earliest ? formatInstant(shortNotice.earliest, i18n.language) : '—' })}
              <Field id="reason" label={t('sessions.shortNoticeReason')} className="mt-3" required>
                <Textarea id="reason" value={reason} onChange={(e) => setReason(e.target.value)} required minLength={10} />
              </Field>
            </AlertDescription>
          </Alert>
        )}
        {error && <p className="text-sm text-destructive" role="alert">{error}</p>}
        <div>
          <Button type="submit" disabled={busy || !when || incomplete}>{shortNotice ? t('sessions.scheduleShortNotice') : t('sessions.schedule')}</Button>
          {incomplete && <p className="mt-1 text-xs text-muted-foreground">{t('sessions.agendaIncomplete')}</p>}
        </div>
      </form>
    </div>
  );
}

/** Open files of the committee; each one ticked needs at least one activity. */
function NormalActivities({ committeeId, items, onChange }: {
  committeeId: string; items: Record<string, string[]>; onChange: (v: Record<string, string[]>) => void;
}) {
  const { t } = useTranslation();
  const q = useQuery({ queryKey: ['files', { committeeId }], queryFn: () => files.list({ committeeId }), enabled: !!committeeId });
  const open = (q.data ?? []).filter((f) => f.stage !== 'CLOSED');
  const toggle = (id: string, on: boolean) => {
    const next = { ...items };
    if (on) next[id] = [];
    else delete next[id];
    onChange(next);
  };
  if (q.isLoading) return null;
  if (open.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        {t('sessions.noOpenFiles')} <Link className="text-primary underline" to={`/files/new?committeeId=${encodeURIComponent(committeeId)}`}>{t('files.new')}</Link>
      </p>
    );
  }
  return (
    <ul className="grid gap-2">
      {open.map((f) => (
        <li key={f.id} className="rounded-md border p-3">
          <div className="flex items-start gap-2">
            <Checkbox id={`nf-${f.id}`} checked={f.id in items} onCheckedChange={(v) => toggle(f.id, v === true)} />
            <Label htmlFor={`nf-${f.id}`} className="grid gap-0.5 font-normal">
              <span className="font-medium"><ItemTypeBadge type={f.itemType} /> {f.fileNumber} — {f.title}</span>
              <span className="text-xs text-muted-foreground">{t(`itemType.${f.itemType}`)} · {t(`stage.${f.stage}`)}</span>
            </Label>
          </div>
          {f.id in items && (
            <div className="mt-3 pl-6">
              <ActivityPicker fileId={f.id} value={items[f.id]} onChange={(codes) => onChange({ ...items, [f.id]: codes })} />
            </div>
          )}
        </li>
      ))}
    </ul>
  );
}

/** Submitted requests; each one ticked is accepted as a file of the committee and tabled. */
function SubmittedRequests({ list, loading, planned, onChange }: {
  list: RequestRow[]; loading: boolean; planned: Record<string, PlannedRequest>; onChange: (v: Record<string, PlannedRequest>) => void;
}) {
  const { t } = useTranslation();
  const cfg = useConfig();
  const needContracts = list.some((r) => r.id in planned && ['C', 'D'].includes(r.itemType));
  const cts = useQuery({ queryKey: ['contracts'], queryFn: contracts.list, enabled: needContracts });
  const allowed = (type: string) => (cfg.data?.values['agenda.activities'] as Record<string, string[]> | undefined)?.[type] ?? [];
  const toggle = (r: RequestRow, on: boolean) => {
    const next = { ...planned };
    if (on) {
      const match = (cts.data ?? []).find((c) => c.contractRef.trim().toLowerCase() === (r.contractRef ?? '').trim().toLowerCase());
      next[r.id] = { contractId: match?.id ?? '', activities: allowed(r.itemType) };
    } else {
      delete next[r.id];
    }
    onChange(next);
  };
  // Preselect the contract matching the requester's reference once contracts are loaded.
  React.useEffect(() => {
    if (!cts.data) return;
    let changed = false;
    const next = { ...planned };
    for (const r of list) {
      if (r.id in next && !next[r.id].contractId && ['C', 'D'].includes(r.itemType)) {
        const match = cts.data.find((c) => c.contractRef.trim().toLowerCase() === (r.contractRef ?? '').trim().toLowerCase());
        if (match) { next[r.id] = { ...next[r.id], contractId: match.id }; changed = true; }
      }
    }
    if (changed) onChange(next);
  }, [cts.data, list, planned, onChange]);
  if (loading) return null;
  if (list.length === 0) return <p className="text-sm text-muted-foreground">{t('agenda.noSubmittedRequests')}</p>;
  return (
    <ul className="grid gap-2">
      {list.map((r) => (
        <li key={r.id} className="rounded-md border p-3">
          <div className="flex items-start gap-2">
            <Checkbox id={`sr-${r.id}`} checked={r.id in planned} onCheckedChange={(v) => toggle(r, v === true)} />
            <Label htmlFor={`sr-${r.id}`} className="grid gap-0.5 font-normal">
              <span className="font-medium"><ItemTypeBadge type={r.itemType} /> {r.requestNumber} — {r.title}</span>
              <span className="text-xs text-muted-foreground">
                {r.requesterName} · {t(`itemType.${r.itemType}`)} · {t('agenda.requestDocuments', { count: r.documentCount })}
                {r.contractRef && ` · ${r.contractRef}`}
              </span>
            </Label>
            <Link className="ml-auto text-xs text-primary underline" to={`/requests/${r.id}`} target="_blank" rel="noreferrer">{t('requests.openRequest')}</Link>
          </div>
          {r.id in planned && (
            <div className="mt-3 grid gap-3 pl-6">
              {['C', 'D'].includes(r.itemType) && (
                <Field id={`sr-c-${r.id}`} label={t('files.contract')} hint={t('requests.contractMatchHint', { ref: r.contractRef ?? '' })} required>
                  <SimpleSelect id={`sr-c-${r.id}`} value={planned[r.id].contractId} placeholder={t('common.choose')}
                    onChange={(v) => onChange({ ...planned, [r.id]: { ...planned[r.id], contractId: v } })}
                    options={(cts.data ?? []).map((c) => ({ value: c.id, label: `${c.contractRef} — ${c.title}` }))} />
                </Field>
              )}
              <ActivityChecklist allowed={allowed(r.itemType)} outstanding={allowed(r.itemType)} value={planned[r.id].activities}
                onChange={(codes) => onChange({ ...planned, [r.id]: { ...planned[r.id], activities: codes } })} />
            </div>
          )}
        </li>
      ))}
    </ul>
  );
}
