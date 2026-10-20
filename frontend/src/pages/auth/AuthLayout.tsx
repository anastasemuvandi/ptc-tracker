import * as React from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { BookOpen, ShieldCheck } from 'lucide-react';
import { LANGUAGES, setLanguage } from '@/i18n';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';

export function AuthLayout({ title, description, children }: { title: string; description?: string; children: React.ReactNode }) {
  const { t, i18n } = useTranslation();
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-primary/5 p-4">
      <div className="mb-6 flex items-center gap-2 text-primary">
        <ShieldCheck className="h-8 w-8" aria-hidden />
        <span className="text-2xl font-bold">PTC Tracker</span>
      </div>
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle>{title}</CardTitle>
          {description && <CardDescription>{description}</CardDescription>}
        </CardHeader>
        <CardContent>{children}</CardContent>
      </Card>
      <Link to="/guide"
        className="mt-4 inline-flex items-center gap-1.5 text-sm font-medium text-primary underline-offset-4 hover:underline">
        <BookOpen className="h-4 w-4" aria-hidden />{t('auth.userGuide')}
      </Link>
      <div className="mt-4 flex gap-3 text-sm">
        {LANGUAGES.map((l) => (
          <button key={l.code} onClick={() => setLanguage(l.code)} className={i18n.language === l.code ? 'font-bold underline' : 'text-muted-foreground'}>
            {l.label}
          </button>
        ))}
      </div>
      <p className="mt-6 max-w-md text-center text-xs text-muted-foreground">{t('app.scopeNotice')}</p>
    </div>
  );
}
