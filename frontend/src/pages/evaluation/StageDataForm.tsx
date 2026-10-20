import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { evaluation } from '@/api/endpoints';
import type { EvaluationStage } from '@/api/types';
import { useMutate } from '@/hooks/useMutate';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Field } from '@/components/ui/field';
import { SimpleSelect } from '@/components/ui/select';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';

type Data = Record<string, unknown>;
interface ResourcePerson { name?: string; institution?: string; noDecisionRole?: boolean; noInterest?: boolean }

/**
 * Stage-level structured record. Keys mirror the server's completion rules
 * (EvaluationService.completeBlockers). No scores or prices are captured.
 */
export function StageDataForm({ fileId, stage, canEdit, allBidsGrounds }: {
  fileId: string; stage: EvaluationStage; canEdit: boolean; allBidsGrounds: string[];
}) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [data, setData] = React.useState<Data>(stage.data ?? {});
  React.useEffect(() => setData(stage.data ?? {}), [stage.code, stage.data]);
  const save = useMutate(() => evaluation.saveStage(fileId, stage.code, { data, outcome: stage.outcome ?? undefined, version: stage.version }), {
    invalidate: [['evaluation', fileId]], success: t('common.saved'),
  });
  const [raiseReason, setRaiseReason] = React.useState('');
  const raise = useMutate(() => evaluation.raiseRejectionOfAll(fileId, String(data.allBidsFlag), raiseReason), {
    invalidate: [['evaluation', fileId]], success: t('evaluation.allBids.raised'), onSuccess: (f) => navigate(`/files/${f.id}`),
  });
  const set = (k: string, v: unknown) => setData((d) => ({ ...d, [k]: v }));
  const num = (k: string) => (
    <Field id={`${stage.code}-${k}`} label={t(`evaluation.fields.${k}`)}>
      <Input id={`${stage.code}-${k}`} type="number" min={0} disabled={!canEdit} value={(data[k] as number | undefined) ?? ''}
        onChange={(e) => set(k, e.target.value === '' ? null : Number(e.target.value))} />
    </Field>
  );
  const check = (k: string) => (
    <div className="flex items-start gap-2 sm:col-span-2">
      <Checkbox id={`${stage.code}-${k}`} checked={data[k] === true} onCheckedChange={(c) => set(k, c === true)} disabled={!canEdit} />
      <Label htmlFor={`${stage.code}-${k}`} className="leading-snug">{t(`evaluation.fields.${k}`)}</Label>
    </div>
  );

  let body: React.ReactNode = null;
  if (stage.code === 'A2') {
    const rp = (data.resourcePerson as ResourcePerson | undefined) ?? {};
    const used = data.resourcePerson != null;
    const setRp = (patch: Partial<ResourcePerson>) => set('resourcePerson', { ...rp, ...patch });
    body = (
      <>
        {num('evaluatedCount')}
        {num('passingCount')}
        <div className="flex items-start gap-2 sm:col-span-2">
          <Checkbox id="A2-rp" checked={used} disabled={!canEdit} onCheckedChange={(c) => set('resourcePerson', c === true ? {} : null)} />
          <Label htmlFor="A2-rp" className="leading-snug">{t('evaluation.fields.resourcePersonUsed')}</Label>
        </div>
        {used && (
          <>
            <Field id="A2-rp-name" label={t('evaluation.fields.resourcePersonName')}>
              <Input id="A2-rp-name" disabled={!canEdit} value={rp.name ?? ''} onChange={(e) => setRp({ name: e.target.value })} />
            </Field>
            <Field id="A2-rp-inst" label={t('evaluation.fields.resourcePersonInstitution')}>
              <Input id="A2-rp-inst" disabled={!canEdit} value={rp.institution ?? ''} onChange={(e) => setRp({ institution: e.target.value })} />
            </Field>
            <div className="flex items-start gap-2 sm:col-span-2">
              <Checkbox id="A2-rp-nd" disabled={!canEdit} checked={rp.noDecisionRole === true} onCheckedChange={(c) => setRp({ noDecisionRole: c === true })} />
              <Label htmlFor="A2-rp-nd" className="leading-snug">{t('evaluation.fields.resourcePersonNoDecisionRole')}</Label>
            </div>
            <div className="flex items-start gap-2 sm:col-span-2">
              <Checkbox id="A2-rp-ni" disabled={!canEdit} checked={rp.noInterest === true} onCheckedChange={(c) => setRp({ noInterest: c === true })} />
              <Label htmlFor="A2-rp-ni" className="leading-snug">{t('evaluation.fields.resourcePersonNoInterest')}</Label>
            </div>
          </>
        )}
      </>
    );
  } else if (stage.code === 'A3') {
    const taxes = data.taxesIncludedInComparison;
    body = (
      <>
        {check('arithmeticCheckDone')}
        <Field id="A3-taxes" label={t('evaluation.fields.taxesIncludedInComparison')} className="sm:col-span-2">
          <SimpleSelect id="A3-taxes" disabled={!canEdit} allowEmpty value={taxes === true ? 'yes' : taxes === false ? 'no' : ''}
            onChange={(v) => set('taxesIncludedInComparison', v === '' ? null : v === 'yes')}
            options={[{ value: 'yes', label: t('evaluation.fields.taxesYes') }, { value: 'no', label: t('evaluation.fields.taxesNo') }]} />
        </Field>
        <Field id="A3-allbids" label={t('evaluation.allBids.label')} hint={t('evaluation.allBids.hint')} className="sm:col-span-2">
          <SimpleSelect id="A3-allbids" disabled={!canEdit || data.rejectionOfAllFileId != null} allowEmpty emptyLabel={t('evaluation.allBids.none')}
            value={(data.allBidsFlag as string | undefined) ?? ''} onChange={(v) => set('allBidsFlag', v || null)}
            options={allBidsGrounds.map((g) => ({ value: g, label: t(`evaluation.allBids.grounds.${g}`, { defaultValue: g }) }))} />
        </Field>
      </>
    );
  } else if (stage.code === 'A4') {
    body = check('allRequirementsMet');
  }
  if (!body) return null;

  const flagged = stage.code === 'A3' && !!data.allBidsFlag && (stage.data?.allBidsFlag === data.allBidsFlag);
  return (
    <div className="grid gap-3 rounded-md border p-3">
      <div className="grid gap-3 sm:grid-cols-2">{body}</div>
      {stage.code === 'A2' && <p className="text-xs text-muted-foreground">{t('evaluation.noScores')}</p>}
      {canEdit && <Button variant="outline" className="justify-self-start" disabled={save.isPending} onClick={() => save.mutate(undefined)}>{t('common.save')}</Button>}
      {flagged && (
        <Alert variant="warning">
          <AlertTitle>{t('evaluation.allBids.raiseTitle')}</AlertTitle>
          <AlertDescription className="grid gap-2">
            <p>{t('evaluation.allBidsFlag')}</p>
            {data.rejectionOfAllFileId ? (
              <Button variant="link" className="justify-self-start p-0" onClick={() => navigate(`/files/${String(data.rejectionOfAllFileId)}`)}>{t('evaluation.allBids.openTypeF')}</Button>
            ) : canEdit && (
              <>
                <Label htmlFor="raise-reason">{t('common.reason')}</Label>
                <Textarea id="raise-reason" value={raiseReason} onChange={(e) => setRaiseReason(e.target.value)} />
                <Button className="justify-self-start" disabled={!raiseReason.trim() || raise.isPending} onClick={() => raise.mutate(undefined)}>{t('evaluation.allBids.raise')}</Button>
              </>
            )}
          </AlertDescription>
        </Alert>
      )}
    </div>
  );
}
