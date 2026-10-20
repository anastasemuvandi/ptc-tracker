/**
 * Display helpers. Instants are stored and transmitted in UTC and always
 * shown in Africa/Kigali; calendar dates (YYYY-MM-DD) are shown as-is
 * without any time-zone shift.
 */
export const KIGALI = 'Africa/Kigali';

const localeTag = (lng: string) => (lng === 'rw' ? 'rw-RW' : lng === 'fr' ? 'fr-RW' : 'en-GB');

export function formatInstant(iso: string | null | undefined, lng = 'en'): string {
  if (!iso) return '—';
  return new Intl.DateTimeFormat(localeTag(lng), {
    timeZone: KIGALI, year: 'numeric', month: 'short', day: '2-digit', hour: '2-digit', minute: '2-digit',
  }).format(new Date(iso));
}

export function formatDate(date: string | null | undefined, lng = 'en'): string {
  if (!date) return '—';
  const [y, m, d] = date.split('-').map(Number);
  // Noon UTC keeps the calendar day stable whatever zone formats it.
  return new Intl.DateTimeFormat(localeTag(lng), { timeZone: 'UTC', year: 'numeric', month: 'short', day: '2-digit' })
    .format(new Date(Date.UTC(y, m - 1, d, 12)));
}

/** Today's calendar date in Kigali as YYYY-MM-DD. */
export function todayKigali(now = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', { timeZone: KIGALI, year: 'numeric', month: '2-digit', day: '2-digit' }).format(now);
}

/** Whole calendar days from today (Kigali) to the date; negative if past. */
export function daysUntil(date: string, now = new Date()): number {
  const [y, m, d] = date.split('-').map(Number);
  const [ty, tm, td] = todayKigali(now).split('-').map(Number);
  return Math.round((Date.UTC(y, m - 1, d) - Date.UTC(ty, tm - 1, td)) / 86_400_000);
}

/** <input type="datetime-local"> value (Kigali wall time) → UTC ISO. Kigali is UTC+2 with no DST. */
export function kigaliLocalToIso(local: string): string {
  return new Date(`${local}:00+02:00`).toISOString();
}

export function isoToKigaliLocal(iso: string): string {
  const d = new Date(new Date(iso).getTime() + 2 * 3600_000);
  return d.toISOString().slice(0, 16);
}

export function formatMoney(amount: string | number | null | undefined, currency: string | null | undefined, lng = 'en'): string {
  if (amount === null || amount === undefined || amount === '') return '—';
  const n = typeof amount === 'number' ? amount : Number(amount);
  const formatted = new Intl.NumberFormat(localeTag(lng), { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(n);
  return currency ? `${formatted} ${currency}` : formatted;
}

export function formatPct(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') return '—';
  return `${Number(value).toFixed(2)} %`;
}
