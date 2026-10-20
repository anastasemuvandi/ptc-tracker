import * as React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { contractChanges } from '@/api/endpoints';
import type { ExtensionAssessment, ExtensionRequest, FileDetail } from '@/api/types';
import { useSession } from '@/hooks/useSession';
import { useMutate } from '@/hooks/useMutate';
import { useConfig } from '@/lib/config';
import { KeyValue, QueryState } from '@/components/app/common';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Field } from '@/components/ui/field';
import { SimpleSelect } from '@/components/ui/select';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { formatDate } from '@/lib/time';
import { issueText } from '@/lib/issues';

interface Ground { code: string; bidderFault: boolean }

export function ExtensionPanel({ file }: { file: FileDetail }) {
  const q = useQuery({ queryKey: ['extension', file.id], queryFn: () => contractChanges.extension(file.id) });
  return <QueryState query={q}>{(v) => <Extension file={file} request={v.request} assessment={v.assessment} completion={v.contract?.currentCompletionDate} viewGrounds={v.grounds} />}</QueryState>;
}

function Extension({ file, request, assessment, completion, viewGrounds }: {
  file: FileDetail; request: ExtensionRequest | null; assessment: ExtensionAssessment | null; completion?: string; viewGrounds?: Ground[];
}) {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const { committeeRole } = useSession();
  const cfg = useConfig();
  const grounds = viewGrounds ?? (cfg.data?.values['extension.grounds'] as Ground[] | undefined) ?? [];
  const canEdit = !!committeeRole(file.committeeId) && (!request || request.status === 'OPEN');
  const [d, setD] = React.useState<Partial<ExtensionRequest>>(request ?? { completionDateBefore: completion, hasCostClaim: false });
  React.useEffect(() => setD(request ?? { completionDateBefore: completion, hasCostClaim: false }), [request, completion]);
  const set = <K extends keyof ExtensionRequest>(k: K, v: ExtensionRequest[K] | null | undefined) => setD((s) => ({ ...s, [k]: v }));
  const ground = grounds.find((g) => g.code === d.groundCode);
  const bidderFault = ground?.bidderFault ?? false;
  const receivedBefore = d.requestReceivedOn && d.completionDateBefore ? d.requestReceivedOn < d.completionDateBefore : null;
  const save = useMutate(() => (request ? contractChanges.updateExtension(file.id, d) : contractChanges.createExtension(file.id, d)), {
    invalidate: [['extension', file.id], ['file', file.id]], success: t('common.saved'),
  });
  const [claimValue, setClaimValue] = React.useState('');
  const [claimDesc, setClaimDesc] = React.useState('');
  const costClaim = useMutate(() => contractChanges.costClaim(file.id, { requestedValue: claimValue, description: claimDesc }), {
    invalidate: [['extension', file.id]], onSuccess: (r) => navigate(`/files/${r.fileId}`), success: t('extension.costClaimCreated'),
  });

  return (
    <div className="grid gap-4">
      {assessment && (
        <Card>
          <CardHeader><CardTitle>{t('extension.assessment')}</CardTitle></CardHeader>
          <CardContent className="grid gap-3">
            <KeyValue items={[
              [t('extension.receivedBefore'), assessment.receivedBeforeCompletion ? t('common.yes') : t('common.no')],
              [t('extension.ground'), assessment.ground ? t(`extension.grounds.${assessment.ground.code}`, { defaultValue: assessment.ground.code }) : '—'],
              [t('extension.extensionDays'), String(assessment.extensionDays)],
            ]} />
            {(assessment.warnings ?? []).map((w, i) => <Alert key={`w${i}`} variant="warning"><AlertDescription>{issueText(t, w)}</AlertDescription></Alert>)}
            {(assessment.blockers ?? []).map((b, i) => <Alert key={`b${i}`} variant="destructive"><AlertDescription>{issueText(t, b)}</AlertDescription></Alert>)}
            {assessment.readyForRecommendation && <Alert variant="success"><AlertDescription>{t('extension.ready')}</AlertDescription></Alert>}
            {assessment.forceMajeure && (
              <Alert variant={assessment.forceMajeure.exceeded ? 'destructive' : 'info'}>
                <AlertTitle>{t('extension.forceMajeure')}</AlertTitle>
                <AlertDescription>{t('extension.forceMajeureDays', { days: assessment.forceMajeure.days, limit: assessment.forceMajeure.autoCancelDays })}
                  {assessment.forceMajeure.exceeded && ` ${t('extension.forceMajeureExceeded')}`}</AlertDescription>
              </Alert>
            )}
            {assessment.costClaim && (
              <Alert variant="warning">
                <AlertTitle>{t('extension.costClaimTitle')}</AlertTitle>
                <AlertDescription>
                  {t('extension.costClaimExplain')}{' '}
                  {assessment.linkedAmendmentFileId
                    ? <Link className="font-semibold underline" to={`/files/${assessment.linkedAmendmentFileId}`}>{t('extension.openLinked')}</Link>
                    : null}
                </AlertDescription>
              </Alert>
            )}
          </CardContent>
        </Card>
      )}
      <Card>
        <CardHeader><CardTitle>{t('extension.requestTitle')}</CardTitle></CardHeader>
        <CardContent>
          <form className="grid gap-3 sm:grid-cols-2" onSubmit={(e) => { e.preventDefault(); save.mutate(undefined); }}>
            <Field id="ex-orig" label={t('extension.originalCompletion')} hint={t('extension.fromContract')}>
              <Input id="ex-orig" type="date" disabled value={d.completionDateBefore ?? ''} readOnly />
            </Field>
            <Field id="ex-new" label={t('extension.requestedDate')} required>
              <Input id="ex-new" type="date" disabled={!canEdit} value={d.requestedCompletionDate ?? ''} onChange={(e) => set('requestedCompletionDate', e.target.value)} />
            </Field>
            <Field id="ex-recv" label={t('extension.receivedOn')} required
              hint={receivedBefore === false ? t('extension.lateRequest') : receivedBefore ? t('extension.receivedInTime', { date: formatDate(d.completionDateBefore ?? null, i18n.language) }) : undefined}>
              <Input id="ex-recv" type="date" disabled={!canEdit} value={d.requestReceivedOn ?? ''} onChange={(e) => set('requestReceivedOn', e.target.value)} />
            </Field>
            <Field id="ex-ground" label={t('extension.ground')} hint={t('extension.groundDetermines')} required>
              <SimpleSelect id="ex-ground" disabled={!canEdit} value={d.groundCode ?? ''} onChange={(v) => set('groundCode', v)} placeholder={t('common.choose')}
                options={grounds.map((g) => ({ value: g.code, label: t(`extension.grounds.${g.code}`, { defaultValue: g.code }) }))} />
            </Field>
            <Field id="ex-details" label={t('extension.groundDetails')} className="sm:col-span-2" required>
              <Textarea id="ex-details" disabled={!canEdit} value={d.groundDetails ?? ''} onChange={(e) => set('groundDetails', e.target.value)} />
            </Field>
            {bidderFault && (
              <>
                <Alert variant="warning" className="sm:col-span-2"><AlertDescription>{t('extension.penaltiesPrompt')}</AlertDescription></Alert>
                <div className="flex items-center gap-2 sm:col-span-2">
                  <Checkbox id="ex-pen" disabled={!canEdit} checked={!!d.penaltiesConsidered} onCheckedChange={(c) => set('penaltiesConsidered', c === true)} />
                  <Label htmlFor="ex-pen">{t('extension.penaltiesConsidered')}</Label>
                </div>
                <Field id="ex-reasoning" label={t('extension.bidderFaultReasoning')} className="sm:col-span-2" required>
                  <Textarea id="ex-reasoning" disabled={!canEdit} value={d.bidderFaultReasoning ?? ''} onChange={(e) => set('bidderFaultReasoning', e.target.value)} />
                </Field>
              </>
            )}
            {d.groundCode === 'FORCE_MAJEURE' && (
              <>
                <Field id="ex-fms" label={t('extension.fmStart')}><Input id="ex-fms" type="date" disabled={!canEdit} value={d.forceMajeureStart ?? ''} onChange={(e) => set('forceMajeureStart', e.target.value || null)} /></Field>
                <Field id="ex-fme" label={t('extension.fmEnd')} hint={t('extension.fmOngoing')}><Input id="ex-fme" type="date" disabled={!canEdit} value={d.forceMajeureEnd ?? ''} onChange={(e) => set('forceMajeureEnd', e.target.value || null)} /></Field>
              </>
            )}
            {canEdit && (
              <div className="sm:col-span-2">
                <Button type="submit" disabled={save.isPending || !d.requestedCompletionDate || !d.requestReceivedOn || !d.groundCode || !d.groundDetails || (bidderFault && !d.bidderFaultReasoning?.trim())}>
                  {request ? t('common.save') : t('extension.create')}
                </Button>
              </div>
            )}
          </form>
        </CardContent>
      </Card>
      {request && canEdit && !request.linkedAmendmentFileId && (
        <Card>
          <CardHeader><CardTitle>{t('extension.costClaimTitle')}</CardTitle></CardHeader>
          <CardContent className="grid gap-3">
            <p className="text-sm text-muted-foreground">{t('extension.costClaimExplain')}</p>
            <div className="grid gap-3 sm:grid-cols-2">
              <Field id="cc-value" label={t('amendment.requestedValue')} hint={t('amendment.contractCurrency')}>
                <Input id="cc-value" inputMode="decimal" value={claimValue} onChange={(e) => setClaimValue(e.target.value)} />
              </Field>
              <Field id="cc-desc" label={t('amendment.description')} className="sm:col-span-2">
                <Textarea id="cc-desc" value={claimDesc} onChange={(e) => setClaimDesc(e.target.value)} />
              </Field>
            </div>
            <Button className="justify-self-start" disabled={!claimValue || !claimDesc.trim() || costClaim.isPending} onClick={() => costClaim.mutate(undefined)}>
              {t('extension.createLinked')}
            </Button>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
