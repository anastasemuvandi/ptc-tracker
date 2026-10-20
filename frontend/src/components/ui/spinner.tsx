import { Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils';

export function Spinner({ className, label }: { className?: string; label?: string }) {
  return (
    <span role="status" className={cn('inline-flex items-center gap-2 text-muted-foreground', className)}>
      <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
      {label && <span className="text-sm">{label}</span>}
    </span>
  );
}
