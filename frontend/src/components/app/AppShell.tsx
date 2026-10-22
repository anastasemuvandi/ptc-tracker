import * as React from 'react';
import { NavLink, Outlet, Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import {
  Bell, Building2, CalendarDays, ClipboardList, FileStack, Gauge, LayoutDashboard, LogOut, Menu, ScrollText,
  Settings, ShieldCheck, SlidersHorizontal, Users, Landmark, BarChart3, ListChecks, FileSignature, BookOpen, Inbox, Send } from 'lucide-react';
import { useSession } from '@/hooks/useSession';
import { notifications } from '@/api/endpoints';
import { Sheet, SheetContent, SheetTitle } from '@/components/ui/sheet';
import { Button } from '@/components/ui/button';
import { LANGUAGES, setLanguage } from '@/i18n';
import { ConnectivityBar } from './ConnectivityBar';
import { cn } from '@/lib/utils';

interface NavItem {
  to: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
}

function useNav(): NavItem[] {
  const { t } = useTranslation();
  const { me, hasRole, isOfficerAnywhere, isAdminOnly } = useSession();
  const items: NavItem[] = [];
  if (!me) return items;
  if (hasRole('PLATFORM_ADMIN')) {
    items.push({ to: '/platform', label: t('nav.platform'), icon: Landmark });
    items.push({ to: '/admin/config', label: t('nav.config'), icon: SlidersHorizontal });
    items.push({ to: '/admin/holidays', label: t('nav.holidays'), icon: CalendarDays });
    items.push({ to: '/guide', label: t('nav.guide'), icon: BookOpen });
    return items;
  }
  // The institution administrator's own pages are Users, Institution,
  // Rules & thresholds and the Audit log; other roles the same person holds add theirs.
  if (!isAdminOnly) items.push({ to: '/', label: t('nav.home'), icon: LayoutDashboard });
  const inCommittee = me.committees.length > 0;
  const readsAll = hasRole('CBM', 'READ_ONLY');
  if (inCommittee || readsAll) {
    items.push({ to: '/sessions', label: t('nav.sessions'), icon: CalendarDays });
    items.push({ to: '/files', label: t('nav.files'), icon: FileStack });
    items.push({ to: '/contracts', label: t('nav.contracts'), icon: FileSignature });
    items.push({ to: '/committees', label: t('nav.committees'), icon: Users });
  }
  if (isOfficerAnywhere || readsAll) items.push({ to: '/requests/inbox', label: t('nav.requestInbox'), icon: Inbox });
  if (hasRole('REQUESTER')) items.push({ to: '/requests', label: t('nav.myRequests'), icon: Send });
  if (hasRole('CBM')) {
    items.push({ to: '/cbm', label: t('nav.cbm'), icon: ClipboardList });
    items.push({ to: '/dashboard/cbm', label: t('nav.cbmDashboard'), icon: Gauge });
  }
  if (isOfficerAnywhere) {
    items.push({ to: '/dashboard/chair', label: t('nav.chairDashboard'), icon: Gauge });
  }
  if (hasRole('CBM') || isOfficerAnywhere) {
    items.push({ to: '/tasks', label: t('nav.tasks'), icon: ListChecks });
  }
  if (readsAll || isOfficerAnywhere) items.push({ to: '/reports', label: t('nav.reports'), icon: BarChart3 });
  if (hasRole('INSTITUTION_ADMIN')) {
    items.push({ to: '/admin/users', label: t('nav.users'), icon: Users });
    items.push({ to: '/admin/institution', label: t('nav.institution'), icon: Building2 });
    items.push({ to: '/admin/config', label: t('nav.config'), icon: SlidersHorizontal });
  }
  if (hasRole('READ_ONLY', 'INSTITUTION_ADMIN', 'CBM')) items.push({ to: '/audit', label: t('nav.audit'), icon: ScrollText });
  items.push({ to: '/guide', label: t('nav.guide'), icon: BookOpen });
  return items;
}

function NavList({ items, onNavigate }: { items: NavItem[]; onNavigate?: () => void }) {
  return (
    <nav className="flex flex-col gap-1">
      {items.map(({ to, label, icon: Icon }) => (
        <NavLink
          key={to}
          to={to}
          end={to === '/'}
          onClick={onNavigate}
          className={({ isActive }) =>
            cn('flex items-center gap-3 rounded-md px-3 py-2.5 text-sm font-medium hover:bg-secondary',
              isActive && 'bg-secondary text-primary')
          }
        >
          <Icon className="h-4 w-4" aria-hidden />
          {label}
        </NavLink>
      ))}
    </nav>
  );
}

export function AppShell() {
  const { t, i18n } = useTranslation();
  const { me, logout } = useSession();
  const items = useNav();
  const [open, setOpen] = React.useState(false);
  const unread = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: () => notifications.list(true),
    refetchInterval: 60_000,
    enabled: !!me?.institutionId,
  });
  const unreadCount = unread.data?.length ?? 0;

  return (
    <div className="flex min-h-screen flex-col">
      <a href="#main" className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded focus:bg-card focus:p-2">
        {t('nav.skipToContent')}
      </a>
      <header className="sticky top-0 z-30 border-b bg-primary text-primary-foreground pt-[env(safe-area-inset-top)]">
        <div className="mx-auto flex h-14 max-w-7xl items-center gap-2 px-3">
          <Button variant="ghost" size="icon" className="text-primary-foreground hover:bg-white/10 lg:hidden" onClick={() => setOpen(true)} aria-label={t('nav.menu')}>
            <Menu className="h-5 w-5" aria-hidden />
          </Button>
          <Link to="/" className="flex items-center gap-2 font-bold">
            <ShieldCheck className="h-5 w-5" aria-hidden />
            <span>PTC Tracker</span>
          </Link>
          <span className="hidden truncate text-sm opacity-80 sm:inline">{me?.institution?.abbreviation}</span>
          <div className="ml-auto flex items-center gap-1">
            <label className="sr-only" htmlFor="lang">{t('settings.language')}</label>
            <select
              id="lang"
              value={i18n.language}
              onChange={(e) => setLanguage(e.target.value)}
              className="rounded bg-white/10 px-2 py-1 text-sm text-primary-foreground [&>option]:text-foreground"
            >
              {LANGUAGES.map((l) => <option key={l.code} value={l.code}>{l.code.toUpperCase()}</option>)}
            </select>
            {me?.institutionId && (
              <Button asChild variant="ghost" size="icon" className="relative text-primary-foreground hover:bg-white/10">
                <Link to="/notifications" aria-label={t('nav.notificationsCount', { count: unreadCount })}>
                  <Bell className="h-5 w-5" aria-hidden />
                  {unreadCount > 0 && (
                    <span className="absolute right-1 top-1 rounded-full bg-warning px-1.5 text-[10px] font-bold text-warning-foreground">{unreadCount}</span>
                  )}
                </Link>
              </Button>
            )}
            <Button asChild variant="ghost" size="icon" className="text-primary-foreground hover:bg-white/10">
              <Link to="/settings" aria-label={t('nav.settings')}><Settings className="h-5 w-5" aria-hidden /></Link>
            </Button>
            <Button variant="ghost" size="icon" className="text-primary-foreground hover:bg-white/10" onClick={() => void logout()} aria-label={t('auth.signOut')}>
              <LogOut className="h-5 w-5" aria-hidden />
            </Button>
          </div>
        </div>
      </header>
      <ConnectivityBar />
      <div className="mx-auto flex w-full max-w-7xl flex-1">
        <aside className="hidden w-60 shrink-0 border-r p-3 lg:block">
          <NavList items={items} />
        </aside>
        <Sheet open={open} onOpenChange={setOpen}>
          <SheetContent side="left" closeLabel={t('common.close')}>
            <SheetTitle className="mb-2 font-bold">{me?.fullName}</SheetTitle>
            <NavList items={items} onNavigate={() => setOpen(false)} />
          </SheetContent>
        </Sheet>
        <main id="main" className="min-w-0 flex-1 p-3 pb-24 sm:p-5">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
