import * as React from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { Check, X } from 'lucide-react';
import { sessions } from '@/api/endpoints';
import type { AgendaActivity, AgendaItem, SessionDetail } from '@/api/types';
import { useMutate } from '@/hooks/useMutate';
import { StatusBadge } from '@/components/app/common';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { Field } from '@/components/ui/field';
import { Textarea } from '@/components/ui/textarea';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog';

/** Type A activities are the evaluation stages and share their names. */
export function activityLabel(t: TFunction, code: string): string {
  return /^A\d$/.test(code) ? t(`evaluation.stages.${code}.name`) : t(`activity.code.${code}`, { defaultValue: code });
}

/** Activities of one agenda item, recorded as completed or not while the session is in progress. */
export function ActivityList({ sessionId, item, canRecord }: { sessionId: string; item: AgendaItem; canRecord: boolean }) {
  const { t } = useTranslation();
  const [notDone, setNotDone] = React.useState<AgendaActivity | null>(null);
  const complete = useMutate((a: AgendaActivity) => sessions.recordActivity(sessionId, a.id, { status: 'COMPLETED' }),
    { invalidate: [['session', sessionId]], success: t('activity.recorded') });
  if (!item.activities?.length) return null;
  return (
    <ul className="grid gap-1 border-t px-3 py-2 text-sm">
      {item.activities.map((a) => (
        <li key={a.id} className="flex flex-wrap items-center gap-2">
          <span className="min-w-0 flex-1">
            {activityLabel(t, a.activityCode)}
            {a.carriedFromSession && <span className="ml-1 text-xs text-muted-foreground">({t('activity.carriedFrom', { session: a.carriedFromSession })})</span>}
            {a.status === 'NOT_COMPLETED' && a.note && <span className="block text-xs text-muted-foreground">{a.note}</span>}
          </span>
          <StatusBadge ns="activity.status" value={a.status} />
          {canRecord && a.status !== 'COMPLETED' && (
            <Button size="sm" variant="outline" disabled={complete.isPending} onClick={() => complete.mutate(a)}
              aria-label={`${t('activity.markCompleted')}: ${activityLabel(t, a.activityCode)}`}>
              <Check className="h-4 w-4" aria-hidden />{t('activity.markCompleted')}
            </Button>
          )}
          {canRecord && a.status === 'PLANNED' && (
            <Button size="sm" variant="outline" onClick={() => setNotDone(a)}
              aria-label={`${t('activity.markNotCompleted')}: ${activityLabel(t, a.activityCode)}`}>
              <X className="h-4 w-4" aria-hidden />{t('activity.markNotCompleted')}
            </Button>
          )}
        </li>
      ))}
      <NotCompletedDialog sessionId={sessionId} activity={notDone} onClose={() => setNotDone(null)} />
    </ul>
  );
}

function NotCompletedDialog({ sessionId, activity, onClose }: { sessionId: string; activity: AgendaActivity | null; onClose: () => void }) {
  const { t } = useTranslation();
  const [note, setNote] = React.useState('');
  const save = useMutate(() => sessions.recordActivity(sessionId, activity!.id, { status: 'NOT_COMPLETED', note }), {
    invalidate: [['session', sessionId]], success: t('activity.recorded'), onSuccess: () => { setNote(''); onClose(); },
  });
  return (
    <Dialog open={!!activity} onOpenChange={(o) => !o && onClose()}>
      <DialogContent closeLabel={t('common.close')}>
        <DialogHeader>
          <DialogTitle>{activity ? activityLabel(t, activity.activityCode) : ''}</DialogTitle>
          <DialogDescription>{t('activity.notCompletedHelp')}</DialogDescription>
        </DialogHeader>
        <Field id="act-note" label={t('common.reason')} required>
          <Textarea id="act-note" value={note} onChange={(e) => setNote(e.target.value)} />
        </Field>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={!note.trim() || save.isPending} onClick={() => save.mutate(undefined)}>{t('activity.markNotCompleted')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** Checkboxes of the activities for a file; those still outstanding are preselected. */
export function ActivityPicker({ sessionId, fileId, value, onChange }: {
  sessionId: string; fileId: string; value: string[]; onChange: (codes: string[]) => void;
}) {
  const { t } = useTranslation();
  const options = useQuery({ queryKey: ['activity-options', sessionId, fileId], queryFn: () => sessions.activityOptions(sessionId, fileId) });
  React.useEffect(() => {
    if (options.data) onChange(options.data.outstanding);
    // Preselect once per file.
  }, [options.data]);
  if (!options.data) return null;
  const { allowed, outstanding } = options.data;
  return (
    <fieldset className="grid gap-2">
      <legend className="mb-1 text-sm font-medium">{t('activity.toPerform')}</legend>
      {allowed.map((code) => {
        const done = !outstanding.includes(code);
        return (
          <div key={code} className="flex items-center gap-2">
            <Checkbox id={`act-${code}`} checked={value.includes(code)} disabled={done}
              onCheckedChange={(v) => onChange(v === true ? allowed.filter((c) => c === code || value.includes(c)) : value.filter((c) => c !== code))} />
            <Label htmlFor={`act-${code}`} className={done ? 'text-muted-foreground' : undefined}>
              {activityLabel(t, code)}{done && ` — ${t('activity.alreadyCompleted')}`}
            </Label>
          </div>
        );
      })}
    </fieldset>
  );
}

/** Activities not completed in earlier sessions, offered for this one. */
export function CarryOverCard({ detail }: { detail: SessionDetail }) {
  const { t } = useTranslation();
  const sessionId = detail.session.id;
  const q = useQuery({ queryKey: ['carry-over', sessionId], queryFn: () => sessions.carryOverCandidates(sessionId) });
  const [chosen, setChosen] = React.useState<string[]>([]);
  const carry = useMutate(() => sessions.carryOver(sessionId, chosen), {
    invalidate: [['session', sessionId], ['carry-over', sessionId]], success: t('activity.carriedOver'), onSuccess: () => setChosen([]),
  });
  const list = q.data ?? [];
  if (list.length === 0) return null;
  return (
    <Card className="mb-4 border-warning">
      <CardHeader>
        <CardTitle>{t('activity.carryOverTitle')}</CardTitle>
        <p className="text-sm text-muted-foreground">{t('activity.carryOverHelp')}</p>
      </CardHeader>
      <CardContent className="grid gap-3">
        {list.map((c) => (
          <div key={c.id} className="flex items-start gap-2">
            <Checkbox id={`co-${c.id}`} checked={chosen.includes(c.id)}
              onCheckedChange={(v) => setChosen((s) => (v === true ? [...s, c.id] : s.filter((x) => x !== c.id)))} />
            <Label htmlFor={`co-${c.id}`} className="grid gap-0.5 font-normal">
              <span className="font-medium">{c.fileNumber} — {activityLabel(t, c.activityCode)}</span>
              <span className="text-xs text-muted-foreground">{t('activity.notCompletedIn', { session: c.sessionNumber })}: {c.note}</span>
            </Label>
          </div>
        ))}
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" size="sm" onClick={() => setChosen(list.map((c) => c.id))}>{t('activity.selectAll')}</Button>
          <Button size="sm" disabled={chosen.length === 0 || carry.isPending} onClick={() => carry.mutate(undefined)}>{t('activity.addToAgenda')}</Button>
        </div>
      </CardContent>
    </Card>
  );
}

/** "3 of 5 activities completed" for the agenda header. */
export function activityProgress(agenda: AgendaItem[]): { done: number; total: number; planned: number } {
  const all = agenda.flatMap((i) => i.activities ?? []);
  return {
    done: all.filter((a) => a.status === 'COMPLETED').length,
    total: all.length,
    planned: all.filter((a) => a.status === 'PLANNED').length,
  };
}
