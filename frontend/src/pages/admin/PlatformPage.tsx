import * as React from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Plus } from 'lucide-react';
import type { PlatformMetric } from '@/api/types';
import { platform } from '@/api/endpoints';
import { useMutate } from '@/hooks/useMutate';
import { PageHeader, QueryState } from '@/components/app/common';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { Badge } from '@/components/ui/badge';
import { DataTable, cols } from '@/components/domain/DataTable';

/** Cross-institution operational metrics only — never file content. */
export function PlatformPage() {
  const { t } = useTranslation();
  const q = useQuery({ queryKey: ['platform-metrics'], queryFn: platform.metrics });
  const [open, setOpen] = React.useState(false);
  const [adminsOf, setAdminsOf] = React.useState<PlatformMetric | null>(null);
  const rotate = useMutate(() => platform.rotateKeys(), { success: t('platform.keysRotated') });
  return (
    <div className="grid gap-4">
      <PageHeader title={t('platform.title')} subtitle={t('platform.subtitle')}
        actions={<>
          <Button onClick={() => setOpen(true)}><Plus className="h-4 w-4" aria-hidden />{t('platform.newInstitution')}</Button>
          <Button variant="outline" onClick={() => window.confirm(t('platform.rotateConfirm')) && rotate.mutate(undefined)}>{t('platform.rotateKeys')}</Button>
        </>} />
      <Alert variant="info"><AlertDescription>{t('platform.metricsOnly')}</AlertDescription></Alert>
      <Card>
        <CardHeader><CardTitle>{t('platform.institutions')}</CardTitle></CardHeader>
        <CardContent>
          <QueryState query={q}>
            {(rows) => <DataTable rows={rows as unknown as Record<string, unknown>[]} columns={[
              ...cols('abbreviation', 'name', 'status', 'committees', 'activeUsers', 'openFiles', 'overdueFiles', 'sessionsLast30Days', 'lastActivity'),
              { key: 'admins', render: (r) => <Button size="sm" variant="outline" onClick={() => setAdminsOf(r as unknown as PlatformMetric)}>{t('platform.administrators')}</Button> },
            ]} />}
          </QueryState>
        </CardContent>
      </Card>
      <NewInstitutionDialog open={open} onClose={() => setOpen(false)} />
      {adminsOf && <AdminsDialog institution={adminsOf} onClose={() => setAdminsOf(null)} />}
    </div>
  );
}

function NewInstitutionDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t } = useTranslation();
  const [f, setF] = React.useState({ name: '', abbreviation: '', institutionType: '', supervisingAuthority: '', defaultCurrency: 'RWF', adminEmail: '', adminFullName: '' });
  const create = useMutate(() => platform.createInstitution(f), { invalidate: [['platform-metrics']], onSuccess: onClose, success: t('platform.institutionCreated') });
  const field = (k: keyof typeof f, label: string, required = true) => (
    <Field id={`pi-${k}`} label={label} required={required}>
      <Input id={`pi-${k}`} type={k === 'adminEmail' ? 'email' : 'text'} value={f[k]} onChange={(e) => setF({ ...f, [k]: e.target.value })} />
    </Field>
  );
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent closeLabel={t('common.close')}>
        <DialogHeader><DialogTitle>{t('platform.newInstitution')}</DialogTitle><DialogDescription>{t('platform.newInstitutionHelp')}</DialogDescription></DialogHeader>
        <div className="grid gap-3">
          {field('name', t('common.name'))}
          {field('abbreviation', t('platform.abbreviation'))}
          {field('institutionType', t('institution.type'))}
          {field('supervisingAuthority', t('institution.supervisingAuthority'), false)}
          {field('defaultCurrency', t('institution.defaultCurrency'))}
          {field('adminFullName', t('platform.adminName'))}
          {field('adminEmail', t('platform.adminEmail'))}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={create.isPending || !f.name || !f.abbreviation || !f.institutionType || !f.adminEmail || !f.adminFullName} onClick={() => create.mutate(undefined)}>{t('common.create')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

/** Recovery for an institution whose administrators lost their authenticator: reset an administrator's two-step verification. */
function AdminsDialog({ institution, onClose }: { institution: PlatformMetric; onClose: () => void }) {
  const { t } = useTranslation();
  const key = ['platform-admins', institution.institutionId];
  const q = useQuery({ queryKey: key, queryFn: () => platform.institutionAdmins(institution.institutionId) });
  const reset = useMutate((userId: string) => platform.resetAdminMfa(institution.institutionId, userId),
    { invalidate: [key], success: t('users.mfaReset') });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent closeLabel={t('common.close')}>
        <DialogHeader>
          <DialogTitle>{t('platform.administratorsOf', { name: institution.abbreviation })}</DialogTitle>
          <DialogDescription>{t('platform.administratorsHelp')}</DialogDescription>
        </DialogHeader>
        <QueryState query={q}>
          {(admins) => admins.length === 0 ? <p className="text-sm text-muted-foreground">{t('common.empty')}</p> : (
            <ul className="grid gap-2">
              {admins.map((a) => (
                <li key={a.id} className="flex flex-wrap items-center justify-between gap-2 rounded-md border p-3">
                  <div className="min-w-0">
                    <p className="font-medium">{a.fullName}</p>
                    <p className="break-all text-sm text-muted-foreground">{a.email}</p>
                    <div className="mt-1 flex flex-wrap gap-1">
                      <Badge variant="outline">{t(`users.status.${a.status}`)}</Badge>
                      <Badge variant={a.mfaEnrolled ? 'success' : 'warning'}>{t(a.mfaEnrolled ? 'platform.mfaEnrolled' : 'platform.mfaNotEnrolled')}</Badge>
                    </div>
                  </div>
                  {a.mfaEnrolled && (
                    <Button size="sm" variant="outline" disabled={reset.isPending}
                      onClick={() => window.confirm(t('platform.resetMfaConfirm', { name: a.fullName })) && reset.mutate(a.id)}>
                      {t('users.resetMfa')}
                    </Button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </QueryState>
        <DialogFooter><Button variant="outline" onClick={onClose}>{t('common.close')}</Button></DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
