import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { evaluation } from '@/api/endpoints';
import type { DueDiligence } from '@/api/types';
import { useMutate } from '@/hooks/useMutate';
import { StatusBadge } from '@/components/app/common';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { Field } from '@/components/ui/field';
import { SimpleSelect } from '@/components/ui/select';
import { formatDate, todayKigali } from '@/lib/time';

const RESULTS: DueDiligence['result'][] = ['CONFIRMED', 'FALSE', 'MATERIALLY_CONFUSING', 'INCOMPLETE'];

export function DueDiligenceSection({ fileId, items, canEdit }: { fileId: string; items: DueDiligence[]; canEdit: boolean }) {
  const { t, i18n } = useTranslation();
  const [d, setD] = React.useState<Partial<DueDiligence> | null>(null);
  const save = useMutate(() => (d!.id ? evaluation.updateDueDiligence(d!.id, d!) : evaluation.addDueDiligence(fileId, d!)), {
    invalidate: [['evaluation', fileId]], onSuccess: () => setD(null), success: t('common.saved'),
  });
  const confirmed = d?.result === 'CONFIRMED';
  return (
    <div className="grid gap-3">
      <h3 className="font-semibold">{t('evaluation.dd.title')}</h3>
      {items.map((x) => (
        <div key={x.id} className="grid gap-1 rounded bg-muted p-3 text-sm">
          <div className="flex flex-wrap items-center gap-2">
            <strong>{x.bidderRef}</strong>
            <StatusBadge ns="evaluation.dd.result" value={x.result} />
            {x.disqualified && <span className="font-semibold text-destructive">{t('evaluation.dd.disqualified')}</span>}
          </div>
          <p>{t('evaluation.dd.by', { name: x.performedByName, date: formatDate(x.performedOn, i18n.language) })}</p>
          <p>{x.itemsVerified}</p>
          {x.disqualificationGround && <p>{t('evaluation.dd.ground')}: {x.disqualificationGround}</p>}
        </div>
      ))}
      {canEdit && !d && <Button variant="outline" className="justify-self-start" onClick={() => setD({ performedOn: todayKigali(), result: 'CONFIRMED', disqualified: false })}>{t('evaluation.dd.add')}</Button>}
      {d && (
        <form className="grid gap-3 rounded-md border p-3 sm:grid-cols-2" onSubmit={(e) => { e.preventDefault(); save.mutate(undefined); }}>
          <Field id="dd-bidder" label={t('evaluation.bidders.ref')} required><Input id="dd-bidder" value={d.bidderRef ?? ''} onChange={(e) => setD({ ...d, bidderRef: e.target.value })} /></Field>
          <Field id="dd-by" label={t('evaluation.dd.performedBy')} required><Input id="dd-by" value={d.performedByName ?? ''} onChange={(e) => setD({ ...d, performedByName: e.target.value })} /></Field>
          <Field id="dd-on" label={t('common.date')} required><Input id="dd-on" type="date" value={d.performedOn ?? ''} onChange={(e) => setD({ ...d, performedOn: e.target.value })} /></Field>
          <Field id="dd-result" label={t('evaluation.dd.resultLabel')}>
            <SimpleSelect id="dd-result" value={d.result ?? 'CONFIRMED'} onChange={(v) => setD({ ...d, result: v as DueDiligence['result'], disqualified: v === 'CONFIRMED' ? false : d.disqualified })}
              options={RESULTS.map((r) => ({ value: r, label: t(`evaluation.dd.result.${r}`) }))} />
          </Field>
          <Field id="dd-items" label={t('evaluation.dd.itemsVerified')} className="sm:col-span-2" required>
            <Textarea id="dd-items" value={d.itemsVerified ?? ''} onChange={(e) => setD({ ...d, itemsVerified: e.target.value })} />
          </Field>
          {!confirmed && (
            <>
              <div className="flex items-center gap-2 sm:col-span-2">
                <Checkbox id="dd-disq" checked={!!d.disqualified} onCheckedChange={(c) => setD({ ...d, disqualified: c === true })} />
                <Label htmlFor="dd-disq">{t('evaluation.dd.disqualify')}</Label>
              </div>
              {d.disqualified && (
                <Field id="dd-ground" label={t('evaluation.dd.ground')} className="sm:col-span-2" required>
                  <Textarea id="dd-ground" value={d.disqualificationGround ?? ''} onChange={(e) => setD({ ...d, disqualificationGround: e.target.value })} />
                </Field>
              )}
            </>
          )}
          <div className="flex gap-2 sm:col-span-2">
            <Button type="submit" disabled={save.isPending || !d.bidderRef || !d.performedByName || !d.itemsVerified || (d.disqualified && !d.disqualificationGround?.trim())}>{t('common.save')}</Button>
            <Button type="button" variant="ghost" onClick={() => setD(null)}>{t('common.cancel')}</Button>
          </div>
        </form>
      )}
    </div>
  );
}
