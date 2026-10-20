import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { WifiOff } from 'lucide-react';
import { decisions } from '@/api/endpoints';
import { newIdempotencyKey } from '@/api/client';
import { useSession } from '@/hooks/useSession';
import { useOnline } from '@/hooks/useOnline';
import { useErrorText } from '@/hooks/useErrorText';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Field } from '@/components/ui/field';
import { SimpleSelect } from '@/components/ui/select';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { toast } from '@/components/ui/toast';
import { formatInstant } from '@/lib/time';

/**
 * Conflict-of-interest step before a file is opened in session. Declaring
 * an interest excludes the member from discussion and vote; the voting
 * threshold is recalculated server-side.
 */
export function DeclarationPanel({ agendaItemId }: { agendaItemId: string }) {
  const { t, i18n } = useTranslation();
  const { me } = useSession();
  const online = useOnline();
  const errorText = useErrorText();
  const qc = useQueryClient();
  const q = useQuery({ queryKey: ['declarations', agendaItemId], queryFn: () => decisions.declarations(agendaItemId) });
  const [hasInterest, setHasInterest] = React.useState<boolean | null>(null);
  const [kind, setKind] = React.useState('DIRECT');
  const [details, setDetails] = React.useState('');
  const [busy, setBusy] = React.useState(false);
  const key = React.useRef(newIdempotencyKey());

  const mine = q.data?.declarations.find((d) => d.userId === me?.id);
  const iAmPending = q.data?.pendingMembers.some((p) => p.userId === me?.id);

  const submit = async () => {
    if (hasInterest === null) return;
    setBusy(true);
    try {
      await decisions.declare(agendaItemId, hasInterest ? { hasInterest, interestKind: kind, details } : { hasInterest },
        { idempotencyKey: key.current });
      toast({ title: t('coi.recorded'), tone: 'success' });
      key.current = newIdempotencyKey();
      void qc.invalidateQueries({ queryKey: ['declarations', agendaItemId] });
      void qc.invalidateQueries({ queryKey: ['decision', agendaItemId] });
    } catch (e) {
      toast({ title: t('common.actionFailed'), description: errorText(e), tone: 'destructive' });
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('coi.title')}</CardTitle>
        <CardDescription>{t('coi.explanation')}</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        {mine ? (
          <Alert variant={mine.hasInterest ? 'warning' : 'success'}>
            <AlertDescription>
              {mine.hasInterest ? t('coi.youDeclaredInterest') : t('coi.youDeclaredNone')} · {formatInstant(mine.declaredAt, i18n.language)}
            </AlertDescription>
          </Alert>
        ) : iAmPending ? (
          <div className="grid gap-3">
            {!online && <Alert variant="destructive"><WifiOff className="h-4 w-4" aria-hidden /><AlertDescription>{t('coi.offlineBlocked')}</AlertDescription></Alert>}
            <div className="grid grid-cols-2 gap-2" role="radiogroup" aria-label={t('coi.question')}>
              <Button size="lg" variant={hasInterest === false ? 'success' : 'outline'} role="radio" aria-checked={hasInterest === false} onClick={() => setHasInterest(false)}>
                {t('coi.noInterest')}
              </Button>
              <Button size="lg" variant={hasInterest === true ? 'destructive' : 'outline'} role="radio" aria-checked={hasInterest === true} onClick={() => setHasInterest(true)}>
                {t('coi.haveInterest')}
              </Button>
            </div>
            {hasInterest && (
              <>
                <Field id="coi-kind" label={t('coi.kind')}>
                  <SimpleSelect id="coi-kind" value={kind} onChange={setKind}
                    options={['DIRECT', 'INDIRECT'].map((k) => ({ value: k, label: t(`coi.kinds.${k}`) }))} />
                </Field>
                <Field id="coi-details" label={t('coi.details')} required>
                  <Textarea id="coi-details" value={details} onChange={(e) => setDetails(e.target.value)} required />
                </Field>
                <Alert variant="warning"><AlertDescription>{t('coi.exclusionNotice')}</AlertDescription></Alert>
              </>
            )}
            <Button size="lg" disabled={hasInterest === null || busy || !online || (hasInterest && !details.trim())} onClick={() => void submit()}>
              {t('coi.submit')}
            </Button>
          </div>
        ) : null}
        {q.data && (
          <div>
            <p className="mb-2 text-sm font-medium">{t('coi.status')}</p>
            <ul className="grid gap-1 text-sm">
              {q.data.declarations.map((d) => (
                <li key={d.userId} className="flex flex-wrap items-center gap-2">
                  <span>{d.fullName}</span>
                  {d.hasInterest
                    ? <Badge variant="destructive">{t('coi.interest', { kind: t(`coi.kinds.${d.interestKind ?? 'DIRECT'}`) })}</Badge>
                    : <Badge variant="success">{t('coi.none')}</Badge>}
                  {d.hasInterest && d.details && <span className="text-muted-foreground">— {d.details}</span>}
                </li>
              ))}
              {q.data.pendingMembers.map((p) => (
                <li key={p.userId} className="flex items-center gap-2"><span>{p.fullName}</span><Badge variant="warning">{t('coi.pending')}</Badge></li>
              ))}
            </ul>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
