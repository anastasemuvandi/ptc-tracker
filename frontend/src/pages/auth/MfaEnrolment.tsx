import * as React from 'react';
import { useTranslation } from 'react-i18next';
import QRCode from 'qrcode';
import { auth } from '@/api/endpoints';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Field } from '@/components/ui/field';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { useErrorText } from '@/hooks/useErrorText';
import type { Enrolment } from '@/api/types';

/**
 * TOTP enrolment: QR code generated locally (the secret never leaves the
 * page for a third-party QR service), confirmation code, then the
 * recovery codes shown exactly once.
 */
export function MfaEnrolment({ challenge, onDone }: { challenge?: string; onDone: () => void }) {
  const { t } = useTranslation();
  const errorText = useErrorText();
  const [enrolment, setEnrolment] = React.useState<Enrolment | null>(null);
  const [qr, setQr] = React.useState<string>('');
  const [code, setCode] = React.useState('');
  const [codes, setCodes] = React.useState<string[] | null>(null);
  const [error, setError] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);
  const [saved, setSaved] = React.useState(false);

  React.useEffect(() => {
    auth.startEnrolment(challenge).then(async (e) => {
      setEnrolment(e);
      setQr(await QRCode.toDataURL(e.otpauthUri, { margin: 1, width: 220 }));
    }).catch((e) => setError(errorText(e)));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [challenge]);

  const confirm = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const r = await auth.confirmEnrolment(code.trim(), challenge);
      setCodes(r.recoveryCodes);
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  const downloadCodes = () => {
    const blob = new Blob([`PTC Tracker — ${t('mfa.recoveryCodes')}\n\n${codes!.join('\n')}\n`], { type: 'text/plain' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'ptc-recovery-codes.txt';
    a.click();
    URL.revokeObjectURL(a.href);
    setSaved(true);
  };

  if (codes) {
    return (
      <div className="grid gap-4">
        <Alert variant="warning">
          <AlertTitle>{t('mfa.recoveryCodes')}</AlertTitle>
          <AlertDescription>{t('mfa.recoveryCodesOnce')}</AlertDescription>
        </Alert>
        <ul className="grid grid-cols-2 gap-2 rounded-md bg-muted p-3 font-mono text-sm" aria-label={t('mfa.recoveryCodes')}>
          {codes.map((c) => <li key={c}>{c}</li>)}
        </ul>
        <div className="flex flex-wrap gap-2">
          <Button variant="outline" onClick={downloadCodes}>{t('mfa.download')}</Button>
          <Button variant="outline" onClick={() => { window.print(); setSaved(true); }}>{t('mfa.print')}</Button>
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={saved} onChange={(e) => setSaved(e.target.checked)} />
          {t('mfa.savedConfirm')}
        </label>
        <Button disabled={!saved} onClick={onDone}>{t('common.continue')}</Button>
      </div>
    );
  }

  return (
    <form onSubmit={confirm} className="grid gap-4">
      <p className="text-sm">{t('mfa.enrolIntro')}</p>
      {qr && <img src={qr} alt={t('mfa.qrAlt')} className="mx-auto h-56 w-56 rounded border bg-white p-2" />}
      {enrolment && (
        <div className="text-sm">
          <p className="text-muted-foreground">{t('mfa.manualEntry')}</p>
          <code className="block break-all rounded bg-muted p-2 font-mono text-sm">{enrolment.secret}</code>
        </div>
      )}
      <Field id="enrol-code" label={t('auth.totpCode')} error={error}>
        <Input id="enrol-code" inputMode="numeric" autoComplete="one-time-code" maxLength={6} pattern="\d{6}" value={code} onChange={(e) => setCode(e.target.value)} required />
      </Field>
      <Button type="submit" disabled={busy || !enrolment}>{t('mfa.confirm')}</Button>
    </form>
  );
}
