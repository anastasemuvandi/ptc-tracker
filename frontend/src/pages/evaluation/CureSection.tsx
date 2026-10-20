import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { evaluation } from '@/api/endpoints';
import type { CureItem, CureRequest } from '@/api/types';
import { useMutate } from '@/hooks/useMutate';
import { Countdown, StatusBadge } from '@/components/app/common';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { Field } from '@/components/ui/field';
import { SimpleSelect } from '@/components/ui/select';
import { daysUntil, formatDate, todayKigali } from '@/lib/time';

/** A4: outstanding administrative documents of the first-ranked bidder, with the cure countdown. */
export function CureSection({ fileId, items, canEdit, cureItems, firstRanked }: {
  fileId: string; items: CureRequest[]; canEdit: boolean; cureItems: string[]; firstRanked?: string;
}) {
  const { t, i18n } = useTranslation();
  const [creating, setCreating] = React.useState(false);
  return (
    <div className="grid gap-3">
      <h3 className="font-semibold">{t('evaluation.cure.title')}</h3>
      {items.map((c) => <CureCard key={c.id} cure={c} fileId={fileId} canEdit={canEdit} lng={i18n.language} />)}
      {canEdit && !creating && <Button variant="outline" className="justify-self-start" onClick={() => setCreating(true)}>{t('evaluation.cure.issue')}</Button>}
      {creating && <NewCure fileId={fileId} cureItems={cureItems} firstRanked={firstRanked} onDone={() => setCreating(false)} />}
    </div>
  );
}

function CureCard({ cure, fileId, canEdit, lng }: { cure: CureRequest; fileId: string; canEdit: boolean; lng: string }) {
  const { t } = useTranslation();
  const [d, setD] = React.useState<CureRequest>(cure);
  React.useEffect(() => setD(cure), [cure]);
  const save = useMutate(() => evaluation.updateCure(cure.id, d), { invalidate: [['evaluation', fileId]], success: t('common.saved') });
  const pending = cure.outcome === 'PENDING';
  return (
    <div className="grid gap-3 rounded-md border p-3">
      <div className="flex flex-wrap items-center gap-3">
        <strong>{cure.bidderRef}</strong>
        <StatusBadge ns="evaluation.cure.outcome" value={cure.outcome} />
        <span className="text-sm text-muted-foreground">
          {t('evaluation.cure.issuedOn', { date: formatDate(cure.issuedOn, lng), days: cure.workingDays })}
        </span>
        {pending && <Countdown days={cure.overdue ? -Math.max(1, -daysUntil(cure.deadline)) : (cure.workingDaysRemaining ?? daysUntil(cure.deadline))} warnAt={2}
          label={t('evaluation.cure.deadline', { date: formatDate(cure.deadline, lng) })} />}
        {cure.suppliedLate && <span className="text-sm font-semibold text-destructive">{t('evaluation.cure.suppliedLate')}</span>}
      </div>
      <ul className="grid gap-2">
        {d.outstandingItems.map((item, i) => (
          <li key={item.code} className="flex flex-wrap items-center gap-2 text-sm">
            <Checkbox id={`cure-${cure.id}-${i}`} checked={!!item.supplied} disabled={!canEdit || !pending}
              onCheckedChange={(c) => setD((s) => ({ ...s, outstandingItems: s.outstandingItems.map((x, j) => (j === i ? { ...x, supplied: c === true, suppliedOn: c === true ? todayKigali() : null } : x)) }))} />
            <Label htmlFor={`cure-${cure.id}-${i}`}>{t(`evaluation.cureItems.${item.code}`, { defaultValue: item.label ?? item.code })}</Label>
            {item.suppliedOn && <span className="text-muted-foreground">({formatDate(item.suppliedOn, lng)})</span>}
          </li>
        ))}
      </ul>
      {canEdit && pending && (
        <div className="grid gap-3 sm:grid-cols-2">
          <Field id={`sup-${cure.id}`} label={t('evaluation.cure.suppliedSummary')}>
            <Textarea id={`sup-${cure.id}`} value={d.suppliedSummary ?? ''} onChange={(e) => setD({ ...d, suppliedSummary: e.target.value })} />
          </Field>
          <Field id={`supon-${cure.id}`} label={t('evaluation.cure.suppliedOn')}>
            <Input id={`supon-${cure.id}`} type="date" value={d.suppliedOn ?? ''} onChange={(e) => setD({ ...d, suppliedOn: e.target.value || null })} />
          </Field>
          <Field id={`out-${cure.id}`} label={t('evaluation.cure.outcomeLabel')}>
            <SimpleSelect id={`out-${cure.id}`} value={d.outcome} onChange={(v) => setD({ ...d, outcome: v as CureRequest['outcome'] })}
              options={['PENDING', 'COMPLIED', 'NOT_COMPLIED'].map((x) => ({ value: x, label: t(`evaluation.cure.outcome.${x}`) }))} />
          </Field>
          <Field id={`note-${cure.id}`} label={t('common.note')}>
            <Input id={`note-${cure.id}`} value={d.outcomeNote ?? ''} onChange={(e) => setD({ ...d, outcomeNote: e.target.value })} />
          </Field>
          <Button className="justify-self-start" variant="outline" disabled={save.isPending} onClick={() => save.mutate(undefined)}>{t('common.save')}</Button>
        </div>
      )}
      {!pending && cure.suppliedSummary && <p className="text-sm">{cure.suppliedSummary}</p>}
    </div>
  );
}

function NewCure({ fileId, cureItems, firstRanked, onDone }: { fileId: string; cureItems: string[]; firstRanked?: string; onDone: () => void }) {
  const { t } = useTranslation();
  const [bidder, setBidder] = React.useState(firstRanked ?? '');
  const [issuedOn, setIssuedOn] = React.useState(todayKigali());
  const [chosen, setChosen] = React.useState<string[]>([]);
  const create = useMutate(() => evaluation.addCure(fileId, {
    bidderRef: bidder, issuedOn, outstandingItems: chosen.map((code): CureItem => ({ code, supplied: false })),
  }), { invalidate: [['evaluation', fileId]], onSuccess: onDone, success: t('evaluation.cure.issued') });
  return (
    <div className="grid gap-3 rounded-md border p-3">
      <p className="text-sm text-muted-foreground">{t('evaluation.cure.firstRankedOnly')}</p>
      <div className="grid gap-3 sm:grid-cols-2">
        <Field id="cure-bidder" label={t('evaluation.cure.firstRanked')} required>
          <Input id="cure-bidder" value={bidder} onChange={(e) => setBidder(e.target.value)} />
        </Field>
        <Field id="cure-issued" label={t('evaluation.cure.issuedOnLabel')} required>
          <Input id="cure-issued" type="date" value={issuedOn} onChange={(e) => setIssuedOn(e.target.value)} />
        </Field>
      </div>
      <fieldset className="grid gap-2">
        <legend className="mb-1 text-sm font-medium">{t('evaluation.cure.outstanding')}</legend>
        {cureItems.map((code) => (
          <div key={code} className="flex items-center gap-2">
            <Checkbox id={`new-cure-${code}`} checked={chosen.includes(code)}
              onCheckedChange={(c) => setChosen((s) => (c === true ? [...s, code] : s.filter((x) => x !== code)))} />
            <Label htmlFor={`new-cure-${code}`}>{t(`evaluation.cureItems.${code}`, { defaultValue: code })}</Label>
          </div>
        ))}
      </fieldset>
      <div className="flex gap-2">
        <Button disabled={!bidder || chosen.length === 0 || create.isPending} onClick={() => create.mutate(undefined)}>{t('evaluation.cure.issue')}</Button>
        <Button variant="ghost" onClick={onDone}>{t('common.cancel')}</Button>
      </div>
    </div>
  );
}
