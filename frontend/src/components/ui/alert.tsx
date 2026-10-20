import * as React from 'react';
import { cva, type VariantProps } from 'class-variance-authority';
import { cn } from '@/lib/utils';

const alertVariants = cva('relative w-full rounded-lg border p-4 text-sm [&>svg]:absolute [&>svg]:left-4 [&>svg]:top-4 [&>svg~*]:pl-7', {
  variants: {
    variant: {
      default: 'bg-card text-foreground',
      info: 'border-primary/40 bg-primary/5 text-foreground',
      warning: 'border-warning bg-warning/15 text-foreground',
      destructive: 'border-destructive bg-destructive/10 text-foreground',
      success: 'border-success bg-success/10 text-foreground',
    },
  },
  defaultVariants: { variant: 'default' },
});

export function Alert({ className, variant, ...props }: React.HTMLAttributes<HTMLDivElement> & VariantProps<typeof alertVariants>) {
  return <div role="alert" className={cn(alertVariants({ variant }), className)} {...props} />;
}
export const AlertTitle = ({ className, ...p }: React.HTMLAttributes<HTMLHeadingElement>) => (
  <h5 className={cn('mb-1 font-semibold leading-none', className)} {...p} />
);
export const AlertDescription = ({ className, ...p }: React.HTMLAttributes<HTMLDivElement>) => (
  <div className={cn('text-sm [&_p]:leading-relaxed', className)} {...p} />
);
