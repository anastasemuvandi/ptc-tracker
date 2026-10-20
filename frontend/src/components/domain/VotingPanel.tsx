import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { ShieldAlert, ThumbsDown, ThumbsUp, MinusCircle, WifiOff } from 'lucide-react';
import type { DecisionState, VoteChoice } from '@/api/types';
import { decisions } from '@/api/endpoints';
import { newIdempotencyKey } from '@/api/client';
import { useOnline } from '@/hooks/useOnline';
import { useErrorText } from '@/hooks/useErrorText';
import { voteOutlook } from '@/lib/quorum';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Label } from '@/components/ui/label';
import { toast } from '@/components/ui/toast';
import { cn } from '@/lib/utils';

export function ThresholdBox({ state }: { state: DecisionState }) {
  const { t } = useTranslation();
  const th = state.threshold;
  const d = state.decision;
  const cast = (d?.votesFor ?? 0) + (d?.votesAgainst ?? 0) + (d?.votesAbstained ?? 0);
  const outlook = voteOutlook(th, d?.votesFor ?? 0, cast);
  return (
    <div className="rounded-lg border-2 border-primary/30 bg-primary/5 p-4" aria-live="polite">
      <p className="text-sm font-medium text-muted-foreground">{t('voting.required')}</p>
      <p className="text-4xl font-extrabold tabular-nums text-primary">{th.required}</p>
      <p className="text-sm">{t('voting.requiredExplain', { rule: th.rule, eligible: th.eligible })}</p>
      <dl className="mt-3 grid grid-cols-3 gap-2 text-center text-sm">
        <div className="rounded bg-card p-2"><dt className="text-xs text-muted-foreground">{t('voting.present')}</dt><dd className="text-lg font-bold">{th.present}</dd></div>
        <div className="rounded bg-card p-2"><dt className="text-xs text-muted-foreground">{t('voting.excludedCoi')}</dt><dd className="text-lg font-bold">{th.excluded}</dd></div>
        <div className="rounded bg-card p-2"><dt className="text-xs text-muted-foreground">{t('voting.eligible')}</dt><dd className="text-lg font-bold">{th.eligible}</dd></div>
      </dl>
      {d && (d.status === 'VOTING' || cast > 0) && (
        <p className="mt-3 text-sm font-medium">
          {t('voting.tally', { for: d.votesFor, against: d.votesAgainst, abstained: d.votesAbstained })}{' '}
          {outlook.adoptedAlready ? t('voting.thresholdReached') : outlook.stillPossible ? t('voting.moreNeeded', { count: outlook.remaining }) : t('voting.cannotPass')}
        </p>
      )}
    </div>
  );
}

/**
 * Mobile-first voting: three large targets, the dissent reason field is on
 * screen immediately (no menu), and the act is only reported as recorded
 * after the server confirms it. Never queued offline.
 */
export function VotingPanel({ state, onVoted }: { state: DecisionState; onVoted: () => void }) {
  const { t } = useTranslation();
  const errorText = useErrorText();
  const online = useOnline();
  const [choice, setChoice] = React.useState<VoteChoice | null>(null);
  const [reason, setReason] = React.useState('');
  const [busy, setBusy] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const reasonRef = React.useRef<HTMLTextAreaElement>(null);
  // One key per intended vote: a retry after step-up or a dropped connection
  // cannot record a second vote.
  const key = React.useRef(newIdempotencyKey());
  const decision = state.decision!;

  if (state.myVote) {
    return (
      <Alert variant="success">
        <AlertTitle>{t('voting.recorded', { choice: t(`voting.choice.${state.myVote.choice}`) })}</AlertTitle>
        {state.myVote.dissentReason && <AlertDescription>{t('voting.yourDissent')}: “{state.myVote.dissentReason}”</AlertDescription>}
      </Alert>
    );
  }
  if (!state.canVote) {
    return <Alert variant="info"><AlertDescription>{t('voting.notEligible')}</AlertDescription></Alert>;
  }

  const needsReason = choice === 'AGAINST' || choice === 'ABSTAIN';
  const pick = (c: VoteChoice) => {
    setChoice(c);
    setError(null);
    if (c !== 'FOR') setTimeout(() => reasonRef.current?.focus(), 0);
  };

  const submit = async () => {
    if (!choice) return;
    if (!online) {
      setError(t('errors.offline.blocked'));
      return;
    }
    if (needsReason && reason.trim().length === 0) {
      setError(t('voting.reasonRequired'));
      reasonRef.current?.focus();
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await decisions.vote(decision.id, { choice, dissentReason: needsReason ? reason.trim() : undefined }, { idempotencyKey: key.current });
      toast({ title: t('voting.confirmed'), tone: 'success' });
      onVoted();
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  };

  const btn = (c: VoteChoice, Icon: React.ComponentType<{ className?: string }>, tone: string) => (
    <button
      type="button"
      onClick={() => pick(c)}
      aria-pressed={choice === c}
      disabled={busy}
      className={cn(
        'flex min-h-[72px] flex-col items-center justify-center gap-1 rounded-lg border-2 text-lg font-bold transition-colors focus-visible:outline-none focus-visible:ring-4 focus-visible:ring-ring',
        choice === c ? tone : 'border-input bg-card hover:bg-muted',
      )}
    >
      <Icon className="h-6 w-6" aria-hidden />
      {t(`voting.choice.${c}`)}
    </button>
  );

  return (
    <div className="grid gap-4">
      {!online && (
        <Alert variant="destructive">
          <WifiOff className="h-4 w-4" aria-hidden />
          <AlertDescription>{t('voting.offlineBlocked')}</AlertDescription>
        </Alert>
      )}
      <div className="grid grid-cols-3 gap-2" role="group" aria-label={t('voting.yourVote')}>
        {btn('FOR', ThumbsUp, 'border-success bg-success text-success-foreground')}
        {btn('AGAINST', ThumbsDown, 'border-destructive bg-destructive text-destructive-foreground')}
        {btn('ABSTAIN', MinusCircle, 'border-warning bg-warning text-warning-foreground')}
      </div>

      <Alert variant="warning">
        <ShieldAlert className="h-4 w-4" aria-hidden />
        <AlertTitle>{t('voting.dissentTitle')}</AlertTitle>
        <AlertDescription>{t('voting.dissentProtects')}</AlertDescription>
      </Alert>
      <div className="grid gap-1.5">
        <Label htmlFor="dissent">{t('voting.dissentReason')} {needsReason && <span className="text-destructive">*</span>}</Label>
        <Textarea
          id="dissent"
          ref={reasonRef}
          rows={4}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          placeholder={t('voting.dissentPlaceholder')}
          className={cn(needsReason && 'border-2 border-warning')}
        />
        <p className="text-xs text-muted-foreground">{t('voting.dissentInMinutes')}</p>
      </div>
      {error && <p className="text-sm font-medium text-destructive" role="alert">{error}</p>}
      <Button size="xl" disabled={!choice || busy || !online} onClick={() => void submit()}>
        {choice ? t('voting.castAs', { choice: t(`voting.choice.${choice}`) }) : t('voting.chooseFirst')}
      </Button>
      <p className="text-center text-xs text-muted-foreground">{t('voting.stepUpNote')}</p>
    </div>
  );
}
