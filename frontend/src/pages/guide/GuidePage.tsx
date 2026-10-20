import * as React from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Markdown, { type Components } from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { ArrowLeft, BookOpen } from 'lucide-react';
import { useSession } from '@/hooks/useSession';
import guide from './user-guide.en.md?raw';

/** Heading text → anchor id, so the contents list can link to sections. */
function slug(text: string): string {
  return text.toLowerCase().normalize('NFKD').replace(/[^\w\s-]/g, '').trim().replace(/\s+/g, '-');
}

function textOf(node: React.ReactNode): string {
  if (typeof node === 'string' || typeof node === 'number') return String(node);
  if (Array.isArray(node)) return node.map(textOf).join('');
  if (React.isValidElement<{ children?: React.ReactNode }>(node)) return textOf(node.props.children);
  return '';
}

const SECTIONS = [...guide.matchAll(/^## (.+)$/gm)].map((m) => m[1]);

const components: Components = {
  h2: ({ children }) => (
    <h2 id={slug(textOf(children))} className="mt-12 scroll-mt-20 border-b pb-2 text-2xl font-semibold first:mt-0">{children}</h2>
  ),
  h3: ({ children }) => <h3 id={slug(textOf(children))} className="mt-8 scroll-mt-20 text-lg font-semibold">{children}</h3>,
  p: ({ children }) => <p className="mt-3 leading-7">{children}</p>,
  ul: ({ children }) => <ul className="mt-3 list-disc space-y-1.5 pl-6 leading-7">{children}</ul>,
  ol: ({ children }) => <ol className="mt-3 list-decimal space-y-1.5 pl-6 leading-7">{children}</ol>,
  strong: ({ children }) => <strong className="font-semibold">{children}</strong>,
  table: ({ children }) => (
    <div className="mt-4 overflow-x-auto rounded-md border">
      <table className="w-full min-w-[32rem] text-left text-sm">{children}</table>
    </div>
  ),
  thead: ({ children }) => <thead className="bg-muted/60">{children}</thead>,
  th: ({ children }) => <th className="px-3 py-2 font-semibold">{children}</th>,
  td: ({ children }) => <td className="border-t px-3 py-2 align-top">{children}</td>,
  img: ({ src, alt }) => (
    <figure className="mt-5">
      <img src={src} alt={alt ?? ''} loading="lazy"
        className="mx-auto max-h-[80vh] rounded-md border bg-white object-contain shadow-sm" />
      {alt && <figcaption className="mt-2 text-center text-sm text-muted-foreground">{alt}</figcaption>}
    </figure>
  ),
  a: ({ href, children }) => <a href={href} className="text-primary underline underline-offset-4">{children}</a>,
};

/**
 * The user guide, bundled with the app so it opens without leaving it and
 * without signing in (it is linked from the sign-in screens). The text is in
 * English; screenshots are served from /guide/.
 */
export function GuidePage() {
  const { t } = useTranslation();
  const { status } = useSession();
  const back = status === 'authenticated' ? '/' : '/login';

  React.useEffect(() => {
    document.title = `${t('auth.userGuide')} — PTC Tracker`;
    return () => { document.title = 'PTC Tracker'; };
  }, [t]);

  return (
    <div className="min-h-screen bg-background">
      <header className="sticky top-0 z-10 border-b bg-primary text-primary-foreground">
        <div className="mx-auto flex max-w-6xl items-center gap-3 px-4 py-3">
          <BookOpen className="h-5 w-5" aria-hidden />
          <span className="font-semibold">PTC Tracker — {t('auth.userGuide')}</span>
          <Link to={back} className="ml-auto inline-flex items-center gap-1.5 text-sm underline-offset-4 hover:underline">
            <ArrowLeft className="h-4 w-4" aria-hidden />
            {status === 'authenticated' ? t('guide.backToApp') : t('auth.backToSignIn')}
          </Link>
        </div>
      </header>
      <div className="mx-auto grid max-w-6xl gap-8 px-4 py-6 lg:grid-cols-[14rem_1fr]">
        <nav aria-label={t('guide.contents')} className="lg:sticky lg:top-20 lg:self-start">
          <p className="text-sm font-semibold text-muted-foreground">{t('guide.contents')}</p>
          <ol className="mt-2 grid gap-1 text-sm">
            {SECTIONS.map((s) => (
              <li key={s}><a href={`#${slug(s)}`} className="text-primary hover:underline">{s}</a></li>
            ))}
          </ol>
        </nav>
        <main className="min-w-0 max-w-3xl pb-16">
          <Markdown remarkPlugins={[remarkGfm]} components={components}>{guide}</Markdown>
        </main>
      </div>
    </div>
  );
}
