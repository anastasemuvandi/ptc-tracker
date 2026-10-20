import * as React from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { auth } from '@/api/endpoints';
import { useErrorText } from '@/hooks/useErrorText';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Field } from '@/components/ui/field';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { AuthLayout } from './AuthLayout';

function NewPasswordForm({ submitLabel, onSubmit }: { submitLabel: string; onSubmit: (password: string) => Promise<void> }) {
  const { t } = useTranslation();
  const errorText = useErrorText();
  const [password, setPassword] = React.useState('');
  const [confirm, setConfirm] = React.useState('');
  const [error, setError] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);
  return (
    <form
      className="grid gap-4"
      onSubmit={async (e) => {
        e.preventDefault();
        if (password !== confirm) {
          setError(t('auth.passwordsDiffer'));
          return;
        }
        setBusy(true);
        setError(null);
        try {
          await onSubmit(password);
        } catch (err) {
          setError(errorText(err));
        } finally {
          setBusy(false);
        }
      }}
    >
      <Field id="new-password" label={t('auth.newPassword')} hint={t('auth.passwordPolicy')}>
        <Input id="new-password" type="password" autoComplete="new-password" minLength={12} value={password} onChange={(e) => setPassword(e.target.value)} required />
      </Field>
      <Field id="confirm-password" label={t('auth.confirmPassword')} error={error}>
        <Input id="confirm-password" type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
      </Field>
      <Button type="submit" disabled={busy}>{submitLabel}</Button>
    </form>
  );
}

export function AcceptInvitePage() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [done, setDone] = React.useState(false);
  return (
    <AuthLayout title={t('auth.acceptInviteTitle')} description={t('auth.acceptInviteHelp')}>
      {done ? (
        <div className="grid gap-3">
          <Alert variant="success"><AlertDescription>{t('auth.accountReady')}</AlertDescription></Alert>
          <Button asChild><Link to="/login">{t('auth.signIn')}</Link></Button>
        </div>
      ) : (
        <NewPasswordForm submitLabel={t('auth.setPassword')} onSubmit={async (p) => { await auth.acceptInvitation(token, p); setDone(true); }} />
      )}
    </AuthLayout>
  );
}

export function ResetPasswordPage() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const [done, setDone] = React.useState(false);
  return (
    <AuthLayout title={t('auth.resetTitle')}>
      {done ? (
        <div className="grid gap-3">
          <Alert variant="success"><AlertDescription>{t('auth.passwordChanged')}</AlertDescription></Alert>
          <Button asChild><Link to="/login">{t('auth.signIn')}</Link></Button>
        </div>
      ) : (
        <NewPasswordForm submitLabel={t('auth.setPassword')} onSubmit={async (p) => { await auth.resetPassword(token, p); setDone(true); }} />
      )}
    </AuthLayout>
  );
}

export function ForgotPasswordPage() {
  const { t } = useTranslation();
  const [email, setEmail] = React.useState('');
  const [sent, setSent] = React.useState(false);
  return (
    <AuthLayout title={t('auth.forgotPassword')} description={t('auth.forgotHelp')}>
      {sent ? (
        <div className="grid gap-3">
          <Alert variant="info"><AlertDescription>{t('auth.resetSent')}</AlertDescription></Alert>
          <Link to="/login" className="text-center text-sm text-primary underline">{t('auth.backToSignIn')}</Link>
        </div>
      ) : (
        <form
          className="grid gap-4"
          onSubmit={async (e) => {
            e.preventDefault();
            try {
              await auth.forgotPassword(email);
            } finally {
              setSent(true); // same message whether or not the account exists
            }
          }}
        >
          <Field id="forgot-email" label={t('auth.email')}>
            <Input id="forgot-email" type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </Field>
          <Button type="submit">{t('auth.sendResetLink')}</Button>
          <Link to="/login" className="text-center text-sm text-primary underline">{t('auth.backToSignIn')}</Link>
        </form>
      )}
    </AuthLayout>
  );
}
