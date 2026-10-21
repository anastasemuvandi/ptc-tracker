import * as React from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { UserPlus } from 'lucide-react';
import { identity } from '@/api/endpoints';
import type { InstitutionRole, Locale, RequesterCategory, UserRow } from '@/api/types';
import { useMutate } from '@/hooks/useMutate';
import { PageHeader, QueryState, StatusBadge } from '@/components/app/common';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogDescription } from '@/components/ui/dialog';
import { Field } from '@/components/ui/field';
import { Input } from '@/components/ui/input';
import { SimpleSelect } from '@/components/ui/select';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table';
import { daysUntil, formatDate, todayKigali } from '@/lib/time';

const ROLES: InstitutionRole[] = ['INSTITUTION_ADMIN', 'CBM', 'READ_ONLY', 'REQUESTER'];
export const OFFICES = ['HEAD_OF_INSTITUTION', 'CBM', 'HEAD_OF_FINANCE', 'INTERNAL_AUDITOR', 'LEGAL_ADVISOR', 'LOGISTICS_OFFICER', 'LOWEST_JOB_CATEGORY'];

export function UsersPage() {
  const { t } = useTranslation();
  const q = useQuery({ queryKey: ['users'], queryFn: identity.users });
  const [inviteOpen, setInviteOpen] = React.useState(false);
  const [editing, setEditing] = React.useState<UserRow | null>(null);
  return (
    <div>
      <PageHeader title={t('users.title')} subtitle={t('users.subtitle')}
        actions={<Button onClick={() => setInviteOpen(true)}><UserPlus className="h-4 w-4" aria-hidden />{t('users.invite')}</Button>} />
      <QueryState query={q} empty={(d) => d.length === 0}>
        {(list) => (
          <Card>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('common.name')}</TableHead>
                  <TableHead>{t('auth.email')}</TableHead>
                  <TableHead>{t('users.office')}</TableHead>
                  <TableHead>{t('users.roles')}</TableHead>
                  <TableHead>{t('common.status')}</TableHead>
                  <TableHead />
                </TableRow>
              </TableHeader>
              <TableBody>
                {list.map((u) => (
                  <TableRow key={u.id}>
                    <TableCell className="font-medium">{u.fullName} {u.isProcurementOfficer && <Badge variant="secondary">{t('committee.procurementOfficer')}</Badge>}</TableCell>
                    <TableCell>{u.email}</TableCell>
                    <TableCell>{u.officeHolderCode ? t(`users.offices.${u.officeHolderCode}`, { defaultValue: u.officeHolderCode }) : '—'}</TableCell>
                    <TableCell>
                      {u.roles.map((r) => t(`roles.${r}`)).join(', ') || '—'}
                      {u.roles.includes('REQUESTER') && <RequesterAccessBadge user={u} />}
                    </TableCell>
                    <TableCell><StatusBadge ns="users.status" value={u.status} /></TableCell>
                    <TableCell><Button size="sm" variant="outline" onClick={() => setEditing(u)}>{t('common.manage')}</Button></TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Card>
        )}
      </QueryState>
      <InviteDialog open={inviteOpen} onClose={() => setInviteOpen(false)} />
      {editing && <ManageDialog user={editing} onClose={() => setEditing(null)} />}
    </div>
  );
}

/** "Short term until 30 Nov 2026", flagged when access ends soon or has ended. */
function RequesterAccessBadge({ user }: { user: UserRow }) {
  const { t, i18n } = useTranslation();
  if (!user.requesterCategory) return null;
  const left = user.accessUntil ? daysUntil(user.accessUntil) : null;
  return (
    <span className="mt-1 block text-xs">
      <Badge variant={left !== null && left < 0 ? 'destructive' : left !== null && left <= 14 ? 'warning' : 'outline'}>
        {user.requesterCategory === 'SHORT_TERM'
          ? t('users.shortTermUntil', { date: formatDate(user.accessUntil, i18n.language) })
          : t('users.category.LONG_TERM')}
      </Badge>
    </span>
  );
}

/** Category and end of access of a requester; short term needs an end date. */
function RequesterAccessFields({ idPrefix, category, accessUntil, onChange }: {
  idPrefix: string; category: RequesterCategory | ''; accessUntil: string;
  onChange: (v: { category: RequesterCategory | ''; accessUntil: string }) => void;
}) {
  const { t } = useTranslation();
  return (
    <div className="grid gap-3 rounded-md border p-3 sm:grid-cols-2">
      <Field id={`${idPrefix}-cat`} label={t('users.requesterCategory')} required>
        <SimpleSelect id={`${idPrefix}-cat`} value={category} placeholder={t('common.choose')}
          onChange={(v) => onChange({ category: v as RequesterCategory, accessUntil })}
          options={(['SHORT_TERM', 'LONG_TERM'] as const).map((c) => ({ value: c, label: t(`users.category.${c}`) }))} />
      </Field>
      {category === 'SHORT_TERM' && (
        <Field id={`${idPrefix}-until`} label={t('users.accessUntil')} hint={t('users.accessUntilHint')} required>
          <Input id={`${idPrefix}-until`} type="date" min={todayKigali()} value={accessUntil}
            onChange={(e) => onChange({ category, accessUntil: e.target.value })} />
        </Field>
      )}
      <p className="text-xs text-muted-foreground sm:col-span-2">{t('users.requesterHelp')}</p>
    </div>
  );
}

function InviteDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t } = useTranslation();
  const [f, setF] = React.useState({ email: '', fullName: '', jobTitle: '', officeHolderCode: '', procurementOfficer: false, locale: 'en' as Locale, roles: [] as InstitutionRole[] });
  const [access, setAccess] = React.useState<{ category: RequesterCategory | ''; accessUntil: string }>({ category: '', accessUntil: '' });
  const requester = f.roles.includes('REQUESTER');
  const accessIncomplete = requester && (!access.category || (access.category === 'SHORT_TERM' && !access.accessUntil));
  const invite = useMutate(() => identity.invite({
    ...f, jobTitle: f.jobTitle || undefined, officeHolderCode: f.officeHolderCode || undefined,
    ...(requester ? { requesterCategory: access.category as RequesterCategory, accessUntil: access.category === 'SHORT_TERM' ? access.accessUntil : undefined } : {}),
  }), {
    invalidate: [['users']], onSuccess: onClose, success: t('users.invited'),
  });
  return (
    <Dialog open={open} onOpenChange={(o) => !o && onClose()}>
      <DialogContent closeLabel={t('common.close')}>
        <DialogHeader><DialogTitle>{t('users.invite')}</DialogTitle><DialogDescription>{t('users.inviteHelp')}</DialogDescription></DialogHeader>
        <div className="grid gap-3">
          <Field id="iv-name" label={t('common.name')} required><Input id="iv-name" value={f.fullName} onChange={(e) => setF({ ...f, fullName: e.target.value })} /></Field>
          <Field id="iv-email" label={t('auth.email')} required><Input id="iv-email" type="email" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} /></Field>
          <Field id="iv-title" label={t('users.jobTitle')}><Input id="iv-title" value={f.jobTitle} onChange={(e) => setF({ ...f, jobTitle: e.target.value })} /></Field>
          <Field id="iv-office" label={t('users.office')} hint={t('users.officeHint')}>
            <SimpleSelect id="iv-office" value={f.officeHolderCode} onChange={(v) => setF({ ...f, officeHolderCode: v })} allowEmpty emptyLabel={t('users.noOffice')}
              options={OFFICES.map((o) => ({ value: o, label: t(`users.offices.${o}`) }))} />
          </Field>
          <Field id="iv-locale" label={t('settings.language')}>
            <SimpleSelect id="iv-locale" value={f.locale} onChange={(v) => setF({ ...f, locale: v as Locale })}
              options={[{ value: 'en', label: 'English' }, { value: 'rw', label: 'Ikinyarwanda' }, { value: 'fr', label: 'Français' }]} />
          </Field>
          <div className="flex items-center gap-2">
            <Checkbox id="iv-po" checked={f.procurementOfficer} onCheckedChange={(c) => setF({ ...f, procurementOfficer: c === true })} />
            <Label htmlFor="iv-po">{t('committee.procurementOfficer')}</Label>
          </div>
          <fieldset className="grid gap-2">
            <legend className="text-sm font-medium">{t('users.roles')}</legend>
            {ROLES.map((r) => (
              <div key={r} className="flex items-center gap-2">
                <Checkbox id={`iv-${r}`} checked={f.roles.includes(r)} onCheckedChange={(c) => setF({ ...f, roles: c === true ? [...f.roles, r] : f.roles.filter((x) => x !== r) })} />
                <Label htmlFor={`iv-${r}`}>{t(`roles.${r}`)}</Label>
              </div>
            ))}
            <p className="text-xs text-muted-foreground">{t('users.committeeRolesNote')}</p>
          </fieldset>
          {requester && <RequesterAccessFields idPrefix="iv" category={access.category} accessUntil={access.accessUntil} onChange={setAccess} />}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>{t('common.cancel')}</Button>
          <Button disabled={!f.email || !f.fullName || accessIncomplete || invite.isPending} onClick={() => invite.mutate(undefined)}>{t('users.sendInvite')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function ManageDialog({ user, onClose }: { user: UserRow; onClose: () => void }) {
  const { t } = useTranslation();
  const [roles, setRoles] = React.useState<InstitutionRole[]>(user.roles.filter((r) => r !== 'PLATFORM_ADMIN'));
  const [office, setOffice] = React.useState(user.officeHolderCode ?? '');
  const [po, setPo] = React.useState(user.isProcurementOfficer);
  const inv = [['users']];
  const saveRoles = useMutate(() => identity.setRoles(user.id, roles), { invalidate: inv, success: t('common.saved') });
  const saveProfile = useMutate(() => identity.updateUser(user.id, { officeHolderCode: office, procurementOfficer: po }), { invalidate: inv, success: t('common.saved') });
  const setStatus = useMutate((s: string) => identity.setStatus(user.id, s), { invalidate: inv, onSuccess: onClose, success: t('common.saved') });
  const resend = useMutate(() => identity.resendInvitation(user.id), { success: t('users.invitationResent') });
  const resetMfa = useMutate(() => identity.resetMfa(user.id), { success: t('users.mfaReset') });
  const [access, setAccess] = React.useState<{ category: RequesterCategory | ''; accessUntil: string }>(
    { category: user.requesterCategory ?? '', accessUntil: user.accessUntil ?? '' });
  const saveAccess = useMutate(() => identity.setRequesterAccess(user.id, {
    category: access.category as RequesterCategory, accessUntil: access.category === 'SHORT_TERM' ? access.accessUntil : undefined,
  }), { invalidate: inv, success: t('common.saved') });
  return (
    <Dialog open onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-xl" closeLabel={t('common.close')}>
        <DialogHeader><DialogTitle>{user.fullName}</DialogTitle><DialogDescription>{user.email}</DialogDescription></DialogHeader>
        <div className="grid gap-4">
          <fieldset className="grid gap-2">
            <legend className="text-sm font-medium">{t('users.roles')}</legend>
            {ROLES.map((r) => (
              <div key={r} className="flex items-center gap-2">
                <Checkbox id={`mr-${r}`} checked={roles.includes(r)} onCheckedChange={(c) => setRoles(c === true ? [...roles, r] : roles.filter((x) => x !== r))} />
                <Label htmlFor={`mr-${r}`}>{t(`roles.${r}`)}</Label>
              </div>
            ))}
            <Button size="sm" variant="outline" className="justify-self-start" onClick={() => saveRoles.mutate(undefined)}>{t('users.saveRoles')}</Button>
            <p className="text-xs text-muted-foreground">{t('users.rolesRevokeNote')}</p>
          </fieldset>
          {(roles.includes('REQUESTER') || user.requesterCategory) && (
            <div className="grid gap-2">
              <p className="text-sm font-medium">{t('users.requesterAccess')}</p>
              <RequesterAccessFields idPrefix="mg" category={access.category} accessUntil={access.accessUntil} onChange={setAccess} />
              <Button size="sm" variant="outline" className="justify-self-start"
                disabled={!access.category || (access.category === 'SHORT_TERM' && !access.accessUntil) || saveAccess.isPending}
                onClick={() => saveAccess.mutate(undefined)}>{t('users.saveAccess')}</Button>
            </div>
          )}
          <div className="grid gap-2">
            <Field id="mg-office" label={t('users.office')}>
              <SimpleSelect id="mg-office" value={office} onChange={setOffice} allowEmpty emptyLabel={t('users.noOffice')} options={OFFICES.map((o) => ({ value: o, label: t(`users.offices.${o}`) }))} />
            </Field>
            <div className="flex items-center gap-2"><Checkbox id="mg-po" checked={po} onCheckedChange={(c) => setPo(c === true)} /><Label htmlFor="mg-po">{t('committee.procurementOfficer')}</Label></div>
            <Button size="sm" variant="outline" className="justify-self-start" onClick={() => saveProfile.mutate(undefined)}>{t('common.save')}</Button>
          </div>
        </div>
        <DialogFooter className="flex-wrap">
          {user.status === 'INVITED' && <Button variant="outline" onClick={() => resend.mutate(undefined)}>{t('users.resendInvitation')}</Button>}
          {user.status !== 'INVITED' && <Button variant="outline" onClick={() => window.confirm(t('users.resetMfaConfirm')) && resetMfa.mutate(undefined)}>{t('users.resetMfa')}</Button>}
          {user.status === 'ACTIVE'
            ? <Button variant="destructive" onClick={() => window.confirm(t('users.disableConfirm')) && setStatus.mutate('DISABLED')}>{t('users.disable')}</Button>
            : user.status !== 'INVITED' && <Button onClick={() => setStatus.mutate('ACTIVE')}>{t('users.reactivate')}</Button>}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
