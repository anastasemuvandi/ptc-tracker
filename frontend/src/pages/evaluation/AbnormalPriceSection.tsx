import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { evaluation } from '@/api/endpoints';
import type { AbnormalPriceCheck } from '@/api/types';
import { useMutate } from '@/hooks/useMutate';
import { StatusBadge } from '@/components/app/common';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Field } from '@/components/ui/field';
import { SimpleSelect } from '@/components/ui/select';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { formatDate } from '@/lib/time';

type Draft = Partial<AbnormalPriceCheck>;

/** Rejection on abnormal-price grounds is impossible without the written request AND the analysis. */
export function canReject(d: Draft): boolean {
  return !!d.requestRef?.trim() && !!d.requestDate && !!d.analysis?.trim() && d.doubtLifted === false;
}

export function AbnormalPriceSection({ fileId, items, canEdit }: { fileId: string; items: AbnormalPriceCheck[]; canEdit: boolean }) {
  const { t } = useTranslation();
  const [editing, setEditing] = React.useState<Draft | null>(null);
  return (
    <div className="grid gap-3 rounded-md border-2 border-dashed p-3">
      <div>
        <h3 className="font-semibold">{t('evaluation.abnormal.title')}</h3>
        <p className="text-sm text-muted-foreground">{t('evaluation.abnormal.explain')}</p>
      </div>
      {items.map((a) => (
        <div key={a.id} className="grid gap-1 rounded bg-muted p-3 text-sm">
          <div className="flex flex-wrap items-center gap-2">
            <strong>{a.bidderRef}</strong>
            <span>{t(`evaluation.abnormal.direction.${a.direction}`)}</span>
            <StatusBadge ns="evaluation.abnormal.decision" value={a.decision} />
            {canEdit && a.decision === 'PENDING' && <Button size="sm" variant="outline" onClick={() => setEditing(a)}>{t('common.edit')}</Button>}
          </div>
          <p>{t('evaluation.abnormal.request')}: {a.requestRef ?? '—'} · {formatDate(a.requestDate)}</p>
          <p>{t('evaluation.abnormal.explanation')}: {a.explanationSummary ?? '—'} · {formatDate(a.explanationDate)}</p>
          {a.analysis && <p>{t('evaluation.abnormal.analysis')}: {a.analysis}</p>}
          {a.decisionReasons && <p>{t('evaluation.abnormal.reasons')}: {a.decisionReasons}</p>}
        </div>
      ))}
      {canEdit && !editing && (
        <Button variant="outline" className="justify-self-start" onClick={() => setEditing({ direction: 'ABNORMALLY_LOW', decision: 'PENDING' })}>
          {t('evaluation.abnormal.add')}
        </Button>
      )}
      {editing && <AbnormalForm fileId={fileId} initial={editing} onDone={() => setEditing(null)} />}
    </div>
  );
}

function AbnormalForm({ fileId, initial, onDone }: { fileId: string; initial: Draft; onDone: () => void }) {
  const { t } = useTranslation();
  const [d, setD] = React.useState<Draft>(initial);
  const set = <K extends keyof Draft>(k: K, v: Draft[K]) => setD((s) => ({ ...s, [k]: v }));
  const rejectAllowed = canReject(d);
  const save = useMutate(() => (d.id ? evaluation.updateAbnormal(d.id, d) : evaluation.addAbnormal(fileId, d)), {
    invalidate: [['evaluation', fileId]], onSuccess: onDone, success: t('common.saved'),
  });
  return (
    <form className="grid gap-3 sm:grid-cols-2" onSubmit={(e) => { e.preventDefault(); save.mutate(undefined); }}>
      <Field id="ap-bidder" label={t('evaluation.bidders.ref')} required>
        <Input id="ap-bidder" value={d.bidderRef ?? ''} onChange={(e) => set('bidderRef', e.target.value)} disabled={!!d.id} required />
      </Field>
      <Field id="ap-dir" label={t('evaluation.abnormal.directionLabel')}>
        <SimpleSelect id="ap-dir" value={d.direction ?? 'ABNORMALLY_LOW'} onChange={(v) => set('direction', v as Draft['direction'])}
          options={['ABNORMALLY_LOW', 'ABNORMALLY_HIGH'].map((x) => ({ value: x, label: t(`evaluation.abnormal.direction.${x}`) }))} />
      </Field>
      <Field id="ap-req" label={t('evaluation.abnormal.requestRef')}>
        <Input id="ap-req" value={d.requestRef ?? ''} onChange={(e) => set('requestRef', e.target.value)} />
      </Field>
      <Field id="ap-reqdate" label={t('evaluation.abnormal.requestDate')}>
        <Input id="ap-reqdate" type="date" value={d.requestDate ?? ''} onChange={(e) => set('requestDate', e.target.value || null)} />
      </Field>
      <Field id="ap-expl" label={t('evaluation.abnormal.explanation')}>
        <Textarea id="ap-expl" value={d.explanationSummary ?? ''} onChange={(e) => set('explanationSummary', e.target.value)} />
      </Field>
      <Field id="ap-expldate" label={t('evaluation.abnormal.explanationDate')}>
        <Input id="ap-expldate" type="date" value={d.explanationDate ?? ''} onChange={(e) => set('explanationDate', e.target.value || null)} />
      </Field>
      <Field id="ap-analysis" label={t('evaluation.abnormal.analysis')} className="sm:col-span-2">
        <Textarea id="ap-analysis" value={d.analysis ?? ''} onChange={(e) => set('analysis', e.target.value)} />
      </Field>
      <Field id="ap-lifted" label={t('evaluation.abnormal.doubtLifted')}>
        <SimpleSelect id="ap-lifted" value={d.doubtLifted === true ? 'yes' : d.doubtLifted === false ? 'no' : ''} allowEmpty
          onChange={(v) => set('doubtLifted', v === '' ? null : v === 'yes')}
          options={[{ value: 'yes', label: t('common.yes') }, { value: 'no', label: t('common.no') }]} />
      </Field>
      <Field id="ap-decision" label={t('evaluation.abnormal.decisionLabel')}>
        <SimpleSelect id="ap-decision" value={d.decision ?? 'PENDING'} onChange={(v) => set('decision', v as Draft['decision'])}
          options={[
            { value: 'PENDING', label: t('evaluation.abnormal.decision.PENDING') },
            { value: 'ACCEPTED', label: t('evaluation.abnormal.decision.ACCEPTED') },
            ...(rejectAllowed || d.decision === 'REJECTED' ? [{ value: 'REJECTED', label: t('evaluation.abnormal.decision.REJECTED') }] : []),
          ]} />
      </Field>
      {!rejectAllowed && (
        <Alert variant="warning" className="sm:col-span-2"><AlertDescription>{t('evaluation.abnormal.rejectGate')}</AlertDescription></Alert>
      )}
      {d.decision !== 'PENDING' && (
        <Field id="ap-reasons" label={t('evaluation.abnormal.reasons')} className="sm:col-span-2" required>
          <Textarea id="ap-reasons" value={d.decisionReasons ?? ''} onChange={(e) => set('decisionReasons', e.target.value)} required />
        </Field>
      )}
      <div className="flex gap-2 sm:col-span-2">
        <Button type="submit" disabled={save.isPending || !d.bidderRef || (d.decision === 'REJECTED' && !rejectAllowed) || (d.decision !== 'PENDING' && !d.decisionReasons?.trim())}>
          {t('common.save')}
        </Button>
        <Button type="button" variant="ghost" onClick={onDone}>{t('common.cancel')}</Button>
      </div>
    </form>
  );
}
