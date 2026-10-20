import * as React from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { institution } from '@/api/endpoints';
import type { Institution } from '@/api/types';
import { useMutate } from '@/hooks/useMutate';
import { useErrorText } from '@/hooks/useErrorText';
import { PageHeader, QueryState } from '@/components/app/common';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { toast } from '@/components/ui/toast';

export function InstitutionPage() {
  const q = useQuery({ queryKey: ['institution'], queryFn: institution.get });
  return <QueryState query={q}>{(i) => <InstitutionForm inst={i} />}</QueryState>;
}

function InstitutionForm({ inst }: { inst: Institution }) {
  const { t } = useTranslation();
  const errorText = useErrorText();
  const [f, setF] = React.useState<Institution>(inst);
  React.useEffect(() => setF(inst), [inst]);
  const save = useMutate(() => institution.update(f), { invalidate: [['institution']], success: t('common.saved') });
  const upload = useMutate((file: File) => institution.uploadTemplate(file), { invalidate: [['institution']], success: t('institution.templateUploaded') });
  return (
    <div className="grid max-w-3xl gap-4">
      <PageHeader title={t('institution.title')} subtitle={`${inst.name} (${inst.abbreviation})`} />
      <Card>
        <CardHeader><CardTitle>{t('institution.details')}</CardTitle></CardHeader>
        <CardContent className="grid gap-3 sm:grid-cols-2">
          <Field id="in-name" label={t('common.name')} className="sm:col-span-2"><Input id="in-name" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} /></Field>
          <Field id="in-type" label={t('institution.type')}><Input id="in-type" value={f.institutionType} onChange={(e) => setF({ ...f, institutionType: e.target.value })} /></Field>
          <Field id="in-sup" label={t('institution.supervisingAuthority')}><Input id="in-sup" value={f.supervisingAuthority ?? ''} onChange={(e) => setF({ ...f, supervisingAuthority: e.target.value })} /></Field>
          <Field id="in-cur" label={t('institution.defaultCurrency')}><Input id="in-cur" maxLength={3} value={f.defaultCurrency} onChange={(e) => setF({ ...f, defaultCurrency: e.target.value.toUpperCase() })} /></Field>
        </CardContent>
      </Card>
      <Card>
        <CardHeader><CardTitle>{t('institution.minutes')}</CardTitle><CardDescription>{t('institution.minutesHelp')}</CardDescription></CardHeader>
        <CardContent className="grid gap-3 sm:grid-cols-2">
          <Field id="in-letter" label={t('institution.letterhead')} className="sm:col-span-2"><Textarea id="in-letter" value={f.minutesLetterhead ?? ''} onChange={(e) => setF({ ...f, minutesLetterhead: e.target.value })} /></Field>
          <Field id="in-font" label={t('institution.font')}><Input id="in-font" value={f.minutesFontFamily} onChange={(e) => setF({ ...f, minutesFontFamily: e.target.value })} /></Field>
          <Field id="in-size" label={t('institution.fontSize')}><Input id="in-size" type="number" min={8} max={16} value={f.minutesFontSize} onChange={(e) => setF({ ...f, minutesFontSize: Number(e.target.value) })} /></Field>
          <div className="grid gap-2 sm:col-span-2">
            <Label htmlFor="in-template">{t('institution.template')}</Label>
            <Input id="in-template" type="file" accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
              onChange={(e) => { const file = e.target.files?.[0]; if (file) upload.mutate(file); e.target.value = ''; }} />
            <p className="text-xs text-muted-foreground">{inst.hasMinutesTemplate ? t('institution.templatePresent') : t('institution.templateAbsent')}</p>
            {inst.hasMinutesTemplate && <Button variant="link" className="justify-self-start p-0" onClick={() => void institution.downloadTemplate().catch((e) => toast({ title: errorText(e), tone: 'destructive' }))}>{t('institution.downloadTemplate')}</Button>}
          </div>
        </CardContent>
      </Card>
      <Card>
        <CardHeader><CardTitle>{t('institution.calendar')}</CardTitle></CardHeader>
        <CardContent className="flex items-start gap-2">
          <Checkbox id="in-hol" checked={f.holidayOverride} onCheckedChange={(c) => setF({ ...f, holidayOverride: c === true })} />
          <Label htmlFor="in-hol" className="leading-snug">{t('institution.holidayOverride')}</Label>
        </CardContent>
      </Card>
      <Button className="justify-self-start" disabled={save.isPending} onClick={() => save.mutate(undefined)}>{t('common.save')}</Button>
    </div>
  );
}
