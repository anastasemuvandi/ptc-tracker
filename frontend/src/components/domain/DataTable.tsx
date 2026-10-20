import * as React from 'react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { ItemTypeBadge } from '@/components/app/common';
import { Badge } from '@/components/ui/badge';
import { formatDate, formatInstant, formatPct } from '@/lib/time';
import type { ItemType } from '@/api/types';

type Row = Record<string, unknown>;

export interface Column {
  key: string;
  /** Builds an in-app link for the cell (ids stay internal; the text shown is a business reference). */
  link?: (row: Row) => string;
  render?: (row: Row) => React.ReactNode;
}

const DATE_KEYS = /(Date|Expiry|termEnd|termStart|dueDate|Deadline|targetDate|dateReceived)$/;
const INSTANT_KEYS = /(At)$/;
const PCT_KEYS = /(Pct)$/;
const ENUM_KEYS: Record<string, string> = {
  stage: 'stage', status: 'common.statusValue', role: 'committee.role', kind: 'sessions.kind',
  risk: 'dashboard.risk', level: 'amendment.levelShort', myAttendance: 'attendance.status',
};

/** Read-only table for dashboard/report rows. UUID columns are never displayed. */
export function DataTable({ rows, columns, empty }: { rows: Row[] | undefined; columns: Column[]; empty?: string }) {
  const { t, i18n } = useTranslation();
  if (!rows || rows.length === 0) return <p className="py-2 text-sm text-muted-foreground">{empty ?? t('common.empty')}</p>;
  const cell = (row: Row, key: string): React.ReactNode => {
    const v = row[key];
    if (v === null || v === undefined || v === '') return '—';
    if (key === 'itemType') return <ItemTypeBadge type={v as ItemType} />;
    if (typeof v === 'boolean') return v ? t('common.yes') : t('common.no');
    if (key === 'overdue' && v) return <Badge variant="destructive">{t('files.overdue')}</Badge>;
    if (ENUM_KEYS[key]) return t(`${ENUM_KEYS[key]}.${String(v)}`, { defaultValue: String(v) });
    if (PCT_KEYS.test(key)) return formatPct(v as number);
    if (INSTANT_KEYS.test(key)) return formatInstant(String(v), i18n.language);
    if (DATE_KEYS.test(key)) return formatDate(String(v), i18n.language);
    if (Array.isArray(v)) return v.map((x) => t(`errors.${x}`, { defaultValue: String(x) })).join('; ');
    return String(v);
  };
  return (
    <Table>
      <TableHeader>
        <TableRow>{columns.map((c) => <TableHead key={c.key}>{t(`dashboard.cols.${c.key}`, { defaultValue: c.key })}</TableHead>)}</TableRow>
      </TableHeader>
      <TableBody>
        {rows.map((r, i) => (
          <TableRow key={i}>
            {columns.map((c) => (
              <TableCell key={c.key}>
                {c.render ? c.render(r) : c.link ? <Link className="text-primary underline" to={c.link(r)}>{cell(r, c.key)}</Link> : cell(r, c.key)}
              </TableCell>
            ))}
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}

export const cols = (...keys: (string | Column)[]): Column[] => keys.map((k) => (typeof k === 'string' ? { key: k } : k));
