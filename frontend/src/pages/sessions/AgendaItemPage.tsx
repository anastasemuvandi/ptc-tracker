import * as React from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { ArrowLeft } from 'lucide-react';
import { sessions, decisions, files } from '@/api/endpoints';
import { newIdempotencyKey } from '@/api/client';
import type { DecisionState, ItemType, SessionDetail } from '@/api/types';
import { useMutate } from '@/hooks/useMutate';
import { useOnline } from '@/hooks/useOnline';
import { ItemTypeBadge, QueryState, StatusBadge } from '@/components/app/common';
import { DeclarationPanel } from '@/components/domain/DeclarationPanel';
import { ThresholdBox, VotingPanel } from '@/components/domain/VotingPanel';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { SimpleSelect } from '@/components/ui/select';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { formatInstant, formatMoney } from '@/lib/time';

export function AgendaItemPage() {
  const { id = '', itemId = '' } = useParams();
  const session = useQuery({ queryKey: ['session', id], queryFn: () => sessions.get(id) });
  return <QueryState query={session}>{(s) => <ItemView detail={s} itemId={itemId} />}</QueryState>;
}

function ItemView({ detail, itemId }: { detail: SessionDetail; itemId: string }) {
  const { t } = useTranslation();
  const item = detail.agenda.find((a) => a.id === itemId);
  const qc = useQueryClient();
  const state = useQuery({
    queryKey: ['decision', itemId],
    queryFn: () => decisions.forItem(itemId),
    enabled: !!item,
    refetchInterval: (q) => (q.state.data?.decision?.status === 'VOTING' ? 5_000 : false),
  });
  if (!item) return <Alert variant="destructive"><AlertDescription>{t('errors.not_found')}</AlertDescription></Alert>;
  const isOfficer = !!detail.myRole && detail.myRole !== 'MEMBER';
  const isChair = detail.myRole === 'CHAIRPERSON' || detail.myRole === 'VICE_CHAIRPERSON';
  const refresh = () => {
    void qc.invalidateQueries({ queryKey: ['decision', itemId] });
    void qc.invalidateQueries({ queryKey: ['session', detail.session.id] });
  };

  return (
    <div className="mx-auto grid max-w-3xl gap-4">
      <Link to={`/sessions/${detail.session.id}`} className="flex items-center gap-1 text-sm text-primary">
        <ArrowLeft className="h-4 w-4" aria-hidden />{t('sessions.session')} {detail.session.sessionNumber}
      </Link>
      <div className="flex flex-wrap items-center gap-2">
        <ItemTypeBadge type={item.itemType} long />
        <h1 className="text-xl font-bold">{item.fileNumber}</h1>
      </div>
      <p className="text-muted-foreground">{item.title}</p>
      {item.fileId && <Link to={`/files/${item.fileId}`} className="text-sm text-primary underline">{t('agenda.openFileRecord')}</Link>}

      {/* 1. Conflict of interest before the file is opened */}
      <DeclarationPanel agendaItemId={item.id} />

      {/* 2. Decision and vote */}
      <QueryState query={state}>
        {(s) => (
          <Card>
            <CardHeader className="flex-row items-center justify-between space-y-0">
              <CardTitle>{t('decision.title')}</CardTitle>
              {s.decision && <StatusBadge ns="decision.status" value={s.decision.status} />}
            </CardHeader>
            <CardContent className="grid gap-4">
              {!s.decision && isOfficer && <DecisionDraftForm itemId={item.id} itemType={item.itemType} onSaved={refresh} />}
              {!s.decision && !isOfficer && <p className="text-sm text-muted-foreground">{t('decision.noneYet')}</p>}
              {s.decision && <DecisionBody state={s} isOfficer={isOfficer} isChair={isChair} itemType={item.itemType} onChange={refresh} />}
            </CardContent>
          </Card>
        )}
      </QueryState>
    </div>
  );
}

function DecisionBody({ state, isOfficer, isChair, itemType, onChange }: {
  state: DecisionState; isOfficer: boolean; isChair: boolean; itemType: ItemType; onChange: () => void;
}) {
  const { t, i18n } = useTranslation();
  const online = useOnline();
  const d = state.decision!;
  const open = useMutate(() => decisions.openVoting(d.id), { onSuccess: onChange });
  const close = useMutate(() => decisions.closeVoting(d.id), { onSuccess: onChange });
  const issueKey = React.useRef(newIdempotencyKey());
  const issue = useMutate(() => decisions.issue(d.id, { idempotencyKey: issueKey.current }), {
    onSuccess: onChange, success: t('decision.issued'),
  });
  const submit = useMutate(() => decisions.submitToCbm(d.id), { onSuccess: onChange, success: t('decision.submittedToCbm') });
  const dissents = state.votes.filter((v) => v.choice !== 'FOR');
  return (
    <>
      <div className="rounded-md bg-muted p-3">
        <p className="text-xs uppercase text-muted-foreground">{t('decision.recommendation')}</p>
        <p className="font-semibold">{t(`recommendation.${d.recommendationCode}`, { defaultValue: d.recommendationCode })}</p>
        <p className="mt-1 whitespace-pre-wrap text-sm">{d.recommendationText}</p>
        {d.recommendedBidderRef && <p className="mt-1 text-sm">{t('decision.bidderRef')}: <strong>{d.recommendedBidderRef}</strong></p>}
        {d.contractValue && <p className="text-sm">{t('decision.contractValue')}: <strong>{formatMoney(d.contractValue, d.currency, i18n.language)}</strong></p>}
      </div>
      <ThresholdBox state={state} />
      {d.status === 'DRAFT' && isOfficer && (
        <>
          <Alert variant="info"><AlertDescription>{t('decision.openVotingHelp')}</AlertDescription></Alert>
          <Button size="lg" disabled={!online || open.isPending} onClick={() => open.mutate(undefined)}>{t('decision.openVoting')}</Button>
        </>
      )}
      {d.status === 'VOTING' && <VotingPanel state={state} onVoted={onChange} />}
      {d.status === 'VOTING' && isOfficer && (
        <Button variant="outline" disabled={!online || close.isPending} onClick={() => window.confirm(t('decision.closeConfirm')) && close.mutate(undefined)}>
          {t('decision.closeVoting')}
        </Button>
      )}
      {d.status === 'ADOPTED' && isChair && (
        <>
          <Alert variant="info"><AlertDescription>{t('decision.issueHelp')}</AlertDescription></Alert>
          <Button size="lg" disabled={!online || issue.isPending} onClick={() => issue.mutate(undefined)}>{t('decision.issue')}</Button>
          {!online && <p className="text-sm text-destructive">{t('errors.offline.blocked')}</p>}
        </>
      )}
      {d.status === 'NOT_ADOPTED' && <Alert variant="destructive"><AlertDescription>{t('decision.notAdoptedHelp')}</AlertDescription></Alert>}
      {d.status === 'ISSUED' && (
        <>
          <Alert variant="success"><AlertDescription>{t('decision.issuedAt', { at: formatInstant(d.issuedAt ?? null, i18n.language) })}</AlertDescription></Alert>
          {isOfficer && <Button variant="outline" className="justify-self-start" disabled={submit.isPending} onClick={() => submit.mutate(undefined)}>{t('decision.submitToCbm')}</Button>}
        </>
      )}
      {itemType && state.votes.length > 0 && (
        <div>
          <h3 className="mb-2 font-semibold">{t('voting.votes')}</h3>
          <ul className="grid gap-1 text-sm">
            {state.votes.map((v) => (
              <li key={v.userId}>
                <strong>{v.fullName}</strong>: {t(`voting.choice.${v.choice}`)}
              </li>
            ))}
          </ul>
        </div>
      )}
      {dissents.length > 0 && (
        <div className="rounded-md border-2 border-warning p-3">
          <h3 className="mb-2 font-semibold">{t('voting.dissentsRecorded')}</h3>
          <ul className="grid gap-2 text-sm">
            {dissents.map((v) => (
              <li key={v.userId}><strong>{v.fullName}</strong> ({t(`voting.choice.${v.choice}`)}): “{v.dissentReason}”</li>
            ))}
          </ul>
        </div>
      )}
    </>
  );
}

function DecisionDraftForm({ itemId, itemType, onSaved }: { itemId: string; itemType: ItemType; onSaved: () => void }) {
  const { t } = useTranslation();
  const wf = useQuery({ queryKey: ['workflow', itemType], queryFn: () => files.workflow(itemType) });
  const [code, setCode] = React.useState('');
  const [text, setText] = React.useState('');
  const [bidder, setBidder] = React.useState('');
  const [value, setValue] = React.useState('');
  const [currency, setCurrency] = React.useState('RWF');
  const save = useMutate(() => decisions.draft(itemId, {
    recommendationCode: code,
    recommendationText: text,
    recommendedBidderRef: bidder || undefined,
    contractValue: value || undefined,
    currency: value ? currency : undefined,
  }), { onSuccess: onSaved, success: t('decision.draftSaved') });
  return (
    <form className="grid gap-3" onSubmit={(e) => { e.preventDefault(); save.mutate(undefined); }}>
      <Field id="rec-code" label={t('decision.recommendation')} required>
        <SimpleSelect id="rec-code" value={code} onChange={setCode} placeholder={t('common.choose')}
          options={(wf.data?.recommendationCodes ?? []).map((c) => ({ value: c, label: t(`recommendation.${c}`, { defaultValue: c }) }))} />
      </Field>
      <Field id="rec-text" label={t('decision.grounds')} hint={t('decision.groundsHint')} required>
        <Textarea id="rec-text" rows={5} value={text} onChange={(e) => setText(e.target.value)} required />
      </Field>
      {itemType === 'A' && (
        <div className="grid gap-3 sm:grid-cols-3">
          <Field id="bidder" label={t('decision.bidderRef')}><Input id="bidder" value={bidder} onChange={(e) => setBidder(e.target.value)} /></Field>
          <Field id="value" label={t('decision.contractValue')}><Input id="value" inputMode="decimal" pattern="^\d+(\.\d{1,2})?$" value={value} onChange={(e) => setValue(e.target.value)} /></Field>
          <Field id="currency" label={t('common.currency')}><Input id="currency" maxLength={3} value={currency} onChange={(e) => setCurrency(e.target.value.toUpperCase())} /></Field>
        </div>
      )}
      {(itemType === 'C' || itemType === 'D') && <Alert variant="info"><AlertDescription>{t('decision.structuredFromFile')}</AlertDescription></Alert>}
      <Button type="submit" disabled={!code || !text.trim() || save.isPending}>{t('decision.saveDraft')}</Button>
    </form>
  );
}
