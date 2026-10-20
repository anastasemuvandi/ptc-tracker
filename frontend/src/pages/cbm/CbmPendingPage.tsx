import * as React from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { decisions } from '@/api/endpoints';
import type { CbmResponse, PendingRecommendation } from '@/api/types';
import { useMutate } from '@/hooks/useMutate';
import { ItemTypeBadge, PageHeader, QueryState } from '@/components/app/common';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Textarea } from '@/components/ui/textarea';
import { SimpleSelect } from '@/components/ui/select';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { formatInstant } from '@/lib/time';

export function CbmPendingPage() {
  const { t } = useTranslation();
  const q = useQuery({ queryKey: ['cbm-pending'], queryFn: decisions.pendingForCbm });
  return (
    <div>
      <PageHeader title={t('cbm.pendingTitle')} subtitle={t('cbm.pendingSubtitle')} />
      <Alert variant="info" className="mb-4"><AlertDescription>{t('cbm.separateAct')}</AlertDescription></Alert>
      <QueryState query={q} empty={(d) => d.length === 0}>
        {(list) => <div className="grid gap-3">{list.map((r) => <PendingCard key={r.decisionId} rec={r} />)}</div>}
      </QueryState>
    </div>
  );
}

export function PendingCard({ rec, onDone }: { rec: PendingRecommendation; onDone?: () => void }) {
  const { t, i18n } = useTranslation();
  const [response, setResponse] = React.useState<CbmResponse['response'] | ''>('');
  const [reasons, setReasons] = React.useState('');
  const needsReasons = response !== '' && response !== 'APPROVED';
  const respond = useMutate(() => decisions.cbmRespond(rec.decisionId, { response: response as CbmResponse['response'], reasons: reasons || undefined }), {
    invalidate: [['cbm-pending'], ['file', rec.fileId]], success: t('cbm.recorded'), onSuccess: () => onDone?.(),
  });
  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-center gap-2">
          <ItemTypeBadge type={rec.itemType} />
          <CardTitle className="text-base"><Link className="text-primary underline" to={`/files/${rec.fileId}`}>{rec.fileNumber}</Link> — {rec.title}</CardTitle>
        </div>
        <p className="text-sm text-muted-foreground">
          {rec.committeeCode} · {t('sessions.session')} {rec.sessionNumber} · {t('cbm.issuedAt', { at: formatInstant(rec.issuedAt, i18n.language) })}
        </p>
      </CardHeader>
      <CardContent className="grid gap-3">
        <div className="rounded-md bg-muted p-3">
          <p className="font-semibold">{t(`recommendation.${rec.recommendationCode}`, { defaultValue: rec.recommendationCode })}</p>
          <p className="mt-1 whitespace-pre-wrap text-sm">{rec.recommendationText}</p>
          <p className="mt-2 text-sm">{t('voting.tally', { for: rec.votesFor, against: rec.votesAgainst, abstained: rec.votesAbstained })}</p>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field id={`resp-${rec.decisionId}`} label={t('cbm.responseLabel')}>
            <SimpleSelect id={`resp-${rec.decisionId}`} value={response} onChange={(v) => setResponse(v as CbmResponse['response'])} placeholder={t('common.choose')}
              options={(['APPROVED', 'RETURNED_FOR_CORRECTION', 'OTHER'] as const).map((r) => ({ value: r, label: t(`cbm.response.${r}`) }))} />
          </Field>
          {needsReasons && (
            <Field id={`reasons-${rec.decisionId}`} label={t('cbm.reasons')} className="sm:col-span-2" required>
              <Textarea id={`reasons-${rec.decisionId}`} value={reasons} onChange={(e) => setReasons(e.target.value)} />
            </Field>
          )}
        </div>
        <Button className="justify-self-start" disabled={!response || (needsReasons && !reasons.trim()) || respond.isPending} onClick={() => respond.mutate(undefined)}>
          {t('cbm.record')}
        </Button>
      </CardContent>
    </Card>
  );
}
