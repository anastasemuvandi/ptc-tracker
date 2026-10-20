import * as React from 'react';
import * as ToastPrimitive from '@radix-ui/react-toast';
import { X } from 'lucide-react';
import { cn } from '@/lib/utils';

type ToastTone = 'default' | 'success' | 'destructive' | 'warning';
interface ToastItem {
  id: number;
  title: string;
  description?: string;
  tone: ToastTone;
}

let nextId = 1;
let push: ((t: ToastItem) => void) | null = null;

/** Imperative toast: toast({ title, description, tone }). */
export function toast(t: { title: string; description?: string; tone?: ToastTone }): void {
  push?.({ id: nextId++, tone: 'default', ...t });
}

const toneClass: Record<ToastTone, string> = {
  default: 'border bg-card',
  success: 'border-success bg-card border-l-4',
  destructive: 'border-destructive bg-card border-l-4',
  warning: 'border-warning bg-card border-l-4',
};

export function Toaster({ closeLabel = 'Close' }: { closeLabel?: string }) {
  const [items, setItems] = React.useState<ToastItem[]>([]);
  React.useEffect(() => {
    push = (t) => setItems((s) => [...s, t]);
    return () => {
      push = null;
    };
  }, []);
  return (
    <ToastPrimitive.Provider swipeDirection="right" duration={6000}>
      {items.map((t) => (
        <ToastPrimitive.Root
          key={t.id}
          className={cn('relative grid gap-1 rounded-md p-4 pr-8 shadow-lg', toneClass[t.tone])}
          onOpenChange={(open) => !open && setItems((s) => s.filter((x) => x.id !== t.id))}
        >
          <ToastPrimitive.Title className="text-sm font-semibold">{t.title}</ToastPrimitive.Title>
          {t.description && <ToastPrimitive.Description className="text-sm text-muted-foreground">{t.description}</ToastPrimitive.Description>}
          <ToastPrimitive.Close className="absolute right-2 top-2 opacity-70 hover:opacity-100" aria-label={closeLabel}>
            <X className="h-4 w-4" aria-hidden />
          </ToastPrimitive.Close>
        </ToastPrimitive.Root>
      ))}
      <ToastPrimitive.Viewport className="fixed bottom-0 right-0 z-[100] flex max-h-screen w-full flex-col gap-2 p-4 sm:max-w-sm" />
    </ToastPrimitive.Provider>
  );
}
