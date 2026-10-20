import * as React from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { config } from '@/api/endpoints';
import { useSession } from '@/hooks/useSession';
import { useMutate } from '@/hooks/useMutate';
import { PageHeader, QueryState } from '@/components/app/common';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Field } from '@/components/ui/field';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Sheet, SheetContent, SheetTitle, SheetDescription } from '@/components/ui/sheet';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { formatInstant, isoToKigaliLocal, kigaliLocalToIso } from '@/lib/time';

const GROUPS: { id: string; prefixes: string[] }[] = [
  { id: 'composition', prefixes: ['committee.', 'composition.', 'term.', 'replacement.'] },
  { id: 'sessions', prefixes: ['session.', 'quorum.', 'voting.'] },
  { id: 'evaluation', prefixes: ['evaluation.', 'bidSecurity.', 'bidValidity.'] },
  { id: 'contracts', prefixes: ['amendment.', 'extension.', 'forceMajeure.', 'contract.', 'framework.'] },
  { id: 'workflow', prefixes: ['workflow.', 'recommendation.', 'checklist.'] },
  { id: 'security', prefixes: ['mfa.', 'stepUp.'] },
];

function display(v: unknown): string {
  if (v && typeof v === 'object' && 'numerator' in (v as object)) {
    const f = v as { numerator: number; denominator: number };
    return `${f.numerator}/${f.denominator}`;
  }
  return typeof v === 'string' ? v : JSON.stringify(v);
}

/**
 * Every legal threshold is configuration with an effective date. Changes
 * never edit history: a new rule supersedes the old one from its effective
 * date, so past decisions stay judged against the rules then in force.
 */
export function ConfigPage() {
  const { t, i18n } = useTranslation();
  const { hasRole } = useSession();
  const platform = hasRole('PLATFORM_ADMIN');
  const [at, setAt] = React.useState('');
  const q = useQuery({ queryKey: ['config', at], queryFn: () => config.resolved(at ? kigaliLocalToIso(at) : undefined) });
  const [selected, setSelected] = React.useState<string | null>(null);
  return (
    <div className="grid gap-4">
      <PageHeader title={t('config.title')} subtitle={t('config.subtitle')} />
      <Alert variant="info"><AlertDescription>{t('config.historyNote')}</AlertDescription></Alert>
      <div className="flex flex-wrap items-end gap-3">
        <Field id="cfg-at" label={t('config.viewAt')} hint={t('config.viewAtHint')}>
          <Input id="cfg-at" type="datetime-local" value={at} onChange={(e) => setAt(e.target.value)} />
        </Field>
        {at && <Button variant="ghost" onClick={() => setAt('')}>{t('config.now')}</Button>}
      </div>
      <QueryState query={q}>
        {(snap) => (
          <div className="grid gap-4">
            {GROUPS.map((g) => {
              const keys = Object.keys(snap.values).filter((k) => g.prefixes.some((p) => k.startsWith(p))).sort();
              if (keys.length === 0) return null;
              return (
                <Card key={g.id}>
                  <CardHeader><CardTitle>{t(`config.groups.${g.id}`)}</CardTitle></CardHeader>
                  <CardContent>
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>{t('config.rule')}</TableHead>
                          <TableHead>{t('config.value')}</TableHead>
                          <TableHead>{t('config.scope')}</TableHead>
                          <TableHead>{t('config.effectiveFrom')}</TableHead>
                          <TableHead />
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {keys.map((k) => (
                          <TableRow key={k}>
                            <TableCell>
                              <p className="font-medium">{t(`config.keys.${k}`, { defaultValue: k })}</p>
                              <p className="font-mono text-xs text-muted-foreground">{k}</p>
                            </TableCell>
                            <TableCell className="max-w-xs break-words font-mono text-xs">{display(snap.values[k])}</TableCell>
                            <TableCell><Badge variant={snap.sources[k]?.scope === 'INSTITUTION' ? 'default' : 'outline'}>{t(`config.scopes.${snap.sources[k]?.scope ?? 'PLATFORM'}`)}</Badge></TableCell>
                            <TableCell className="text-sm">{formatInstant(snap.sources[k]?.effectiveFrom, i18n.language)}</TableCell>
                            <TableCell><Button size="sm" variant="outline" onClick={() => setSelected(k)}>{t('config.historyAndChange')}</Button></TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </CardContent>
                </Card>
              );
            })}
            {selected && <RuleSheet ruleKey={selected} current={snap.values[selected]} platform={platform} onClose={() => setSelected(null)} />}
          </div>
        )}
      </QueryState>
    </div>
  );
}

function RuleSheet({ ruleKey, current, platform, onClose }: { ruleKey: string; current: unknown; platform: boolean; onClose: () => void }) {
  const { t, i18n } = useTranslation();
  const history = useQuery({ queryKey: ['config-history', ruleKey], queryFn: () => config.history(ruleKey) });
  const [value, setValue] = React.useState(JSON.stringify(current, null, 2));
  const [effective, setEffective] = React.useState(isoToKigaliLocal(new Date(Date.now() + 5 * 60_000).toISOString()));
  const [reason, setReason] = React.useState('');
  const [legal, setLegal] = React.useState('');
  let parsed: unknown;
  let parseError = false;
  try {
    parsed = JSON.parse(value);
  } catch {
    parseError = true;
  }
  const add = useMutate(() => config.add({
    key: ruleKey, value: parsed, effectiveFrom: kigaliLocalToIso(effective), reason, legalReference: legal || undefined,
    scope: platform ? 'PLATFORM' : 'INSTITUTION',
  }), { invalidate: [['config'], ['config-history', ruleKey]], onSuccess: onClose, success: t('config.ruleAdded') });
  return (
    <Sheet open onOpenChange={(o) => !o && onClose()}>
      <SheetContent side="right" className="w-[36rem]" closeLabel={t('common.close')}>
        <SheetTitle className="pr-6 text-lg font-semibold">{t(`config.keys.${ruleKey}`, { defaultValue: ruleKey })}</SheetTitle>
        <SheetDescription className="font-mono text-xs">{ruleKey}</SheetDescription>
        <section className="grid gap-3">
          <h3 className="font-semibold">{t('config.newRule')}</h3>
          <Field id="rule-value" label={t('config.value')} hint={t('config.valueHint')} error={parseError ? t('config.invalidJson') : undefined}>
            <Textarea id="rule-value" className="font-mono" rows={6} value={value} onChange={(e) => setValue(e.target.value)} />
          </Field>
          <Field id="rule-eff" label={t('config.effectiveFrom')} hint={t('config.noRetroactive')} required>
            <Input id="rule-eff" type="datetime-local" value={effective} onChange={(e) => setEffective(e.target.value)} />
          </Field>
          <Field id="rule-reason" label={t('common.reason')} required><Textarea id="rule-reason" value={reason} onChange={(e) => setReason(e.target.value)} /></Field>
          <Field id="rule-legal" label={t('config.legalReference')} hint={t('config.legalHint')}><Input id="rule-legal" value={legal} onChange={(e) => setLegal(e.target.value)} /></Field>
          <Button disabled={parseError || !reason.trim() || add.isPending} onClick={() => add.mutate(undefined)}>
            {platform ? t('config.addPlatformRule') : t('config.addInstitutionRule')}
          </Button>
        </section>
        <section className="grid gap-2">
          <h3 className="font-semibold">{t('config.history')}</h3>
          <QueryState query={history} empty={(d) => d.length === 0}>
            {(rows) => (
              <ol className="grid gap-2">
                {rows.map((r) => (
                  <li key={r.id} className="rounded-md border p-2 text-sm">
                    <div className="flex flex-wrap items-center gap-2">
                      <Badge variant={r.scope === 'INSTITUTION' ? 'default' : 'outline'}>{t(`config.scopes.${r.scope}`)}</Badge>
                      <span>{t('config.from', { at: formatInstant(r.effectiveFrom, i18n.language) })}</span>
                    </div>
                    <p className="mt-1 break-words font-mono text-xs">{r.ruleValue}</p>
                    {r.reason && <p className="text-muted-foreground">{r.reason}</p>}
                    {r.legalReference && <p className="text-muted-foreground">{t('config.legalReference')}: {r.legalReference}</p>}
                    {r.createdByName && <p className="text-xs text-muted-foreground">{r.createdByName} · {formatInstant(r.createdAt, i18n.language)}</p>}
                  </li>
                ))}
              </ol>
            )}
          </QueryState>
        </section>
      </SheetContent>
    </Sheet>
  );
}
