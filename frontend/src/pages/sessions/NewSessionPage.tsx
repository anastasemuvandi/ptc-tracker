import * as React from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { sessions } from '@/api/endpoints';
import { ApiError } from '@/api/errors';
import { newIdempotencyKey } from '@/api/client';
import { useSession } from '@/hooks/useSession';
import { useErrorText } from '@/hooks/useErrorText';
import { PageHeader } from '@/components/app/common';
import { Card, CardContent } from '@/components/ui/card';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { SimpleSelect } from '@/components/ui/select';
import { Button } from '@/components/ui/button';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { formatInstant, kigaliLocalToIso } from '@/lib/time';

export function NewSessionPage() {
  const { t, i18n } = useTranslation();
  const errorText = useErrorText();
  const navigate = useNavigate();
  const { me } = useSession();
  const officerOf = (me?.committees ?? []).filter((c) => c.role !== 'MEMBER');
  const [committeeId, setCommitteeId] = React.useState(officerOf[0]?.committeeId ?? '');
  const [kind, setKind] = React.useState('ORDINARY');
  const [when, setWhen] = React.useState('');
  const [location, setLocation] = React.useState('');
  const [shortNotice, setShortNotice] = React.useState<{ earliest?: string } | null>(null);
  const [reason, setReason] = React.useState('');
  const [error, setError] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);
  // One logical "schedule" action: retries reuse the key.
  const key = React.useRef(newIdempotencyKey());

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const s = await sessions.create(
        { committeeId, kind, scheduledAt: kigaliLocalToIso(when), location, shortNoticeReason: shortNotice ? reason : undefined },
        { idempotencyKey: key.current },
      );
      navigate(`/sessions/${s.id}`);
    } catch (err) {
      if (err instanceof ApiError && err.code === 'session.short_notice_reason_required') {
        setShortNotice({ earliest: err.params.earliestWithoutShortNotice as string | undefined });
        key.current = newIdempotencyKey();
      } else {
        setError(errorText(err));
      }
    } finally {
      setBusy(false);
    }
  };

  if (officerOf.length === 0) {
    return <Alert variant="warning"><AlertDescription>{t('sessions.onlyOfficers')}</AlertDescription></Alert>;
  }

  return (
    <div className="max-w-2xl">
      <PageHeader title={t('sessions.schedule')} subtitle={t('sessions.scheduleHelp')} />
      <Card>
        <CardContent className="pt-5">
          <form onSubmit={submit} className="grid gap-4">
            <Field id="committee" label={t('common.committee')} required>
              <SimpleSelect id="committee" value={committeeId} onChange={setCommitteeId}
                options={officerOf.map((c) => ({ value: c.committeeId, label: `${c.code} — ${c.name}` }))} />
            </Field>
            <Field id="kind" label={t('sessions.kindLabel')} required>
              <SimpleSelect id="kind" value={kind} onChange={(v) => { setKind(v); setShortNotice(null); }}
                options={['ORDINARY', 'EXTRAORDINARY'].map((k) => ({ value: k, label: t(`sessions.kind.${k}`) }))} />
            </Field>
            <Field id="when" label={t('sessions.when')} hint={t('time.kigaliNote')} required>
              <Input id="when" type="datetime-local" value={when} onChange={(e) => { setWhen(e.target.value); setShortNotice(null); }} required />
            </Field>
            <Field id="location" label={t('sessions.location')}>
              <Input id="location" value={location} onChange={(e) => setLocation(e.target.value)} />
            </Field>
            {shortNotice && (
              <Alert variant="warning">
                <AlertTitle>{t('sessions.shortNoticeTitle')}</AlertTitle>
                <AlertDescription>
                  {t('sessions.shortNoticeBody', { earliest: shortNotice.earliest ? formatInstant(shortNotice.earliest, i18n.language) : '—' })}
                  <Field id="reason" label={t('sessions.shortNoticeReason')} className="mt-3" required>
                    <Textarea id="reason" value={reason} onChange={(e) => setReason(e.target.value)} required minLength={10} />
                  </Field>
                </AlertDescription>
              </Alert>
            )}
            {error && <p className="text-sm text-destructive" role="alert">{error}</p>}
            <Button type="submit" disabled={busy || !when}>{shortNotice ? t('sessions.scheduleShortNotice') : t('sessions.schedule')}</Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
