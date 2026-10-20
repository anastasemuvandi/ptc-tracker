import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { evaluation } from '@/api/endpoints';
import type { BidderResult, EvaluationStage } from '@/api/types';
import { useMutate } from '@/hooks/useMutate';
import { StatusBadge } from '@/components/app/common';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Field } from '@/components/ui/field';
import { SimpleSelect } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';

const VAGUE = /^\s*(non[- ]?compliant|non[- ]?responsive|not compliant|failed?|ntibyujuje|non conforme)\.?\s*$/i;

/**
 * Per-bidder outcome by bidder reference. A failure must name the
 * requirement not met and the committee's reason; a bare "non-compliant"
 * is not accepted. Ranked order only — never scores.
 */
export function BidderResults({ fileId, stage, results, canEdit, requirements }: {
  fileId: string; stage: EvaluationStage; results: BidderResult[]; canEdit: boolean; requirements: string[];
}) {
  const { t } = useTranslation();
  const [bidderRef, setBidderRef] = React.useState('');
  const [result, setResult] = React.useState<'PASS' | 'FAIL'>('PASS');
  const [requirement, setRequirement] = React.useState('');
  const [reason, setReason] = React.useState('');
  const [rank, setRank] = React.useState('');
  const reasonInvalid = result === 'FAIL' && (reason.trim().length < 10 || VAGUE.test(reason));
  const save = useMutate(() => evaluation.saveBidder(fileId, stage.code, {
    bidderRef: bidderRef.trim(), result,
    failedRequirement: result === 'FAIL' ? requirement : null,
    failureReason: result === 'FAIL' ? reason.trim() : null,
    rank: rank ? Number(rank) : null,
  }), {
    invalidate: [['evaluation', fileId]],
    onSuccess: () => { setBidderRef(''); setReason(''); setRank(''); setRequirement(''); setResult('PASS'); },
  });
  const showRank = stage.code !== 'A1';
  return (
    <div className="grid gap-3">
      <h3 className="font-semibold">{t('evaluation.bidders.title')}</h3>
      {results.length > 0 && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t('evaluation.bidders.ref')}</TableHead>
              <TableHead>{t('evaluation.bidders.result')}</TableHead>
              <TableHead>{t('evaluation.bidders.requirement')}</TableHead>
              <TableHead>{t('evaluation.bidders.reason')}</TableHead>
              {showRank && <TableHead>{t('evaluation.bidders.rank')}</TableHead>}
            </TableRow>
          </TableHeader>
          <TableBody>
            {[...results].sort((a, b) => (a.rank ?? 999) - (b.rank ?? 999) || a.bidderRef.localeCompare(b.bidderRef)).map((r) => (
              <TableRow key={r.id}>
                <TableCell className="font-medium">{r.bidderRef}</TableCell>
                <TableCell><StatusBadge ns="evaluation.result" value={r.result} /></TableCell>
                <TableCell>{r.failedRequirement ? t(`evaluation.requirements.${r.failedRequirement}`, { defaultValue: r.failedRequirement }) : '—'}</TableCell>
                <TableCell className="max-w-sm text-sm">{r.failureReason ?? '—'}</TableCell>
                {showRank && <TableCell>{r.rank ?? '—'}</TableCell>}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
      {canEdit && (
        <form className="grid gap-3 rounded-md border p-3 sm:grid-cols-4" onSubmit={(e) => { e.preventDefault(); save.mutate(undefined); }}>
          <Field id={`br-${stage.code}`} label={t('evaluation.bidders.ref')} hint={t('evaluation.bidders.refHint')} required>
            <Input id={`br-${stage.code}`} value={bidderRef} onChange={(e) => setBidderRef(e.target.value)} required />
          </Field>
          <Field id={`res-${stage.code}`} label={t('evaluation.bidders.result')}>
            <SimpleSelect id={`res-${stage.code}`} value={result} onChange={(v) => setResult(v as 'PASS' | 'FAIL')}
              options={['PASS', 'FAIL'].map((x) => ({ value: x, label: t(`evaluation.result.${x}`) }))} />
          </Field>
          {showRank && (
            <Field id={`rank-${stage.code}`} label={t('evaluation.bidders.rank')}>
              <Input id={`rank-${stage.code}`} type="number" min={1} value={rank} onChange={(e) => setRank(e.target.value)} />
            </Field>
          )}
          {result === 'FAIL' && (
            <>
              <Field id={`req-${stage.code}`} label={t('evaluation.bidders.requirement')} required className="sm:col-span-2">
                {requirements.length > 0 ? (
                  <SimpleSelect id={`req-${stage.code}`} value={requirement} onChange={setRequirement} placeholder={t('common.choose')}
                    options={requirements.map((r) => ({ value: r, label: t(`evaluation.requirements.${r}`, { defaultValue: r }) }))} />
                ) : (
                  <Input id={`req-${stage.code}`} value={requirement} onChange={(e) => setRequirement(e.target.value)} placeholder={t('evaluation.bidders.criterionPlaceholder')} />
                )}
              </Field>
              <Field id={`reason-${stage.code}`} label={t('evaluation.bidders.reason')} required className="sm:col-span-4"
                error={reason && reasonInvalid ? t('evaluation.bidders.reasonTooVague') : undefined}>
                <Textarea id={`reason-${stage.code}`} value={reason} onChange={(e) => setReason(e.target.value)} />
              </Field>
            </>
          )}
          <div className="sm:col-span-4">
            <Button type="submit" disabled={!bidderRef.trim() || save.isPending || (result === 'FAIL' && (!requirement || reasonInvalid))}>
              {t('evaluation.bidders.record')}
            </Button>
          </div>
        </form>
      )}
    </div>
  );
}
