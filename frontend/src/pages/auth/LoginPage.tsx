import * as React from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { auth } from '@/api/endpoints';
import { useSession } from '@/hooks/useSession';
import { useErrorText } from '@/hooks/useErrorText';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Field } from '@/components/ui/field';
import { AuthLayout } from './AuthLayout';
import { MfaEnrolment } from './MfaEnrolment';

type Step = { kind: 'password' } | { kind: 'mfa'; challenge: string } | { kind: 'enrol'; challenge: string };

export function LoginPage() {
  const { t } = useTranslation();
  const errorText = useErrorText();
  const { status, signedIn } = useSession();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? '/';
  const [step, setStep] = React.useState<Step>({ kind: 'password' });
  const [email, setEmail] = React.useState('');
  const [password, setPassword] = React.useState('');
  const [code, setCode] = React.useState('');
  const [useRecovery, setUseRecovery] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);

  if (status === 'authenticated') return <Navigate to={from} replace />;

  const finish = async () => {
    await signedIn();
    navigate(from, { replace: true });
  };

  const submitPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const r = await auth.login(email, password);
      if (r.status === 'OK') await finish();
      else if (r.status === 'MFA_REQUIRED') setStep({ kind: 'mfa', challenge: r.challenge! });
      else setStep({ kind: 'enrol', challenge: r.challenge! });
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
      setPassword('');
    }
  };

  const submitCode = async (e: React.FormEvent) => {
    e.preventDefault();
    if (step.kind !== 'mfa') return;
    setBusy(true);
    setError(null);
    try {
      await auth.verifyMfa(step.challenge, useRecovery ? undefined : code.trim(), useRecovery ? code.trim() : undefined);
      await finish();
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  if (step.kind === 'enrol') {
    return (
      <AuthLayout title={t('mfa.enrolTitle')} description={t('mfa.enrolRequired')}>
        <MfaEnrolment challenge={step.challenge} onDone={() => void finish()} />
      </AuthLayout>
    );
  }

  if (step.kind === 'mfa') {
    return (
      <AuthLayout title={t('auth.mfaTitle')} description={useRecovery ? t('auth.recoveryHelp') : t('auth.mfaHelp')}>
        <form onSubmit={submitCode} className="grid gap-4">
          <Field id="code" label={useRecovery ? t('auth.recoveryCode') : t('auth.totpCode')} error={error}>
            <Input
              id="code"
              autoFocus
              inputMode={useRecovery ? 'text' : 'numeric'}
              autoComplete="one-time-code"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              required
            />
          </Field>
          <Button type="submit" disabled={busy}>{t('auth.verify')}</Button>
          <Button type="button" variant="link" onClick={() => { setUseRecovery(!useRecovery); setCode(''); }}>
            {useRecovery ? t('auth.useAuthenticator') : t('auth.useRecoveryCode')}
          </Button>
          <Button type="button" variant="ghost" onClick={() => setStep({ kind: 'password' })}>{t('common.back')}</Button>
        </form>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title={t('auth.signIn')} description={t('auth.signInHelp')}>
      <form onSubmit={submitPassword} className="grid gap-4">
        <Field id="email" label={t('auth.email')}>
          <Input id="email" type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required />
        </Field>
        <Field id="password" label={t('auth.password')} error={error}>
          <Input id="password" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </Field>
        <Button type="submit" disabled={busy}>{t('auth.signIn')}</Button>
        <Link to="/forgot-password" className="text-center text-sm text-primary underline">{t('auth.forgotPassword')}</Link>
        <p className="text-center text-xs text-muted-foreground">{t('auth.inviteOnly')}</p>
      </form>
    </AuthLayout>
  );
}
