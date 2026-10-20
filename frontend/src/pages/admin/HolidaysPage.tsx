import * as React from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Trash2 } from 'lucide-react';
import { holidays } from '@/api/endpoints';
import { useSession } from '@/hooks/useSession';
import { useMutate } from '@/hooks/useMutate';
import { PageHeader, QueryState } from '@/components/app/common';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { SimpleSelect } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatDate } from '@/lib/time';

export function HolidaysPage() {
  const { t, i18n } = useTranslation();
  const { hasRole } = useSession();
  const platform = hasRole('PLATFORM_ADMIN');
  const [year, setYear] = React.useState(new Date().getFullYear());
  const q = useQuery({ queryKey: ['holidays', year], queryFn: () => holidays.list(year) });
  const [date, setDate] = React.useState('');
  const [name, setName] = React.useState('');
  const [kind, setKind] = React.useState<'ADD' | 'REMOVE'>('ADD');
  const add = useMutate(() => holidays.add({ date, name, kind, scope: platform ? 'PLATFORM' : 'INSTITUTION' }), {
    invalidate: [['holidays', year]], onSuccess: () => { setDate(''); setName(''); }, success: t('common.saved'),
  });
  const remove = useMutate((id: string) => holidays.remove(id), { invalidate: [['holidays', year]] });
  const canEdit = (scope: string) => (scope === 'PLATFORM' ? platform : hasRole('INSTITUTION_ADMIN'));
  return (
    <div className="grid gap-4">
      <PageHeader title={t('holidays.title')} subtitle={platform ? t('holidays.platformHelp') : t('holidays.institutionHelp')} />
      <div className="flex items-center gap-2">
        <Button variant="outline" size="sm" onClick={() => setYear(year - 1)}>‹</Button>
        <span className="font-semibold">{year}</span>
        <Button variant="outline" size="sm" onClick={() => setYear(year + 1)}>›</Button>
      </div>
      <Card>
        <CardHeader><CardTitle>{t('holidays.add')}</CardTitle></CardHeader>
        <CardContent className="grid gap-3 sm:grid-cols-4">
          <Field id="h-date" label={t('common.date')}><Input id="h-date" type="date" value={date} onChange={(e) => setDate(e.target.value)} /></Field>
          <Field id="h-name" label={t('common.name')}><Input id="h-name" value={name} onChange={(e) => setName(e.target.value)} /></Field>
          {!platform && (
            <Field id="h-kind" label={t('holidays.kind')}>
              <SimpleSelect id="h-kind" value={kind} onChange={(v) => setKind(v as 'ADD' | 'REMOVE')}
                options={[{ value: 'ADD', label: t('holidays.kinds.ADD') }, { value: 'REMOVE', label: t('holidays.kinds.REMOVE') }]} />
            </Field>
          )}
          <div className="flex items-end"><Button disabled={!date || !name || add.isPending} onClick={() => add.mutate(undefined)}>{t('holidays.add')}</Button></div>
        </CardContent>
      </Card>
      <QueryState query={q} empty={(d) => d.length === 0}>
        {(list) => (
          <Card>
            <Table>
              <TableHeader><TableRow><TableHead>{t('common.date')}</TableHead><TableHead>{t('common.name')}</TableHead><TableHead>{t('holidays.scope')}</TableHead><TableHead /></TableRow></TableHeader>
              <TableBody>
                {list.map((h) => (
                  <TableRow key={h.id}>
                    <TableCell>{formatDate(h.holidayDate, i18n.language)}</TableCell>
                    <TableCell>{h.name} {h.kind === 'REMOVE' && <Badge variant="outline">{t('holidays.kinds.REMOVE')}</Badge>}</TableCell>
                    <TableCell>{t(`holidays.scopes.${h.scope}`)}</TableCell>
                    <TableCell>{canEdit(h.scope) && <Button size="icon" variant="ghost" aria-label={t('common.delete')} onClick={() => remove.mutate(h.id)}><Trash2 className="h-4 w-4" aria-hidden /></Button>}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Card>
        )}
      </QueryState>
    </div>
  );
}
