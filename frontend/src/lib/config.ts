import { useQuery } from '@tanstack/react-query';
import { config } from '@/api/endpoints';

/** Resolved configuration for the current institution (cached, SWR in the SW). */
export function useConfig() {
  return useQuery({ queryKey: ['config'], queryFn: () => config.resolved(), staleTime: 5 * 60_000 });
}

export function configList(values: Record<string, unknown> | undefined, key: string): string[] {
  const v = values?.[key];
  return Array.isArray(v) ? v.map(String) : [];
}

