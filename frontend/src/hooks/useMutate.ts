import { useMutation, useQueryClient, type QueryKey } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { toast } from '@/components/ui/toast';
import { useErrorText } from './useErrorText';

/**
 * Mutation that only reports success after the server has confirmed the
 * write, invalidates the given queries and toasts translated errors.
 */
export function useMutate<TVars, TResult>(
  fn: (vars: TVars) => Promise<TResult>,
  opts: { invalidate?: QueryKey[]; success?: string; onSuccess?: (r: TResult, v: TVars) => void } = {},
) {
  const qc = useQueryClient();
  const errorText = useErrorText();
  const { t } = useTranslation();
  return useMutation({
    mutationFn: fn,
    onSuccess: (r, v) => {
      opts.invalidate?.forEach((k) => void qc.invalidateQueries({ queryKey: k }));
      if (opts.success) toast({ title: opts.success, tone: 'success' });
      opts.onSuccess?.(r, v);
    },
    onError: (e) => toast({ title: t('common.actionFailed'), description: errorText(e), tone: 'destructive' }),
  });
}
