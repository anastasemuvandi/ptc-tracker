import * as React from 'react';
import * as DialogPrimitive from '@radix-ui/react-dialog';
import { X } from 'lucide-react';
import { cn } from '@/lib/utils';

export const Sheet = DialogPrimitive.Root;
export const SheetTrigger = DialogPrimitive.Trigger;
export const SheetClose = DialogPrimitive.Close;
export const SheetTitle = DialogPrimitive.Title;
export const SheetDescription = DialogPrimitive.Description;

/** Panel sliding in from an edge (bottom on phones by default). */
export const SheetContent = React.forwardRef<
  React.ElementRef<typeof DialogPrimitive.Content>,
  React.ComponentPropsWithoutRef<typeof DialogPrimitive.Content> & { side?: 'left' | 'bottom' | 'right'; closeLabel?: string }
>(({ className, children, side = 'bottom', closeLabel = 'Close', ...props }, ref) => (
  <DialogPrimitive.Portal>
    <DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-black/40" />
    <DialogPrimitive.Content
      ref={ref}
      className={cn(
        'fixed z-50 flex flex-col gap-3 overflow-y-auto bg-card p-5 shadow-lg',
        side === 'bottom' && 'inset-x-0 bottom-0 max-h-[85vh] rounded-t-xl pb-[max(1.25rem,env(safe-area-inset-bottom))]',
        side === 'left' && 'inset-y-0 left-0 w-72 max-w-[85vw]',
        side === 'right' && 'inset-y-0 right-0 w-96 max-w-[90vw]',
        className,
      )}
      {...props}
    >
      {children}
      <DialogPrimitive.Close className="absolute right-3 top-3 rounded-sm opacity-70 hover:opacity-100 focus:outline-none focus:ring-2 focus:ring-ring">
        <X className="h-5 w-5" aria-hidden />
        <span className="sr-only">{closeLabel}</span>
      </DialogPrimitive.Close>
    </DialogPrimitive.Content>
  </DialogPrimitive.Portal>
));
SheetContent.displayName = 'SheetContent';
