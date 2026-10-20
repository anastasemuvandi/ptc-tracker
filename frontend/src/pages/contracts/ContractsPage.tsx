import * as React from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Plus } from 'lucide-react';
import { contracts } from '@/api/endpoints';
import type { Contract, ProcurementType } from '@/api/types';
import { useSession } from '@/hooks/useSession';
import { useMutate } from '@/hooks/useMutate';
import { PageHeader, QueryState } from '@/components/app/common';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { SimpleSelect } from '@/components/ui/select';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatDate, formatMoney } from '@/lib/time';

export function ContractsPage() {
  const { t, i18n } = useTranslation();
  const { hasRole, isOfficerAnywhere } = useSession();
  const q = useQuery({ queryKey: ['contracts'], queryFn: contracts.list });
  const [open, setOpen] = React.useState(false);
  return (
    <div>
      <PageHeader title={t('contracts.title')} subtitle={t('contracts.subtitle')}
        actions={(hasRole('CBM') || isOfficerAnywhere) && <Button onClick={() => setOpen(true)}><Plus className="h-4 w-4" aria-hidden />{t('contracts.new')}</Button>} />
      <QueryState query={q} empty={(d) => d.length === 0}>
        {(list) => (
          <Card>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('contracts.ref')}</TableHead>
                  <TableHead>{t('files.titleCol')}</TableHead>
                  <TableHead>{t('contracts.supplier')}</TableHead>
                  <TableHead>{t('contracts.originalValue')}</TableHead>
                  <TableHead>{t('contracts.completion')}</TableHead>
                  <TableHead>{t('common.status')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {list.map((c) => (
                  <TableRow key={c.id}>
                    <TableCell className="font-medium"><Link to={`/contracts/${c.id}`} className="text-primary underline">{c.contractRef}</Link></TableCell>
                    <TableCell>{c.title}</TableCell>
                    <TableCell>{c.supplierRef ?? '—'}</TableCell>
                    <TableCell>{formatMoney(c.originalValue, c.currency, i18n.language)}</TableCell>
                    <TableCell>{formatDate(c.currentCompletionDate, i18n.language)}</TableCell>
                    <TableCell>{t(`contracts.status.${c.status}`, { defaultValue: c.status })}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Card>
        )}
      </QueryState>
      <ContractDialog open={open} onClose={() => setOpen(false)} />
    </div>
  );
}

const PROC: ProcurementType[] = ['WORKS', 'GOODS', 'CONSULTANCY', 'NON_CONSULTANCY'];

function ContractDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t } = useTranslation();
  const { me } = useSession();
  const [c, setC] = React.useState<Partial<Contract>>({ procurementType: 'WORKS', currency: me?.institution?.defaultCurrency ?? 'RWF', isFramework: false });
  const set = <K extends keyof Contract>(k: K, v: Contract[K] | undefined) => setC((s) => ({ ...s, [k]: v }));
  const save = useMutate(() => contracts.create(c), { invalidate: [['contracts']], onSuccess: onClose, success: t('common.saved') });
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-2xl" closeLabel={t('common.close')}>
        <DialogHeader><DialogTitle>{t('contracts.new')}</DialogTitle></DialogHeader>
        <p className="text-sm text-muted-foreground">{t('contracts.referenceOnly')}</p>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field id="c-ref" label={t('contracts.ref')} required><Input id="c-ref" value={c.contractRef ?? ''} onChange={(e) => set('contractRef', e.target.value)} /></Field>
          <Field id="c-tender" label={t('files.tenderRef')}><Input id="c-tender" value={c.tenderRef ?? ''} onChange={(e) => set('tenderRef', e.target.value)} /></Field>
          <Field id="c-title" label={t('files.titleCol')} className="sm:col-span-2" required><Input id="c-title" value={c.title ?? ''} onChange={(e) => set('title', e.target.value)} /></Field>
          <Field id="c-supplier" label={t('contracts.supplier')}><Input id="c-supplier" value={c.supplierRef ?? ''} onChange={(e) => set('supplierRef', e.target.value)} /></Field>
          <Field id="c-ptype" label={t('files.procurementType')}>
            <SimpleSelect id="c-ptype" value={c.procurementType ?? 'WORKS'} onChange={(v) => set('procurementType', v as ProcurementType)}
              options={PROC.map((p) => ({ value: p, label: t(`procurementType.${p}`) }))} />
          </Field>
          <Field id="c-value" label={t('contracts.originalValue')} required>
            <Input id="c-value" inputMode="decimal" value={String(c.originalValue ?? '')} onChange={(e) => set('originalValue', e.target.value)} />
          </Field>
          <Field id="c-cur" label={t('common.currency')} hint={t('contracts.currencyHint')} required>
            <Input id="c-cur" maxLength={3} value={c.currency ?? ''} onChange={(e) => set('currency', e.target.value.toUpperCase())} />
          </Field>
          <Field id="c-signed" label={t('contracts.signed')}><Input id="c-signed" type="date" value={c.signedDate ?? ''} onChange={(e) => set('signedDate', e.target.value || null)} /></Field>
          <Field id="c-comp" label={t('contracts.originalCompletion')} required>
            <Input id="c-comp" type="date" value={c.originalCompletionDate ?? ''} onChange={(e) => set('originalCompletionDate', e.target.value)} />
          </Field>
          <Field id="c-ps-ref" label={t('contracts.perfSecurityRef')}><Input id="c-ps-ref" value={c.perfSecurityRef ?? ''} onChange={(e) => set('perfSecurityRef', e.target.value)} /></Field>
          <Field id="c-ps-amt" label={t('contracts.perfSecurityAmount')}><Input id="c-ps-amt" inputMode="decimal" value={String(c.perfSecurityAmount ?? '')} onChange={(e) => set('perfSecurityAmount', e.target.value || null)} /></Field>
          <Field id="c-ps-cur" label={t('contracts.perfSecurityCurrency')}><Input id="c-ps-cur" maxLength={3} value={c.perfSecurityCurrency ?? ''} onChange={(e) => set('perfSecurityCurrency', e.target.value.toUpperCase() || null)} /></Field>
          <Field id="c-ps-exp" label={t('contracts.perfSecurityExpiry')}><Input id="c-ps-exp" type="date" value={c.perfSecurityExpiry ?? ''} onChange={(e) => set('perfSecurityExpiry', e.target.value || null)} /></Field>
          <div className="flex items-center gap-2">
            <Checkbox id="c-fw" checked={!!c.isFramework} onCheckedChange={(v) => set('isFramework', v === true)} />
            <Label htmlFor="c-fw">{t('contracts.framework')}</Label>
          </div>
          {c.isFramework && (
            <Field id="c-fw-exp" label={t('contracts.frameworkExpiry')}><Input id="c-fw-exp" type="date" value={c.frameworkExpiry ?? ''} onChange={(e) => set('frameworkExpiry', e.target.value || null)} /></Field>
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={save.isPending || !c.contractRef || !c.title || !c.originalValue || !c.originalCompletionDate || c.currency?.length !== 3}
            onClick={() => save.mutate(undefined)}>{t('common.save')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
