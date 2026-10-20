import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { MessageSquareWarning } from 'lucide-react';
import { evaluation } from '@/api/endpoints';
import type { Clarification } from '@/api/types';
import { useMutate } from '@/hooks/useMutate';
import { StatusBadge } from '@/components/app/common';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Field } from '@/components/ui/field';
import { SimpleSelect } from '@/components/ui/select';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatDate, todayKigali } from '@/lib/time';

export function ClarificationLog({ fileId, items, canEdit }: { fileId: string; items: Clarification[]; canEdit: boolean }) {
  const { t, i18n } = useTranslation();
  const [d, setD] = React.useState<Partial<Clarification> | null>(null);
  const save = useMutate(() => (d!.id ? evaluation.updateClarification(d!.id, d!) : evaluation.addClarification(fileId, d!)), {
    invalidate: [['evaluation', fileId]], onSuccess: () => setD(null), success: t('common.saved'),
  });
  const responded = !!d?.responseReceivedOn;
  return (
    <Card>
      <CardHeader><CardTitle>{t('evaluation.clar.title')}</CardTitle></CardHeader>
      <CardContent className="grid gap-3">
        <Alert variant="warning">
          <MessageSquareWarning className="h-4 w-4" aria-hidden />
          <AlertTitle>{t('evaluation.clar.reminderTitle')}</AlertTitle>
          <AlertDescription>{t('evaluation.clar.reminder')}</AlertDescription>
        </Alert>
        {items.length > 0 && (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('evaluation.bidders.ref')}</TableHead>
                <TableHead>{t('evaluation.clar.issuedOn')}</TableHead>
                <TableHead>{t('evaluation.clar.subject')}</TableHead>
                <TableHead>{t('evaluation.clar.responseOn')}</TableHead>
                <TableHead>{t('evaluation.clar.treatment')}</TableHead>
                <TableHead>{t('evaluation.clar.effect')}</TableHead>
                <TableHead />
              </TableRow>
            </TableHeader>
            <TableBody>
              {items.map((c) => (
                <TableRow key={c.id}>
                  <TableCell className="font-medium">{c.bidderRef}</TableCell>
                  <TableCell>{formatDate(c.issuedOn, i18n.language)}</TableCell>
                  <TableCell className="max-w-xs text-sm">{c.subject}</TableCell>
                  <TableCell>{formatDate(c.responseReceivedOn, i18n.language)}</TableCell>
                  <TableCell className="max-w-xs text-sm">{c.treatment ?? '—'}</TableCell>
                  <TableCell><StatusBadge ns="evaluation.clar.effects" value={c.effect} /></TableCell>
                  <TableCell>{canEdit && <Button size="sm" variant="outline" onClick={() => setD(c)}>{t('common.edit')}</Button>}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
        {canEdit && !d && <Button variant="outline" className="justify-self-start" onClick={() => setD({ issuedOn: todayKigali(), effect: 'PENDING' })}>{t('evaluation.clar.add')}</Button>}
        {d && (
          <form className="grid gap-3 rounded-md border p-3 sm:grid-cols-2" onSubmit={(e) => { e.preventDefault(); save.mutate(undefined); }}>
            <Field id="cl-bidder" label={t('evaluation.bidders.ref')} required><Input id="cl-bidder" value={d.bidderRef ?? ''} disabled={!!d.id} onChange={(e) => setD({ ...d, bidderRef: e.target.value })} /></Field>
            <Field id="cl-issued" label={t('evaluation.clar.issuedOn')} required><Input id="cl-issued" type="date" value={d.issuedOn ?? ''} onChange={(e) => setD({ ...d, issuedOn: e.target.value })} /></Field>
            <Field id="cl-subject" label={t('evaluation.clar.subject')} className="sm:col-span-2" required><Textarea id="cl-subject" value={d.subject ?? ''} onChange={(e) => setD({ ...d, subject: e.target.value })} /></Field>
            <Field id="cl-resp" label={t('evaluation.clar.responseOn')}><Input id="cl-resp" type="date" value={d.responseReceivedOn ?? ''} onChange={(e) => setD({ ...d, responseReceivedOn: e.target.value || null })} /></Field>
            <Field id="cl-effect" label={t('evaluation.clar.effectQuestion')} required={responded}>
              <SimpleSelect id="cl-effect" value={d.effect ?? 'PENDING'} onChange={(v) => setD({ ...d, effect: v as Clarification['effect'] })}
                options={(responded ? ['CLARIFIED', 'ATTEMPTED_ALTERATION'] : ['PENDING']).map((x) => ({ value: x, label: t(`evaluation.clar.effects.${x}`) }))} />
            </Field>
            {responded && (
              <Field id="cl-treat" label={t('evaluation.clar.treatment')} className="sm:col-span-2" required>
                <Textarea id="cl-treat" value={d.treatment ?? ''} onChange={(e) => setD({ ...d, treatment: e.target.value })} />
              </Field>
            )}
            {d.effect === 'ATTEMPTED_ALTERATION' && (
              <Alert variant="destructive" className="sm:col-span-2"><AlertDescription>{t('evaluation.clar.alterationNote')}</AlertDescription></Alert>
            )}
            <div className="flex gap-2 sm:col-span-2">
              <Button type="submit" disabled={save.isPending || !d.bidderRef || !d.subject || (responded && (d.effect === 'PENDING' || !d.treatment?.trim()))}>{t('common.save')}</Button>
              <Button type="button" variant="ghost" onClick={() => setD(null)}>{t('common.cancel')}</Button>
            </div>
          </form>
        )}
      </CardContent>
    </Card>
  );
}
