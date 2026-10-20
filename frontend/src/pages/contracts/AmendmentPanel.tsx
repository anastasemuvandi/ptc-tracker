import * as React from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { CheckCircle2, XCircle, HelpCircle, Scale, Calculator } from 'lucide-react';
import { contractChanges } from '@/api/endpoints';
import type { AmendmentAssessment, AmendmentRequest, FileDetail } from '@/api/types';
import { useSession } from '@/hooks/useSession';
import { useMutate } from '@/hooks/useMutate';
import { Countdown, QueryState } from '@/components/app/common';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Field } from '@/components/ui/field';
import { SimpleSelect } from '@/components/ui/select';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { formatDate, formatInstant, formatMoney, formatPct } from '@/lib/time';
import { issueText } from '@/lib/issues';
import { cn } from '@/lib/utils';

const tri = (v: boolean | null | undefined) => (v === true ? 'yes' : v === false ? 'no' : '');
const fromTri = (v: string) => (v === '' ? null : v === 'yes');

const LEVEL_CODES = ['amendment.approaching_ceiling', 'amendment.cumulative_exceeded'];

export function AmendmentPanel({ file }: { file: FileDetail }) {
  const q = useQuery({ queryKey: ['amendment', file.id], queryFn: () => contractChanges.amendment(file.id) });
  return <QueryState query={q}>{(v) => <Amendment file={file} request={v.request} assessment={v.assessment} frozenAt={v.frozen ? v.frozenAt : null} />}</QueryState>;
}

function Amendment({ file, request, assessment, frozenAt }: { file: FileDetail; request: AmendmentRequest | null; assessment: AmendmentAssessment | null; frozenAt?: string | null }) {
  const { t, i18n } = useTranslation();
  const { committeeRole } = useSession();
  const canEdit = !!committeeRole(file.committeeId) && (!request || request.status === 'OPEN');
  return (
    <div className="grid gap-4">
      {frozenAt && <Alert variant="info"><AlertDescription>{t('amendment.frozenAt', { at: formatInstant(frozenAt, i18n.language) })}</AlertDescription></Alert>}
      {assessment?.preview && <Alert variant="info"><AlertDescription>{t('amendment.preview')}</AlertDescription></Alert>}
      {assessment && <CumulativeAnalysis a={assessment} contractId={file.contractId} />}
      {assessment && (
        <div className="grid gap-4 lg:grid-cols-2">
          <QualitativeTest a={assessment} />
          <QuantitativeTest a={assessment} />
        </div>
      )}
      {assessment && (
        <Alert variant="warning">
          <Scale className="h-4 w-4" aria-hidden />
          <AlertTitle>{t('amendment.independentTests')}</AlertTitle>
          <AlertDescription>{t('amendment.quantDoesNotCure')}</AlertDescription>
        </Alert>
      )}
      {assessment && <RouteCard a={assessment} />}
      {request?.status === 'OPEN' && canEdit && !frozenAt && <FreezeButton fileId={file.id} />}
      <AmendmentForm file={file} request={request} canEdit={canEdit} />
    </div>
  );
}

function CumulativeAnalysis({ a, contractId }: { a: AmendmentAssessment; contractId: string | null }) {
  const { t, i18n } = useTranslation();
  const m = (v: string | number | null) => formatMoney(v, a.currency, i18n.language);
  const pct = Number(a.resultingPct);
  const ceiling = Number(a.ceilingPct);
  const warn = Number(a.warningPct);
  const tone = a.level === 'EXCEEDED' ? 'bg-destructive' : a.level === 'APPROACHING' ? 'bg-warning' : 'bg-success';
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2"><Calculator className="h-5 w-5" aria-hidden />{t('amendment.cumulativeTitle')}</CardTitle>
        <CardDescription>{t('amendment.cumulativeExplain')}</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        <table className="w-full max-w-xl text-sm">
          <tbody>
            <tr><td className="py-1">{t('amendment.originalValue')}</td><td className="text-right font-medium tabular-nums">{m(a.originalValue)}</td></tr>
            <tr><td className="py-1">{t('amendment.priorApproved')}</td><td className="text-right font-medium tabular-nums">{m(a.priorApprovedCumulative)}</td></tr>
            <tr><td className="py-1">{t('amendment.requested')}</td><td className="text-right font-medium tabular-nums">+ {m(a.requestedValue)}</td></tr>
            <tr className="border-t-2"><td className="py-1 font-semibold">{t('amendment.resultingCumulative')}</td><td className="text-right font-bold tabular-nums">{m(a.resultingCumulative)}</td></tr>
            <tr><td className="py-1 font-semibold">{t('amendment.resultingPct')}</td><td className="text-right text-lg font-extrabold tabular-nums">{formatPct(a.resultingPct)}</td></tr>
            {Number(a.pendingOtherAmendments) > 0 && (
              <tr><td className="py-1 text-muted-foreground">{t('amendment.pendingOthers')}</td><td className="text-right tabular-nums text-muted-foreground">{m(a.pendingOtherAmendments)}</td></tr>
            )}
          </tbody>
        </table>
        <div>
          <div className="relative h-4 w-full max-w-xl overflow-hidden rounded-full bg-muted" role="meter" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={ceiling} aria-label={t('amendment.resultingPct')}>
            <div className={cn('h-full', tone)} style={{ width: `${Math.min(100, (pct / ceiling) * 100)}%` }} />
            <div className="absolute inset-y-0 border-l-2 border-foreground/50" style={{ left: `${(warn / ceiling) * 100}%` }} aria-hidden />
          </div>
          <p className="mt-1 text-xs text-muted-foreground">{t('amendment.ceilingLegend', { warning: formatPct(a.warningPct), ceiling: formatPct(a.ceilingPct), currency: a.currency })}</p>
        </div>
        {a.level !== 'OK' && (
          <Alert variant={a.level === 'EXCEEDED' ? 'destructive' : 'warning'}>
            <AlertDescription>{t(`amendment.level.${a.level}`, { pct: formatPct(a.resultingPct), ceiling: formatPct(a.ceilingPct) })}</AlertDescription>
          </Alert>
        )}
        {/* The ceiling level has its own banner above; don't repeat it from the warning list. */}
        {a.warnings.filter((w) => !LEVEL_CODES.includes(w.code)).map((w, i) => (
          <Alert key={i} variant="warning"><AlertDescription>{issueText(t, w)}</AlertDescription></Alert>
        ))}
        {contractId && <Link to={`/contracts/${contractId}`} className="text-sm text-primary underline">{t('amendment.viewTimeline')}</Link>}
      </CardContent>
    </Card>
  );
}

function Verdict({ passes }: { passes: boolean | null }) {
  const { t } = useTranslation();
  if (passes === null) return <Badge variant="outline"><HelpCircle className="mr-1 h-3.5 w-3.5" aria-hidden />{t('amendment.notAnswered')}</Badge>;
  return passes
    ? <Badge variant="success"><CheckCircle2 className="mr-1 h-3.5 w-3.5" aria-hidden />{t('amendment.passes')}</Badge>
    : <Badge variant="destructive"><XCircle className="mr-1 h-3.5 w-3.5" aria-hidden />{t('amendment.fails')}</Badge>;
}

function QualitativeTest({ a }: { a: AmendmentAssessment }) {
  const { t } = useTranslation();
  const q = a.qualitative;
  return (
    <Card className={cn(q.passes === false && 'border-2 border-destructive')}>
      <CardHeader>
        <div className="flex items-center justify-between gap-2"><CardTitle>{t('amendment.qualitativeTitle')}</CardTitle><Verdict passes={q.passes} /></div>
        <CardDescription>{t('amendment.qualitativeExplain')}</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-2 text-sm">
        <p><strong>{t('amendment.affectsSubstanceQ')}</strong> {q.answered ? (q.affectsSubstance ? t('common.yes') : t('common.no')) : '—'}</p>
        {q.justification && <p className="whitespace-pre-wrap">{q.justification}</p>}
        {q.passes === false && <p className="font-semibold text-destructive">{t('amendment.absoluteBar')}</p>}
      </CardContent>
    </Card>
  );
}

function QuantitativeTest({ a }: { a: AmendmentAssessment }) {
  const { t, i18n } = useTranslation();
  const q = a.quantitative;
  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between gap-2"><CardTitle>{t('amendment.quantitativeTitle')}</CardTitle><Verdict passes={q.passes} /></div>
        <CardDescription>{t('amendment.quantitativeExplain')}</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-1 text-sm">
        <p>{q.withinPct ? '✓' : '✗'} {t('amendment.withinPct', { ceiling: formatPct(a.ceilingPct) })}</p>
        <p>{q.withinAbsolute ? '✓' : '✗'} {t('amendment.withinAbsolute', { ceiling: a.absoluteCeiling != null ? formatMoney(a.absoluteCeiling, a.currency, i18n.language) : t('amendment.noAbsoluteCeiling') })}</p>
        <p className="mt-2 text-muted-foreground">{t('amendment.quantRouteOnly')}</p>
      </CardContent>
    </Card>
  );
}

function RouteCard({ a }: { a: AmendmentAssessment }) {
  const { t, i18n } = useTranslation();
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('amendment.routeTitle')}: {t(`amendment.route.${a.route}`)}</CardTitle>
        <CardDescription>{t(`amendment.routeExplain.${a.route}`)}</CardDescription>
      </CardHeader>
      {a.route === 'ESCALATED' && a.escalation?.applies !== false && a.escalation && (
        <CardContent className="grid gap-3">
          <div className="flex flex-wrap gap-3">
            <Countdown days={a.escalation.daysRemaining} warnAt={10} label={t('amendment.submitBy', { date: formatDate(a.escalation.submitBy, i18n.language) })} />
          </div>
          <p className="text-sm">{t('amendment.leadTime', { days: a.escalation.leadDays })}</p>
          {!a.escalation.sufficient && <Alert variant="destructive"><AlertDescription>{t('amendment.insufficientTime')}</AlertDescription></Alert>}
        </CardContent>
      )}
    </Card>
  );
}

/** Freezes the computed figures into the record before the committee issues its recommendation. */
function FreezeButton({ fileId }: { fileId: string }) {
  const { t } = useTranslation();
  const freeze = useMutate(() => contractChanges.freezeAmendment(fileId), { invalidate: [['amendment', fileId]], success: t('amendment.frozen') });
  return (
    <div className="grid gap-1">
      <Button variant="outline" className="justify-self-start" disabled={freeze.isPending} onClick={() => freeze.mutate(undefined)}>{t('amendment.freeze')}</Button>
      <p className="text-xs text-muted-foreground">{t('amendment.freezeHelp')}</p>
    </div>
  );
}

function AmendmentForm({ file, request, canEdit }: { file: FileDetail; request: AmendmentRequest | null; canEdit: boolean }) {
  const { t } = useTranslation();
  const [d, setD] = React.useState<Partial<AmendmentRequest>>(request ?? {});
  React.useEffect(() => setD(request ?? {}), [request]);
  const set = <K extends keyof AmendmentRequest>(k: K, v: AmendmentRequest[K] | null | undefined) => setD((s) => ({ ...s, [k]: v }));
  const save = useMutate(() => (request ? contractChanges.updateAmendment(file.id, d) : contractChanges.createAmendment(file.id, d)), {
    invalidate: [['amendment', file.id], ['file', file.id]], success: t('common.saved'),
  });
  const yesNo = [{ value: 'yes', label: t('common.yes') }, { value: 'no', label: t('common.no') }];
  const consultancy = file.procurementType === 'CONSULTANCY';
  return (
    <Card>
      <CardHeader><CardTitle>{t('amendment.requestTitle')}</CardTitle></CardHeader>
      <CardContent>
        <form className="grid gap-3 sm:grid-cols-2" onSubmit={(e) => { e.preventDefault(); save.mutate(undefined); }}>
          <Field id="am-value" label={t('amendment.requestedValue')} hint={t('amendment.contractCurrency')} required>
            <Input id="am-value" inputMode="decimal" disabled={!canEdit} value={String(d.requestedValue ?? '')} onChange={(e) => set('requestedValue', e.target.value)} />
          </Field>
          <Field id="am-desc" label={t('amendment.description')} className="sm:col-span-2" required>
            <Textarea id="am-desc" disabled={!canEdit} value={d.description ?? ''} onChange={(e) => set('description', e.target.value)} />
          </Field>
          <Field id="am-subst" label={t('amendment.affectsSubstanceQ')} required>
            <SimpleSelect id="am-subst" disabled={!canEdit} value={tri(d.affectsSubstance)} allowEmpty onChange={(v) => set('affectsSubstance', fromTri(v))} options={yesNo} />
          </Field>
          <Field id="am-just" label={t('amendment.justification')} className="sm:col-span-2" required={d.affectsSubstance != null}>
            <Textarea id="am-just" disabled={!canEdit} value={d.substanceJustification ?? ''} onChange={(e) => set('substanceJustification', e.target.value)} />
          </Field>
          <Field id="am-deliv" label={t('amendment.deliverablesChange')}>
            <SimpleSelect id="am-deliv" disabled={!canEdit} value={tri(d.deliverablesChange)} allowEmpty onChange={(v) => set('deliverablesChange', fromTri(v))} options={yesNo} />
          </Field>
          <Field id="am-psext" label={t('amendment.perfSecurityExtend')}>
            <SimpleSelect id="am-psext" disabled={!canEdit} value={tri(d.perfSecurityExtend)} allowEmpty onChange={(v) => set('perfSecurityExtend', fromTri(v))} options={yesNo} />
          </Field>
          <Field id="am-psinc" label={t('amendment.perfSecurityIncrease')}>
            <SimpleSelect id="am-psinc" disabled={!canEdit} value={tri(d.perfSecurityIncrease)} allowEmpty onChange={(v) => set('perfSecurityIncrease', fromTri(v))} options={yesNo} />
          </Field>
          {consultancy && (
            <>
              <Field id="am-kp" label={t('amendment.keyPersonnelChange')}>
                <SimpleSelect id="am-kp" disabled={!canEdit} value={tri(d.keyPersonnelChange)} allowEmpty onChange={(v) => set('keyPersonnelChange', fromTri(v))} options={yesNo} />
              </Field>
              {d.keyPersonnelChange && (
                <Field id="am-eob" label={t('amendment.equalOrBetter')}>
                  <SimpleSelect id="am-eob" disabled={!canEdit} value={tri(d.replacementEqualOrBetter)} allowEmpty onChange={(v) => set('replacementEqualOrBetter', fromTri(v))} options={yesNo} />
                </Field>
              )}
            </>
          )}
          {canEdit && (
            <div className="sm:col-span-2">
              <Button type="submit" disabled={save.isPending || !d.requestedValue || !d.description || (d.affectsSubstance != null && !d.substanceJustification?.trim())}>
                {request ? t('common.save') : t('amendment.create')}
              </Button>
            </div>
          )}
        </form>
      </CardContent>
    </Card>
  );
}
