import * as React from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { tasks } from '@/api/endpoints';
import type { TrackedTask } from '@/api/types';
import { useMutate } from '@/hooks/useMutate';
import { Countdown, PageHeader, QueryState, StatusBadge } from '@/components/app/common';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { SimpleSelect } from '@/components/ui/select';
import { daysUntil, formatDate, formatInstant } from '@/lib/time';

export function TasksPage() {
  const { t } = useTranslation();
  const [status, setStatus] = React.useState('OPEN');
  const q = useQuery({ queryKey: ['tasks', status], queryFn: () => tasks.list(status || undefined) });
  return (
    <div>
      <PageHeader title={t('tasks.title')} subtitle={t('tasks.subtitle')} />
      <div className="mb-3 max-w-xs">
        <SimpleSelect value={status} onChange={setStatus} allowEmpty emptyLabel={t('common.all')}
          options={['OPEN', 'DONE', 'CANCELLED'].map((s) => ({ value: s, label: t(`tasks.status.${s}`) }))} />
      </div>
      <QueryState query={q} empty={(d) => d.length === 0}>
        {(list) => <div className="grid gap-3">{list.map((task) => <TaskCard key={task.id} task={task} />)}</div>}
      </QueryState>
    </div>
  );
}

export function TaskCard({ task }: { task: TrackedTask }) {
  const { t, i18n } = useTranslation();
  const [reference, setReference] = React.useState('');
  const [note, setNote] = React.useState('');
  const done = useMutate(() => tasks.complete(task.id, { reference, note: note || undefined }), { invalidate: [['tasks']], success: t('tasks.completed') });
  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-center gap-2">
          <CardTitle className="text-base">{t(`tasks.kind.${task.kind}`, { defaultValue: task.title })}</CardTitle>
          <StatusBadge ns="tasks.status" value={task.status} />
        </div>
        <p className="text-sm text-muted-foreground">{task.title}</p>
      </CardHeader>
      <CardContent className="grid gap-3">
        {task.details && <p className="text-sm">{task.details}</p>}
        {task.status === 'OPEN' && task.dueDate && <Countdown days={daysUntil(task.dueDate)} label={t('tasks.due', { date: formatDate(task.dueDate, i18n.language) })} />}
        {task.status === 'DONE' && <p className="text-sm">{t('tasks.doneAt', { at: formatInstant(task.doneAt, i18n.language), ref: task.doneReference ?? '' })}</p>}
        {task.status === 'OPEN' && (
          <form className="grid gap-2 sm:grid-cols-2" onSubmit={(e) => { e.preventDefault(); done.mutate(undefined); }}>
            <Field id={`ref-${task.id}`} label={t('tasks.reference')} hint={t('tasks.referenceHint')} required>
              <Input id={`ref-${task.id}`} value={reference} onChange={(e) => setReference(e.target.value)} />
            </Field>
            <Field id={`note-${task.id}`} label={t('common.note')}>
              <Textarea id={`note-${task.id}`} rows={2} value={note} onChange={(e) => setNote(e.target.value)} />
            </Field>
            <Button type="submit" className="justify-self-start" disabled={!reference.trim() || done.isPending}>{t('tasks.markDone')}</Button>
          </form>
        )}
      </CardContent>
    </Card>
  );
}
