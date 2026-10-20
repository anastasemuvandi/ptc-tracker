import * as React from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { CalendarClock, Clock, FileSignature, ScrollText, UserCheck } from 'lucide-react';
import { contracts, dashboards, decisions, evaluation, files, minutes, sessions, tasks } from '@/api/endpoints';
import { useSession } from '@/hooks/useSession';
import { Countdown, QueryState } from '@/components/app/common';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { DeclarationPanel } from '@/components/domain/DeclarationPanel';
import { ThresholdBox, VotingPanel } from '@/components/domain/VotingPanel';
import { ApologyCard } from '@/pages/sessions/SessionPage';
import { TaskCard } from '@/pages/committee/TasksPage';
import { PendingCard } from '@/pages/cbm/CbmPendingPage';
import { daysUntil, formatDate, formatInstant } from '@/lib/time';

/**
 * Notification landing pages: each answers one question and offers one
 * action, so a reminder opened on a phone needs no navigation.
 */
function Landing({ icon: Icon, question, children }: { icon: React.ComponentType<{ className?: string }>; question: string; children: React.ReactNode }) {
  return (
    <div className="mx-auto grid max-w-lg gap-4">
      <Card>
        <CardHeader>
          <Icon className="h-8 w-8 text-primary" aria-hidden />
          <CardTitle className="text-xl">{question}</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4">{children}</CardContent>
      </Card>
    </div>
  );
}

export function BidValidityLanding() {
  const { t, i18n } = useTranslation();
  const { fileId = '' } = useParams();
  const f = useQuery({ queryKey: ['file', fileId], queryFn: () => files.get(fileId) });
  const ev = useQuery({ queryKey: ['evaluation', fileId], queryFn: () => evaluation.get(fileId) });
  return (
    <Landing icon={Clock} question={t('landing.bidValidity.question')}>
      <QueryState query={ev}>
        {(e) => (
          <>
            <p className="text-sm">{f.data ? `${f.data.fileNumber} — ${f.data.title}` : ''}</p>
            <p className="font-semibold">{t(`evaluation.bidValidity.state.${e.bidValidity.state}`)}</p>
            <div className="flex flex-wrap gap-3">
              <Countdown days={e.bidValidity.daysToRequestDeadline} warnAt={10} label={t('evaluation.bidValidity.requestDeadline', { date: formatDate(e.bidValidity.regulatorRequestDeadline, i18n.language) })} />
              <Countdown days={e.bidValidity.daysToExpiry} warnAt={14} label={t('evaluation.bidValidity.expiry', { date: formatDate(e.bidValidity.expiry, i18n.language) })} />
            </div>
            <p className="text-sm text-muted-foreground">{t('landing.bidValidity.explain')}</p>
            <Button asChild size="lg"><Link to={`/files/${fileId}`}>{t('landing.bidValidity.action')}</Link></Button>
          </>
        )}
      </QueryState>
    </Landing>
  );
}

export function CureLanding() {
  const { t, i18n } = useTranslation();
  const { cureRequestId = '' } = useParams();
  const [params] = useSearchParams();
  const fileId = params.get('fileId') ?? '';
  const ev = useQuery({ queryKey: ['evaluation', fileId], queryFn: () => evaluation.get(fileId), enabled: !!fileId });
  const cure = ev.data?.cureRequests.find((c) => c.id === cureRequestId) ?? ev.data?.cureRequests.find((c) => c.outcome === 'PENDING');
  return (
    <Landing icon={CalendarClock} question={t('landing.cure.question')}>
      {!fileId && <Alert variant="info"><AlertDescription>{t('landing.cure.openFromFile')}</AlertDescription></Alert>}
      {cure && (
        <>
          <p>{t('landing.cure.bidder', { bidder: cure.bidderRef })}</p>
          <Countdown days={daysUntil(cure.deadline)} warnAt={2} label={t('evaluation.cure.deadline', { date: formatDate(cure.deadline, i18n.language) })} />
          <p className="text-sm">{t('landing.cure.outstanding', { count: cure.outstandingItems.filter((i) => !i.supplied).length })}</p>
        </>
      )}
      <Button asChild size="lg"><Link to={fileId ? `/files/${fileId}` : '/files'}>{t('landing.cure.action')}</Link></Button>
    </Landing>
  );
}

export function TermLanding() {
  const { t, i18n } = useTranslation();
  const { membershipId = '' } = useParams();
  const { me, hasRole } = useSession();
  const own = me?.committees.find((c) => c.membershipId === membershipId);
  const cbm = useQuery({ queryKey: ['dashboard', 'cbm'], queryFn: dashboards.cbm, enabled: !own && hasRole('CBM') });
  const row = ((cbm.data?.termExpiries as Record<string, unknown>[] | undefined) ?? []).find((r) => r.membershipId === membershipId);
  const termEnd = own?.termEnd ?? (row?.termEnd as string | undefined);
  return (
    <Landing icon={UserCheck} question={t('landing.term.question')}>
      {own && <p>{t('landing.term.yours', { committee: own.code, role: t(`committee.role.${own.role}`) })}</p>}
      {row && <p>{t('landing.term.member', { name: String(row.fullName), committee: String(row.committeeCode) })}</p>}
      {termEnd && <Countdown days={daysUntil(termEnd)} warnAt={30} label={t('landing.term.ends', { date: formatDate(termEnd, i18n.language) })} />}
      {hasRole('CBM')
        ? <Button asChild size="lg"><Link to="/committees">{t('landing.term.cbmAction')}</Link></Button>
        : <Button asChild size="lg" variant="outline"><Link to="/">{t('landing.term.memberAction')}</Link></Button>}
    </Landing>
  );
}

export function SessionLanding() {
  const { t, i18n } = useTranslation();
  const { sessionId = '' } = useParams();
  const q = useQuery({ queryKey: ['session', sessionId], queryFn: () => sessions.get(sessionId) });
  return (
    <QueryState query={q}>
      {(d) => (
        <Landing icon={CalendarClock} question={t('landing.session.question', { number: d.session.sessionNumber })}>
          <p>{formatInstant(d.session.scheduledAt, i18n.language)}{d.session.location ? ` · ${d.session.location}` : ''}</p>
          <p className="text-sm text-muted-foreground">{t('sessions.agendaCount', { count: d.agenda.length })}</p>
          {d.myRole && d.session.status === 'PLANNED' ? <ApologyCard detail={d} /> : null}
          <Button asChild size="lg" variant="outline"><Link to={`/sessions/${sessionId}`}>{t('landing.session.action')}</Link></Button>
        </Landing>
      )}
    </QueryState>
  );
}

export function DeclarationLanding() {
  const { t } = useTranslation();
  const { agendaItemId = '' } = useParams();
  return (
    <div className="mx-auto grid max-w-lg gap-4">
      <h1 className="text-xl font-bold">{t('landing.declaration.question')}</h1>
      <DeclarationPanel agendaItemId={agendaItemId} />
    </div>
  );
}

export function VoteLanding() {
  const { t } = useTranslation();
  const { decisionId = '' } = useParams();
  const qc = useQueryClient();
  const d = useQuery({ queryKey: ['decision-by-id', decisionId], queryFn: () => decisions.get(decisionId) });
  const state = useQuery({
    queryKey: ['decision', d.data?.agendaItemId],
    queryFn: () => decisions.forItem(d.data!.agendaItemId),
    enabled: !!d.data?.agendaItemId,
  });
  return (
    <div className="mx-auto grid max-w-lg gap-4">
      <h1 className="text-xl font-bold">{t('landing.vote.question')}</h1>
      <QueryState query={state}>
        {(s) => (
          <>
            <Card>
              <CardHeader>
                <CardTitle>{t(`recommendation.${s.decision?.recommendationCode}`, { defaultValue: s.decision?.recommendationCode ?? '' })}</CardTitle>
                <CardDescription className="whitespace-pre-wrap">{s.decision?.recommendationText}</CardDescription>
              </CardHeader>
            </Card>
            <ThresholdBox state={s} />
            {s.decision?.status === 'VOTING'
              ? <VotingPanel state={s} onVoted={() => void qc.invalidateQueries({ queryKey: ['decision', d.data?.agendaItemId] })} />
              : <Alert variant="info"><AlertDescription>{t('landing.vote.closed')}</AlertDescription></Alert>}
          </>
        )}
      </QueryState>
    </div>
  );
}

export function MinutesLanding() {
  const { t } = useTranslation();
  const { minutesId = '' } = useParams();
  const [params] = useSearchParams();
  const sessionId = params.get('sessionId');
  const [comment] = React.useState('');
  const q = useQuery({ queryKey: ['minutes', sessionId], queryFn: () => minutes.forSession(sessionId!), enabled: !!sessionId });
  const { me } = useSession();
  const [done, setDone] = React.useState(false);
  const pending = q.data?.pendingAcknowledgements.some((p) => p.userId === me?.id);
  return (
    <Landing icon={ScrollText} question={t('landing.minutes.question')}>
      <p className="text-sm text-muted-foreground">{t('minutes.ackExplain')}</p>
      {done && <Alert variant="success"><AlertDescription>{t('minutes.acknowledged')}</AlertDescription></Alert>}
      {sessionId ? (
        <>
          <Button asChild variant="outline"><Link to={`/sessions/${sessionId}/minutes`}>{t('landing.minutes.read')}</Link></Button>
          {pending && !done && <Button size="lg" onClick={() => void minutes.acknowledge(minutesId, comment || undefined).then(() => setDone(true))}>{t('minutes.acknowledge')}</Button>}
        </>
      ) : (
        <Button asChild size="lg"><Link to="/sessions">{t('landing.minutes.read')}</Link></Button>
      )}
    </Landing>
  );
}

export function TaskLanding() {
  const { t } = useTranslation();
  const { taskId = '' } = useParams();
  const q = useQuery({ queryKey: ['tasks', 'all'], queryFn: () => tasks.list() });
  const task = q.data?.find((x) => x.id === taskId);
  return (
    <div className="mx-auto grid max-w-lg gap-4">
      <h1 className="text-xl font-bold">{t('landing.task.question')}</h1>
      <QueryState query={q}>{() => (task ? <TaskCard task={task} /> : <Alert variant="info"><AlertDescription>{t('errors.not_found')}</AlertDescription></Alert>)}</QueryState>
    </div>
  );
}

export function ContractLanding() {
  const { t, i18n } = useTranslation();
  const { contractId = '' } = useParams();
  // Some reminders link with the id of the type C/D file instead of the
  // contract; resolve the file's contract in that case.
  const q = useQuery({
    queryKey: ['contract-timeline', contractId],
    queryFn: async () => {
      try {
        return await contracts.timeline(contractId);
      } catch (e) {
        const f = await files.get(contractId).catch(() => null);
        if (f?.contractId) return contracts.timeline(f.contractId);
        throw e;
      }
    },
  });
  return (
    <QueryState query={q}>
      {(tl) => (
        <Landing icon={FileSignature} question={t('landing.contract.question', { ref: tl.contract.contractRef })}>
          <Countdown days={daysUntil(tl.currentCompletionDate)} warnAt={30} label={t('landing.contract.completion', { date: formatDate(tl.currentCompletionDate, i18n.language) })} />
          <p className="text-sm">{t('landing.contract.explain')}</p>
          <Button asChild size="lg"><Link to={`/contracts/${tl.contract.id}`}>{t('landing.contract.action')}</Link></Button>
        </Landing>
      )}
    </QueryState>
  );
}

export function RecommendationLanding() {
  const { t } = useTranslation();
  const { decisionId = '' } = useParams();
  const q = useQuery({ queryKey: ['cbm-pending'], queryFn: decisions.pendingForCbm });
  const rec = q.data?.find((r) => r.decisionId === decisionId);
  const [answered, setAnswered] = React.useState(false);
  return (
    <div className="mx-auto grid max-w-2xl gap-4">
      <h1 className="text-xl font-bold">{t('landing.recommendation.question')}</h1>
      {answered && <Alert variant="success"><AlertDescription>{t('cbm.recorded')}</AlertDescription></Alert>}
      <QueryState query={q}>{() => (rec ? <PendingCard rec={rec} onDone={() => setAnswered(true)} /> : !answered && <Alert variant="info"><AlertDescription>{t('landing.recommendation.none')}</AlertDescription></Alert>)}</QueryState>
    </div>
  );
}
