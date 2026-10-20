import * as React from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { evaluation } from '@/api/endpoints';
import type { ChecklistItem, FileDetail } from '@/api/types';
import { useSession } from '@/hooks/useSession';
import { useMutate } from '@/hooks/useMutate';
import { QueryState } from '@/components/app/common';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { cn } from '@/lib/utils';

const ANSWERS = ['YES', 'NO', 'NOT_APPLICABLE'] as const;

export function ChecklistView({ file }: { file: FileDetail }) {
  const { t } = useTranslation();
  const q = useQuery({ queryKey: ['checklist', file.id], queryFn: () => evaluation.checklist(file.id) });
  const { committeeRole } = useSession();
  const canEdit = !!committeeRole(file.committeeId);
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t(`checklist.title.${file.itemType}`, { defaultValue: t('checklist.tab') })}</CardTitle>
        <CardDescription>{t(`checklist.intro.${file.itemType}`, { defaultValue: '' })}</CardDescription>
      </CardHeader>
      <CardContent>
        <QueryState query={q} empty={(d) => d.items.length === 0}>
          {(v) => (
            <div className="grid gap-3">
              <ul className="grid gap-3">
                {v.items.map((it) => <ChecklistRow key={it.code} fileId={file.id} itemType={file.itemType} item={it} canEdit={canEdit} />)}
              </ul>
              <p className="text-sm text-muted-foreground">{t('checklist.progress', { done: v.summary.answered, total: v.summary.total })}</p>
              {v.summary.findings > 0 && <Alert variant="warning"><AlertDescription>{t('checklist.findings', { count: v.summary.findings })}</AlertDescription></Alert>}
              {v.summary.suggestedOutcome && (
                <Alert variant="info"><AlertDescription>{t('checklist.suggested', { outcome: t(`checklist.outcome.${v.summary.suggestedOutcome}`, { defaultValue: v.summary.suggestedOutcome }) })}</AlertDescription></Alert>
              )}
            </div>
          )}
        </QueryState>
      </CardContent>
    </Card>
  );
}

function ChecklistRow({ fileId, itemType, item, canEdit }: { fileId: string; itemType: string; item: ChecklistItem; canEdit: boolean }) {
  const { t } = useTranslation();
  const [note, setNote] = React.useState(item.note ?? '');
  const save = useMutate((answer: string) => evaluation.answer(fileId, item.code, answer, note || undefined, item.version), { invalidate: [['checklist', fileId]] });
  const label = t(`checklist.items.${item.code}`, { defaultValue: t(item.labelKey, { defaultValue: `${itemType}.${item.code}` }) });
  return (
    <li className={cn('grid gap-2 rounded-md border p-3', item.answer === 'NO' && 'border-destructive')}>
      <p className="font-medium">{label}</p>
      <div className="flex flex-wrap items-center gap-2" role="radiogroup" aria-label={label}>
        {ANSWERS.map((a) => (
          <button key={a} type="button" role="radio" aria-checked={item.answer === a} disabled={!canEdit || save.isPending}
            onClick={() => save.mutate(a)}
            className={cn('min-h-[40px] rounded-md border px-3 text-sm font-medium',
              item.answer === a ? (a === 'NO' ? 'border-destructive bg-destructive text-destructive-foreground' : 'border-primary bg-primary text-primary-foreground') : 'hover:bg-muted')}>
            {t(`checklist.answer.${a}`)}
          </button>
        ))}
        <Input aria-label={t('common.note')} placeholder={t('common.note')} className="max-w-md flex-1" value={note} disabled={!canEdit}
          onChange={(e) => setNote(e.target.value)} onBlur={() => item.answer && note !== (item.note ?? '') && save.mutate(item.answer)} />
      </div>
    </li>
  );
}
