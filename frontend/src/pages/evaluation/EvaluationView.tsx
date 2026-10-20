import * as React from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { AlertTriangle, CheckCircle2, Circle, Clock, Info, Lock } from 'lucide-react';
import { evaluation } from '@/api/endpoints';
import type { EvaluationStage, EvaluationView as EvalData, FileDetail } from '@/api/types';
import { useSession } from '@/hooks/useSession';
import { useMutate } from '@/hooks/useMutate';
import { useOnline } from '@/hooks/useOnline';
import { useConfig, configList } from '@/lib/config';
import { formatDate, formatInstant } from '@/lib/time';
import { issueText } from '@/lib/issues';
import { Countdown, QueryState, StatusBadge } from '@/components/app/common';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { BidderResults } from './BidderResults';
import { AbnormalPriceSection } from './AbnormalPriceSection';
import { CureSection } from './CureSection';
import { DueDiligenceSection } from './DueDiligenceSection';
import { ClarificationLog } from './ClarificationLog';
import { StageDataForm } from './StageDataForm';

export function EvaluationView({ file }: { file: FileDetail }) {
  const q = useQuery({ queryKey: ['evaluation', file.id], queryFn: () => evaluation.get(file.id) });
  return <QueryState query={q}>{(d) => <Evaluation file={file} data={d} />}</QueryState>;
}

function Evaluation({ file, data }: { file: FileDetail; data: EvalData }) {
  const { committeeRole } = useSession();
  const canEdit = !!committeeRole(file.committeeId);
  const [selected, setSelected] = React.useState<string>(
    data.stages.find((s) => s.status === 'IN_PROGRESS')?.code ?? data.stages.find((s) => s.status === 'NOT_STARTED')?.code ?? 'A1',
  );
  const stage = data.stages.find((s) => s.code === selected);
  return (
    <div className="grid gap-4">
      <BidValidityCard data={data} />
      <CriteriaLockCard file={file} data={data} canEdit={canEdit} />
      <StageProgress data={data} selected={selected} onSelect={setSelected} />
      {stage && <StageDetail file={file} data={data} stage={stage} canEdit={canEdit && data.criteriaLocked} />}
      <ClarificationLog fileId={file.id} items={data.clarifications} canEdit={canEdit} />
    </div>
  );
}

function BidValidityCard({ data }: { data: EvalData }) {
  const { t, i18n } = useTranslation();
  const v = data.bidValidity;
  const variant = v.state === 'OK' ? 'info' : v.state === 'NOT_SET' ? 'warning' : 'destructive';
  return (
    <Alert variant={variant}>
      <Clock className="h-4 w-4" aria-hidden />
      <AlertTitle>{t('evaluation.bidValidity.title')} — {t(`evaluation.bidValidity.state.${v.state}`)}</AlertTitle>
      <AlertDescription>
        <div className="mt-2 flex flex-wrap gap-3">
          <Countdown days={v.daysToRequestDeadline} warnAt={10} label={t('evaluation.bidValidity.requestDeadline', { date: formatDate(v.regulatorRequestDeadline, i18n.language) })} />
          <Countdown days={v.daysToExpiry} warnAt={14} label={t('evaluation.bidValidity.expiry', { date: formatDate(v.expiry, i18n.language) })} />
        </div>
        <p className="mt-2 text-sm">{t('evaluation.bidValidity.explain')}</p>
      </AlertDescription>
    </Alert>
  );
}

function CriteriaLockCard({ file, data, canEdit }: { file: FileDetail; data: EvalData; canEdit: boolean }) {
  const { t, i18n } = useTranslation();
  const [confirmed, setConfirmed] = React.useState(false);
  const [findings, setFindings] = React.useState(data.evaluation?.criteriaDefectFindings ?? '');
  const lock = useMutate(() => evaluation.lockCriteria(file.id, findings || undefined), { invalidate: [['evaluation', file.id]], success: t('evaluation.criteria.locked') });
  const save = useMutate(() => evaluation.saveFindings(file.id, findings), { invalidate: [['evaluation', file.id]], success: t('common.saved') });
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><Lock className="h-5 w-5" aria-hidden />{t('evaluation.criteria.title')}</CardTitle>
        <CardDescription>{t('evaluation.criteria.explain')}</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3">
        {data.criteriaLocked ? (
          <Alert variant="success">
            <AlertDescription>{t('evaluation.criteria.lockedAt', { at: formatInstant(data.evaluation?.criteriaLockedAt ?? null, i18n.language) })}</AlertDescription>
          </Alert>
        ) : (
          <>
            <Alert variant="warning"><AlertDescription>{t('evaluation.criteria.mustLockFirst')}</AlertDescription></Alert>
            <div className="flex items-start gap-2">
              <Checkbox id="criteria-confirm" checked={confirmed} onCheckedChange={(c) => setConfirmed(c === true)} disabled={!canEdit} />
              <Label htmlFor="criteria-confirm" className="leading-snug">{t('evaluation.criteria.confirm')}</Label>
            </div>
          </>
        )}
        <div className="grid gap-1.5">
          <Label htmlFor="findings">{t('evaluation.criteria.findings')}</Label>
          <Textarea id="findings" value={findings} onChange={(e) => setFindings(e.target.value)} disabled={!canEdit} />
          <p className="text-xs text-muted-foreground">{t('evaluation.criteria.findingsHint')}</p>
        </div>
        {canEdit && (data.criteriaLocked
          ? <Button variant="outline" className="justify-self-start" disabled={save.isPending} onClick={() => save.mutate(undefined)}>{t('evaluation.criteria.saveFindings')}</Button>
          : <Button className="justify-self-start" disabled={!confirmed || lock.isPending} onClick={() => lock.mutate(undefined)}>{t('evaluation.criteria.lock')}</Button>)}
      </CardContent>
    </Card>
  );
}

const statusIcon = (s: EvaluationStage['status']) =>
  s === 'COMPLETED' ? <CheckCircle2 className="h-5 w-5 text-success" aria-hidden />
    : s === 'IN_PROGRESS' ? <Clock className="h-5 w-5 text-warning" aria-hidden />
      : <Circle className="h-5 w-5 text-muted-foreground" aria-hidden />;

/** The committee's primary working screen for type A files. */
function StageProgress({ data, selected, onSelect }: { data: EvalData; selected: string; onSelect: (c: string) => void }) {
  const { t, i18n } = useTranslation();
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('evaluation.progress')}</CardTitle>
        <Alert variant="info" className="mt-2">
          <Info className="h-4 w-4" aria-hidden />
          <AlertDescription>{t('evaluation.sequenceNote')}</AlertDescription>
        </Alert>
      </CardHeader>
      <CardContent>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t('evaluation.stage')}</TableHead>
              <TableHead>{t('common.status')}</TableHead>
              <TableHead>{t('evaluation.completedBy')}</TableHead>
              <TableHead>{t('evaluation.outcome')}</TableHead>
              <TableHead>{t('evaluation.blocking')}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {[...data.stages].sort((a, b) => a.sequence - b.sequence).map((s) => (
              <TableRow key={s.code} className={selected === s.code ? 'bg-secondary/60' : 'cursor-pointer'} onClick={() => onSelect(s.code)}>
                <TableCell>
                  <button className="flex items-center gap-2 text-left font-medium" onClick={() => onSelect(s.code)} aria-pressed={selected === s.code}>
                    {statusIcon(s.status)}
                    <span>{s.code}. {t(`evaluation.stages.${s.code}.name`, { defaultValue: s.code })}</span>
                  </button>
                </TableCell>
                <TableCell><StatusBadge ns="evaluation.status" value={s.status} /></TableCell>
                <TableCell className="text-sm">{s.completedByName ? `${s.completedByName} · ${formatInstant(s.completedAt, i18n.language)}` : '—'}</TableCell>
                <TableCell className="max-w-xs truncate text-sm">{s.outcome ?? '—'}</TableCell>
                <TableCell className="text-sm">
                  {s.blockers.length === 0 ? '—' : (
                    <ul className="grid gap-0.5">
                      {s.blockers.map((b, i) => (
                        <li key={i} className="flex items-start gap-1 text-destructive">
                          <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
                          {blockerText(t, b)}
                        </li>
                      ))}
                    </ul>
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}

function StageDetail({ file, data, stage, canEdit }: { file: FileDetail; data: EvalData; stage: EvaluationStage; canEdit: boolean }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const online = useOnline();
  const cfg = useConfig();
  const requirements = data.config?.preliminaryRequirements ?? configList(cfg.data?.values, 'evaluation.preliminaryRequirements');
  const cureItems = data.config?.cureItems ?? configList(cfg.data?.values, 'evaluation.cureItems');
  const [outcome, setOutcome] = React.useState(stage.outcome ?? '');
  React.useEffect(() => setOutcome(stage.outcome ?? ''), [stage.code, stage.outcome]);
  const invalidate = [['evaluation', file.id]];
  const start = useMutate(() => evaluation.startStage(file.id, stage.code), { invalidate });
  const complete = useMutate(() => evaluation.completeStage(file.id, stage.code, outcome), {
    invalidate, success: t('evaluation.stageCompleted'), onSuccess: () => void qc.invalidateQueries({ queryKey: ['file', file.id] }),
  });
  const results = data.bidderResults.filter((r) => r.stageCode === stage.code);
  return (
    <Card>
      <CardHeader>
        <CardTitle>{stage.code}. {t(`evaluation.stages.${stage.code}.name`, { defaultValue: stage.code })}</CardTitle>
        <CardDescription>{t(`evaluation.stages.${stage.code}.help`, { defaultValue: '' })}</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-5">
        {!data.criteriaLocked && <Alert variant="warning"><AlertDescription>{t('evaluation.criteria.mustLockFirst')}</AlertDescription></Alert>}
        {stage.code === 'A4' && <Alert variant="info"><AlertDescription>{t('evaluation.stages.A4.sequence')}</AlertDescription></Alert>}

        {stage.status === 'NOT_STARTED' && stage.blockers.length > 0 && (
          <ul className="grid gap-1 text-sm text-destructive">{stage.blockers.map((b, i) => <li key={i}>• {blockerText(t, b)}</li>)}</ul>
        )}
        {stage.status === 'NOT_STARTED' && canEdit && (
          <Button className="justify-self-start" disabled={start.isPending || stage.blockers.length > 0} onClick={() => start.mutate(undefined)}>{t('evaluation.startStage')}</Button>
        )}

        {stage.status !== 'NOT_STARTED' && (
          <>
            <StageDataForm fileId={file.id} stage={stage} canEdit={canEdit && stage.status !== 'COMPLETED'}
              allBidsGrounds={data.config?.allBidsGrounds ?? ['ALL_ABNORMAL', 'ALL_NON_RESPONSIVE', 'ALL_OVER_BUDGET']} />
            {(stage.code === 'A1' || stage.code === 'A2' || stage.code === 'A3') && (
              <BidderResults fileId={file.id} stage={stage} results={results} canEdit={canEdit && stage.status !== 'COMPLETED'}
                requirements={stage.code === 'A1' ? requirements : []} />
            )}
            {stage.code === 'A3' && <AbnormalPriceSection fileId={file.id} items={data.abnormalPriceChecks} canEdit={canEdit} />}
            {stage.code === 'A4' && <CureSection fileId={file.id} items={data.cureRequests} canEdit={canEdit}
              cureItems={cureItems} firstRanked={firstRanked(data)} />}
            {stage.code === 'A5' && <DueDiligenceSection fileId={file.id} items={data.dueDiligence} canEdit={canEdit} />}
            {stage.code === 'A6' && <Alert variant="info"><AlertDescription>{t('evaluation.stages.A6.howTo')}</AlertDescription></Alert>}
          </>
        )}

        {stage.status === 'IN_PROGRESS' && canEdit && (
          <div className="grid gap-2 border-t pt-4">
            <Label htmlFor={`outcome-${stage.code}`}>{t('evaluation.stageOutcome')}</Label>
            <Textarea id={`outcome-${stage.code}`} value={outcome} onChange={(e) => setOutcome(e.target.value)} />
            {!online && <p className="text-sm text-destructive">{t('evaluation.offlineBlocked')}</p>}
            {stage.blockers.length > 0 && (
              <ul className="grid gap-1 text-sm text-destructive">
                <li className="font-medium">{t('evaluation.resolveBlockers')}</li>
                {stage.blockers.map((b, i) => <li key={i}>• {blockerText(t, b)}</li>)}
              </ul>
            )}
            <Button className="justify-self-start" disabled={!online || !outcome.trim() || complete.isPending || stage.blockers.length > 0}
              onClick={() => complete.mutate(undefined)}>{t('evaluation.completeStage')}</Button>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

const blockerText = issueText;

function firstRanked(data: EvalData): string | undefined {
  const ranked = data.bidderResults.filter((r) => r.stageCode === 'A3' && r.result === 'PASS' && r.rank != null);
  ranked.sort((a, b) => (a.rank ?? 99) - (b.rank ?? 99));
  return ranked[0]?.bidderRef;
}
