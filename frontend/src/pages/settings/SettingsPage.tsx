import * as React from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { auth, identity } from '@/api/endpoints';
import type { Locale } from '@/api/types';
import { useSession } from '@/hooks/useSession';
import { useMutate } from '@/hooks/useMutate';
import { useErrorText } from '@/hooks/useErrorText';
import { LANGUAGES, setLanguage } from '@/i18n';
import { enablePush, disablePush, currentSubscription, pushSupported } from '@/lib/push';
import { PageHeader } from '@/components/app/common';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { SimpleSelect } from '@/components/ui/select';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { toast } from '@/components/ui/toast';
import { MfaEnrolment } from '@/pages/auth/MfaEnrolment';

export function SettingsPage() {
  const { t } = useTranslation();
  return (
    <div className="grid max-w-2xl gap-4">
      <PageHeader title={t('settings.title')} />
      <ProfileCard />
      <NotificationsCard />
      <SecurityCard />
    </div>
  );
}

function ProfileCard() {
  const { t } = useTranslation();
  const { me, reload } = useSession();
  const [fullName, setFullName] = React.useState(me?.fullName ?? '');
  const [phone, setPhone] = React.useState('');
  const [locale, setLocale] = React.useState<Locale>(me?.locale ?? 'en');
  const save = useMutate(() => identity.updateMe({ fullName, phone: phone || undefined, locale }), {
    success: t('common.saved'), onSuccess: () => { setLanguage(locale); void reload(); },
  });
  return (
    <Card>
      <CardHeader><CardTitle>{t('settings.profile')}</CardTitle><CardDescription>{me?.email}</CardDescription></CardHeader>
      <CardContent className="grid gap-3">
        <Field id="st-name" label={t('common.name')}><Input id="st-name" value={fullName} onChange={(e) => setFullName(e.target.value)} /></Field>
        <Field id="st-phone" label={t('settings.phone')}><Input id="st-phone" type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} /></Field>
        <Field id="st-lang" label={t('settings.language')}>
          <SimpleSelect id="st-lang" value={locale} onChange={(v) => setLocale(v as Locale)} options={LANGUAGES.map((l) => ({ value: l.code, label: l.label }))} />
        </Field>
        <Button className="justify-self-start" disabled={save.isPending} onClick={() => save.mutate(undefined)}>{t('common.save')}</Button>
      </CardContent>
    </Card>
  );
}

function NotificationsCard() {
  const { t } = useTranslation();
  const errorText = useErrorText();
  const [subscribed, setSubscribed] = React.useState<boolean | null>(null);
  const [busy, setBusy] = React.useState(false);
  React.useEffect(() => { void currentSubscription().then((s) => setSubscribed(!!s)); }, []);
  const ios = /iphone|ipad|ipod/i.test(navigator.userAgent);
  const standalone = window.matchMedia?.('(display-mode: standalone)').matches;
  return (
    <Card>
      <CardHeader><CardTitle>{t('settings.notifications')}</CardTitle><CardDescription>{t('settings.emailGuaranteed')}</CardDescription></CardHeader>
      <CardContent className="grid gap-3">
        {!pushSupported() ? (
          <Alert variant="info"><AlertDescription>{ios && !standalone ? t('settings.pushIosInstall') : t('settings.pushUnsupported')}</AlertDescription></Alert>
        ) : (
          <div className="flex flex-wrap items-center gap-3">
            <Badge variant={subscribed ? 'success' : 'outline'}>{subscribed ? t('settings.pushOn') : t('settings.pushOff')}</Badge>
            <Button variant={subscribed ? 'outline' : 'default'} disabled={busy || subscribed === null} onClick={async () => {
              setBusy(true);
              try {
                if (subscribed) {
                  await disablePush();
                  setSubscribed(false);
                } else {
                  const ok = await enablePush();
                  setSubscribed(ok);
                  if (!ok) toast({ title: t('settings.pushDenied'), tone: 'warning' });
                }
              } catch (e) {
                toast({ title: t('common.actionFailed'), description: errorText(e), tone: 'destructive' });
              } finally {
                setBusy(false);
              }
            }}>{subscribed ? t('settings.disablePush') : t('settings.enablePush')}</Button>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function SecurityCard() {
  const { t } = useTranslation();
  const errorText = useErrorText();
  const mfa = useQuery({ queryKey: ['mfa-status'], queryFn: auth.mfaStatus });
  const [enrolling, setEnrolling] = React.useState(false);
  const [current, setCurrent] = React.useState('');
  const [next, setNext] = React.useState('');
  const [busy, setBusy] = React.useState(false);
  return (
    <Card>
      <CardHeader><CardTitle>{t('settings.security')}</CardTitle></CardHeader>
      <CardContent className="grid gap-5">
        <section className="grid gap-2">
          <h3 className="font-semibold">{t('settings.twoFactor')}</h3>
          {mfa.data && (
            <p className="text-sm">
              {mfa.data.enrolled ? t('settings.mfaEnrolled', { count: mfa.data.recoveryCodesRemaining }) : mfa.data.required ? t('settings.mfaRequired') : t('settings.mfaOptional')}
            </p>
          )}
          {mfa.data && !mfa.data.enrolled && !enrolling && <Button className="justify-self-start" onClick={() => setEnrolling(true)}>{t('settings.setUpMfa')}</Button>}
          {enrolling && <MfaEnrolment onDone={() => { setEnrolling(false); void mfa.refetch(); }} />}
        </section>
        <form className="grid gap-3" onSubmit={async (e) => {
          e.preventDefault();
          setBusy(true);
          try {
            await auth.changePassword(current, next);
            toast({ title: t('auth.passwordChanged'), tone: 'success' });
            setCurrent('');
            setNext('');
          } catch (err) {
            toast({ title: t('common.actionFailed'), description: errorText(err), tone: 'destructive' });
          } finally {
            setBusy(false);
          }
        }}>
          <h3 className="font-semibold">{t('settings.changePassword')}</h3>
          <Field id="pw-current" label={t('settings.currentPassword')}><Input id="pw-current" type="password" autoComplete="current-password" value={current} onChange={(e) => setCurrent(e.target.value)} required /></Field>
          <Field id="pw-new" label={t('auth.newPassword')} hint={t('auth.passwordPolicy')}><Input id="pw-new" type="password" autoComplete="new-password" minLength={12} value={next} onChange={(e) => setNext(e.target.value)} required /></Field>
          <Button type="submit" className="justify-self-start" disabled={busy}>{t('settings.changePassword')}</Button>
        </form>
      </CardContent>
    </Card>
  );
}
