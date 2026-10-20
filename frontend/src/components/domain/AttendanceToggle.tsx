import { useTranslation } from 'react-i18next';
import type { AttendanceStatus } from '@/api/types';
import { cn } from '@/lib/utils';

const STATUSES: AttendanceStatus[] = ['PRESENT', 'ABSENT_WITH_PERMISSION', 'ON_LEAVE', 'ABSENT_UNJUSTIFIED'];
const active: Record<AttendanceStatus, string> = {
  PRESENT: 'bg-success text-success-foreground border-success',
  ABSENT_WITH_PERMISSION: 'bg-secondary text-secondary-foreground border-primary',
  ON_LEAVE: 'bg-secondary text-secondary-foreground border-primary',
  ABSENT_UNJUSTIFIED: 'bg-destructive text-destructive-foreground border-destructive',
};

/** Four large touch targets; one tap records the mark. */
export function AttendanceToggle({
  value, onChange, disabled, name,
}: { value: AttendanceStatus | null; onChange: (s: AttendanceStatus) => void; disabled?: boolean; name: string }) {
  const { t } = useTranslation();
  return (
    <div role="radiogroup" aria-label={t('attendance.for', { name })} className="grid grid-cols-2 gap-2 sm:grid-cols-4">
      {STATUSES.map((s) => (
        <button
          key={s}
          type="button"
          role="radio"
          aria-checked={value === s}
          disabled={disabled}
          onClick={() => onChange(s)}
          className={cn(
            'min-h-[48px] rounded-md border-2 px-2 py-2 text-sm font-semibold transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-50',
            value === s ? active[s] : 'border-input bg-background hover:bg-muted',
          )}
        >
          {t(`attendance.status.${s}`)}
        </button>
      ))}
    </div>
  );
}
