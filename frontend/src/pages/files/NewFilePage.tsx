import * as React from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { files, committees, identity, contracts } from '@/api/endpoints';
import type { ItemType, NewFile, ProcurementType } from '@/api/types';
import { useMutate } from '@/hooks/useMutate';
import { PageHeader } from '@/components/app/common';
import { Card, CardContent } from '@/components/ui/card';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { SimpleSelect } from '@/components/ui/select';
import { Button } from '@/components/ui/button';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { todayKigali } from '@/lib/time';

const TYPES: ItemType[] = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'];
const PROC: ProcurementType[] = ['WORKS', 'GOODS', 'CONSULTANCY', 'NON_CONSULTANCY'];

export function NewFilePage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const presetType = params.get('type');
  const cs = useQuery({ queryKey: ['committees'], queryFn: committees.list });
  const users = useQuery({ queryKey: ['users'], queryFn: identity.users });
  const cts = useQuery({ queryKey: ['contracts'], queryFn: contracts.list });
  const [f, setF] = React.useState<NewFile>({
    committeeId: params.get('committeeId') ?? '',
    itemType: TYPES.includes(presetType as ItemType) ? (presetType as ItemType) : 'A',
    title: '', dateReceived: todayKigali() });
  const set = <K extends keyof NewFile>(k: K, v: NewFile[K]) => setF((s) => ({ ...s, [k]: v }));
  const needsContract = ['C', 'D', 'G'].includes(f.itemType);
  const create = useMutate(() => files.create(clean(f)), { onSuccess: (d) => navigate(`/files/${d.id}`), success: t('files.created') });

  return (
    <div className="max-w-3xl">
      <PageHeader title={t('files.new')} subtitle={t('files.newHelp')} />
      <Alert variant="info" className="mb-4"><AlertDescription>{t('app.scopeNotice')}</AlertDescription></Alert>
      <Card>
        <CardContent className="pt-5">
          <form className="grid gap-4 sm:grid-cols-2" onSubmit={(e) => { e.preventDefault(); create.mutate(undefined); }}>
            <Field id="committee" label={t('common.committee')} required>
              <SimpleSelect id="committee" value={f.committeeId} onChange={(v) => set('committeeId', v)} placeholder={t('common.choose')}
                options={(cs.data ?? []).filter((c) => c.status === 'ACTIVE').map((c) => ({ value: c.id, label: `${c.code} — ${c.name}` }))} />
            </Field>
            <Field id="type" label={t('files.type')} hint={t(`itemTypeHelp.${f.itemType}`)} required>
              <SimpleSelect id="type" value={f.itemType} onChange={(v) => set('itemType', v as ItemType)}
                options={TYPES.map((x) => ({ value: x, label: `${x} — ${t(`itemType.${x}`)}` }))} />
            </Field>
            <Field id="title" label={t('files.titleCol')} className="sm:col-span-2" required>
              <Input id="title" value={f.title} onChange={(e) => set('title', e.target.value)} required />
            </Field>
            <Field id="tenderRef" label={t('files.tenderRef')} hint={t('files.refHint')}>
              <Input id="tenderRef" value={f.tenderRef ?? ''} onChange={(e) => set('tenderRef', e.target.value)} />
            </Field>
            <Field id="contract" label={t('files.contract')} required={needsContract}>
              <SimpleSelect id="contract" value={f.contractId ?? ''} onChange={(v) => set('contractId', v || undefined)} allowEmpty
                options={(cts.data ?? []).map((c) => ({ value: c.id, label: `${c.contractRef} — ${c.title}` }))} />
            </Field>
            <Field id="ptype" label={t('files.procurementType')}>
              <SimpleSelect id="ptype" value={f.procurementType ?? ''} onChange={(v) => set('procurementType', (v || undefined) as ProcurementType | undefined)} allowEmpty
                options={PROC.map((p) => ({ value: p, label: t(`procurementType.${p}`) }))} />
            </Field>
            <Field id="method" label={t('files.method')}>
              <Input id="method" value={f.procurementMethod ?? ''} onChange={(e) => set('procurementMethod', e.target.value)} />
            </Field>
            <Field id="officer" label={t('files.officer')}>
              <SimpleSelect id="officer" value={f.procurementOfficerId ?? ''} onChange={(v) => set('procurementOfficerId', v || undefined)} allowEmpty
                options={(users.data ?? []).filter((u) => u.isProcurementOfficer).map((u) => ({ value: u.id, label: u.fullName }))} />
            </Field>
            <Field id="received" label={t('files.received')} required>
              <Input id="received" type="date" value={f.dateReceived} onChange={(e) => set('dateReceived', e.target.value)} required />
            </Field>
            <Field id="target" label={t('files.target')} hint={t('files.targetHint')}>
              <Input id="target" type="date" value={f.targetDate ?? ''} onChange={(e) => set('targetDate', e.target.value || undefined)} />
            </Field>
            {f.itemType === 'A' && (
              <Field id="validity" label={t('files.bidValidityExpiry')} hint={t('files.bidValidityHint')} required>
                <Input id="validity" type="date" value={f.bidValidityExpiry ?? ''} onChange={(e) => set('bidValidityExpiry', e.target.value || undefined)} required />
              </Field>
            )}
            {f.itemType === 'H' && (
              <>
                <Field id="refRef" label={t('files.referralRef')} required>
                  <Input id="refRef" value={f.referralRef ?? ''} onChange={(e) => set('referralRef', e.target.value)} required />
                </Field>
                <Field id="refReason" label={t('files.referralReason')} className="sm:col-span-2" required>
                  <Textarea id="refReason" value={f.referralReason ?? ''} onChange={(e) => set('referralReason', e.target.value)} required />
                </Field>
              </>
            )}
            <div className="sm:col-span-2">
              <Button type="submit" disabled={create.isPending || !f.committeeId || !f.title || (needsContract && !f.contractId)}>{t('files.create')}</Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}

function clean(f: NewFile): NewFile {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(f)) if (v !== '' && v !== undefined) out[k] = v;
  return out as unknown as NewFile;
}
