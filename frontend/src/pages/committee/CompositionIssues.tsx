import { useTranslation } from 'react-i18next';
import { AlertTriangle, CheckCircle2, XCircle } from 'lucide-react';
import type { CompositionResult } from '@/api/types';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';

export function CompositionIssues({ result, title }: { result: CompositionResult; title?: string }) {
  const { t } = useTranslation();
  if (result.issues.length === 0) {
    return (
      <Alert variant="success">
        <CheckCircle2 className="h-4 w-4" aria-hidden />
        <AlertDescription>{t('composition.compliant')}</AlertDescription>
      </Alert>
    );
  }
  return (
    <div className="grid gap-2">
      {title && <p className="text-sm font-semibold">{title}</p>}
      {result.issues.map((i, n) => (
        <Alert key={n} variant={i.severity === 'ERROR' ? 'destructive' : 'warning'}>
          {i.severity === 'ERROR' ? <XCircle className="h-4 w-4" aria-hidden /> : <AlertTriangle className="h-4 w-4" aria-hidden />}
          <AlertTitle>{i.severity === 'ERROR' ? t('composition.blocked') : t('composition.warning')}</AlertTitle>
          <AlertDescription>{t(`errors.${i.code}`, { ...stringify(i.params), defaultValue: i.message })}</AlertDescription>
        </Alert>
      ))}
    </div>
  );
}

function stringify(p: Record<string, unknown>): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(p ?? {})) out[k] = typeof v === 'object' ? JSON.stringify(v) : String(v);
  return out;
}
