import * as React from 'react';
import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { ArrowLeft, Download, FileCheck2 } from 'lucide-react';
import { minutes, sessions } from '@/api/endpoints';
import type { Minutes, MinutesView, SessionDetail } from '@/api/types';
import { useSession } from '@/hooks/useSession';
import { useMutate } from '@/hooks/useMutate';
import { useErrorText } from '@/hooks/useErrorText';
import { PageHeader, QueryState, StatusBadge } from '@/components/app/common';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Input } from '@/components/ui/input';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { toast } from '@/components/ui/toast';
import { formatInstant } from '@/lib/time';

export function MinutesPage() {
  const { id = '' } = useParams();
  const s = useQuery({ queryKey: ['session', id], queryFn: () => sessions.get(id) });
  const m = useQuery({ queryKey: ['minutes', id], queryFn: () => minutes.forSession(id) });
  return (
    <QueryState query={s}>
      {(detail) => <QueryState query={m}>{(view) => <MinutesEditor detail={detail} view={view} />}</QueryState>}
    </QueryState>
  );
}

function MinutesEditor({ detail, view }: { detail: SessionDetail; view: MinutesView }) {
  const { t, i18n } = useTranslation();
  const errorText = useErrorText();
  const { me } = useSession();
  const sessionId = detail.session.id;
  const isSecretaryOrChair = detail.myRole === 'SECRETARY' || detail.myRole === 'CHAIRPERSON' || detail.myRole === 'VICE_CHAIRPERSON';
  const m = view.minutes;
  const inv = [['minutes', sessionId]];
  const generate = useMutate(() => minutes.generate(sessionId), { invalidate: inv, success: t('minutes.generated') });
  const [notes, setNotes] = React.useState(m?.secretaryNotes ?? '');
  React.useEffect(() => setNotes(m?.secretaryNotes ?? ''), [m?.secretaryNotes]);
  const save = useMutate(() => minutes.update(m!.id, { secretaryNotes: notes, version: m!.version }), { invalidate: inv, success: t('common.saved') });
  const submit = useMutate(() => minutes.submitForReview(m!.id), { invalidate: inv, success: t('minutes.submitted') });
  const [comment, setComment] = React.useState('');
  const ack = useMutate(() => minutes.acknowledge(m!.id, comment || undefined), { invalidate: inv, success: t('minutes.acknowledged') });
  const finalise = useMutate(() => minutes.finalise(m!.id), { invalidate: inv, success: t('minutes.finalised') });
  const iAcked = view.acknowledgements.some((a) => a.userId === me?.id);
  const iPending = view.canAcknowledge ?? view.pendingAcknowledgements.some((p) => p.userId === me?.id);

  return (
    <div className="grid gap-4">
      <Link to={`/sessions/${sessionId}`} className="flex items-center gap-1 text-sm text-primary"><ArrowLeft className="h-4 w-4" aria-hidden />{t('sessions.session')} {detail.session.sessionNumber}</Link>
      <PageHeader title={t('minutes.titleFor', { number: detail.session.sessionNumber })}
        actions={<>
          {m && <StatusBadge ns="minutes.status" value={m.status} />}
          {m && (
            <Button variant="outline" onClick={() => void minutes.docx(m.id, `minutes-${detail.session.sessionNumber.replace(/[^\w-]+/g, '_')}.docx`).catch((e) => toast({ title: errorText(e), tone: 'destructive' }))}>
              <Download className="h-4 w-4" aria-hidden />{t('minutes.downloadDocx')}
            </Button>
          )}
        </>} />
      {!m && (
        <Card>
          <CardContent className="grid gap-3 pt-5">
            <p className="text-sm text-muted-foreground">{t('minutes.none')}</p>
            {isSecretaryOrChair && <Button className="justify-self-start" onClick={() => generate.mutate(undefined)} disabled={generate.isPending}>{t('minutes.generate')}</Button>}
          </CardContent>
        </Card>
      )}
      {m && (
        <>
          <MinutesPreview minutes={m} />
          {isSecretaryOrChair && m.status === 'DRAFT' && (
            <Card>
              <CardHeader><CardTitle>{t('minutes.drafting')}</CardTitle></CardHeader>
              <CardContent className="grid gap-3">
                <Label htmlFor="sec-notes">{t('minutes.secretaryNotes')}</Label>
                <Textarea id="sec-notes" rows={6} value={notes} onChange={(e) => setNotes(e.target.value)} />
                <div className="flex flex-wrap gap-2">
                  <Button variant="outline" disabled={save.isPending} onClick={() => save.mutate(undefined)}>{t('common.save')}</Button>
                  <Button variant="outline" disabled={generate.isPending} onClick={() => window.confirm(t('minutes.regenerateConfirm')) && generate.mutate(undefined)}>{t('minutes.regenerate')}</Button>
                  <Button disabled={submit.isPending} onClick={() => submit.mutate(undefined)}>{t('minutes.submitForReview')}</Button>
                </div>
              </CardContent>
            </Card>
          )}
          <Card>
            <CardHeader><CardTitle>{t('minutes.acknowledgements')}</CardTitle></CardHeader>
            <CardContent className="grid gap-3">
              <ul className="grid gap-1 text-sm">
                {view.acknowledgements.map((a) => (
                  <li key={a.userId} className="flex flex-wrap items-center gap-2">
                    <FileCheck2 className="h-4 w-4 text-success" aria-hidden />{a.fullName} · {formatInstant(a.acknowledgedAt, i18n.language)}
                    {a.comment && <span className="text-muted-foreground">— {a.comment}</span>}
                  </li>
                ))}
                {view.pendingAcknowledgements.map((p) => (
                  <li key={p.userId} className="flex items-center gap-2">{p.fullName} <Badge variant="warning">{t('minutes.awaiting')}</Badge></li>
                ))}
              </ul>
              {m.status === 'IN_REVIEW' && iPending && !iAcked && (
                <div className="grid gap-2 border-t pt-3">
                  <p className="text-sm">{t('minutes.ackExplain')}</p>
                  <Input aria-label={t('minutes.comment')} placeholder={t('minutes.comment')} value={comment} onChange={(e) => setComment(e.target.value)} />
                  <Button className="justify-self-start" disabled={ack.isPending} onClick={() => ack.mutate(undefined)}>{t('minutes.acknowledge')}</Button>
                </div>
              )}
              {m.status === 'IN_REVIEW' && isSecretaryOrChair && (
                <>
                  {view.pendingAcknowledgements.length > 0 && <Alert variant="warning"><AlertDescription>{t('minutes.finaliseBlocked', { count: view.pendingAcknowledgements.length })}</AlertDescription></Alert>}
                  <Button className="justify-self-start" disabled={finalise.isPending || view.pendingAcknowledgements.length > 0} onClick={() => finalise.mutate(undefined)}>{t('minutes.finalise')}</Button>
                </>
              )}
              {m.status === 'FINAL' && <Alert variant="success"><AlertDescription>{t('minutes.finalAt', { at: formatInstant(m.finalisedAt, i18n.language) })}</AlertDescription></Alert>}
            </CardContent>
          </Card>
        </>
      )}
    </div>
  );
}

/**
 * Renders the generated content generically: the backend's structure adapts
 * to item type (A: stage outcomes + clarifications, C: cumulative value and
 * route, B: checklist). Keys are shown as labelled rows.
 */
function MinutesPreview({ minutes: m }: { minutes: Minutes }) {
  const { t } = useTranslation();
  return (
    <Card>
      <CardHeader><CardTitle>{t('minutes.preview')}</CardTitle></CardHeader>
      <CardContent className="prose max-w-none text-sm">
        <RenderNode value={m.content} depth={0} />
        {m.secretaryNotes && (
          <section className="mt-4">
            <h3 className="font-semibold">{t('minutes.secretaryNotes')}</h3>
            <p className="whitespace-pre-wrap">{m.secretaryNotes}</p>
          </section>
        )}
      </CardContent>
    </Card>
  );
}

function humanise(key: string): string {
  return key.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/_/g, ' ').replace(/^./, (c) => c.toUpperCase());
}

const HIDDEN = /(^id$|Id$|^version$)/;

function RenderNode({ value, depth }: { value: unknown; depth: number }): React.ReactElement | null {
  const { t } = useTranslation();
  if (value === null || value === undefined || value === '') return null;
  if (Array.isArray(value)) {
    return (
      <ul className="ml-4 grid list-disc gap-1">
        {value.map((v, i) => <li key={i}><RenderNode value={v} depth={depth + 1} /></li>)}
      </ul>
    );
  }
  if (typeof value === 'object') {
    const entries = Object.entries(value as Record<string, unknown>).filter(([k]) => !HIDDEN.test(k));
    return (
      <div className={depth > 0 ? 'grid gap-1 border-l-2 pl-3' : 'grid gap-3'}>
        {entries.map(([k, v]) => (
          typeof v === 'object' && v !== null
            ? <section key={k}><h4 className="font-semibold">{t(`minutes.keys.${k}`, { defaultValue: humanise(k) })}</h4><RenderNode value={v} depth={depth + 1} /></section>
            : <p key={k}><span className="text-muted-foreground">{t(`minutes.keys.${k}`, { defaultValue: humanise(k) })}:</span> {String(v)}</p>
        ))}
      </div>
    );
  }
  if (typeof value === 'boolean') return <span>{value ? t('common.yes') : t('common.no')}</span>;
  return <span className="whitespace-pre-wrap">{String(value)}</span>;
}
