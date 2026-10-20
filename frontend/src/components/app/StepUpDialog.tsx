import * as React from 'react';
import { useTranslation } from 'react-i18next';
import { ShieldCheck } from 'lucide-react';
import { setStepUpHandler } from '@/api/client';
import { auth } from '@/api/endpoints';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Field } from '@/components/ui/field';
import { useErrorText } from '@/hooks/useErrorText';

/**
 * Global step-up challenge. When the API answers auth.step_up_required, the
 * client awaits this dialog; on success the original request is retried
 * with the same Idempotency-Key, so the act is recorded exactly once.
 */
export function StepUpDialog() {
  const { t } = useTranslation();
  const errorText = useErrorText();
  const [open, setOpen] = React.useState(false);
  const [usePassword, setUsePassword] = React.useState(false);
  const [act, setAct] = React.useState<string | undefined>();
  const [value, setValue] = React.useState('');
  const [error, setError] = React.useState<string | null>(null);
  const [busy, setBusy] = React.useState(false);
  const resolver = React.useRef<((ok: boolean) => void) | null>(null);

  React.useEffect(() => {
    setStepUpHandler(async (params) => {
      let enrolled = true;
      try {
        enrolled = (await auth.mfaStatus()).enrolled;
      } catch {
        // assume TOTP
      }
      setUsePassword(!enrolled);
      setAct(typeof params.act === 'string' ? params.act : undefined);
      setValue('');
      setError(null);
      setOpen(true);
      return new Promise<boolean>((resolve) => {
        resolver.current = resolve;
      });
    });
    return () => setStepUpHandler(null);
  }, []);

  const finish = (ok: boolean) => {
    setOpen(false);
    resolver.current?.(ok);
    resolver.current = null;
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await auth.stepUp(usePassword ? { password: value } : { code: value.trim() });
      finish(true);
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={(o) => !o && finish(false)}>
      <DialogContent closeLabel={t('common.close')}>
        <form onSubmit={submit} className="grid gap-4">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <ShieldCheck className="h-5 w-5 text-primary" aria-hidden />
              {t('stepUp.title')}
            </DialogTitle>
            <DialogDescription>
              {t('stepUp.explanation')} {act && t(`stepUp.acts.${act}`, { defaultValue: '' })}
            </DialogDescription>
          </DialogHeader>
          <Field id="stepup-value" label={usePassword ? t('auth.password') : t('auth.totpCode')} error={error}>
            <Input
              id="stepup-value"
              autoFocus
              type={usePassword ? 'password' : 'text'}
              inputMode={usePassword ? undefined : 'numeric'}
              autoComplete={usePassword ? 'current-password' : 'one-time-code'}
              pattern={usePassword ? undefined : '\\d{6}'}
              maxLength={usePassword ? 256 : 6}
              value={value}
              onChange={(e) => setValue(e.target.value)}
              required
            />
          </Field>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => finish(false)}>{t('common.cancel')}</Button>
            <Button type="submit" disabled={busy}>{t('stepUp.confirm')}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
