import * as React from 'react';
import { identity, auth } from '@/api/endpoints';
import { refreshAccessToken } from '@/api/refresh';
import { tokenStore } from '@/api/tokenStore';
import { setUnauthorizedHandler } from '@/api/client';
import { setLanguage } from '@/i18n';
import type { CommitteeRole, InstitutionRole, Me } from '@/api/types';

type Status = 'loading' | 'anonymous' | 'authenticated';
interface SessionValue {
  status: Status;
  me: Me | null;
  reload: () => Promise<void>;
  signedIn: () => Promise<void>;
  logout: () => Promise<void>;
  hasRole: (...roles: InstitutionRole[]) => boolean;
  committeeRole: (committeeId: string) => CommitteeRole | null;
  isOfficerAnywhere: boolean;
  /** Institution administrator holding no other role or committee seat: sees only the administration pages. */
  isAdminOnly: boolean;
}

const SessionContext = React.createContext<SessionValue | null>(null);

export function SessionProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = React.useState<Status>('loading');
  const [me, setMe] = React.useState<Me | null>(null);

  const loadMe = React.useCallback(async () => {
    const m = await identity.me();
    setMe(m);
    if (m.locale) setLanguage(m.locale);
    setStatus('authenticated');
  }, []);

  // Cold start (including relaunch of the installed app): try the refresh
  // cookie silently before showing the sign-in screen.
  React.useEffect(() => {
    let cancelled = false;
    (async () => {
      const ok = await refreshAccessToken();
      if (cancelled) return;
      if (ok) {
        try {
          await loadMe();
          return;
        } catch {
          // fall through
        }
      }
      setStatus('anonymous');
    })();
    return () => {
      cancelled = true;
    };
  }, [loadMe]);

  React.useEffect(() => {
    setUnauthorizedHandler(() => {
      setMe(null);
      setStatus('anonymous');
    });
    const unsub = tokenStore.subscribe((authed) => {
      if (!authed) {
        setMe(null);
        setStatus('anonymous');
      }
    });
    return () => {
      setUnauthorizedHandler(null);
      unsub();
    };
  }, []);

  const value = React.useMemo<SessionValue>(() => ({
    status,
    me,
    reload: loadMe,
    signedIn: loadMe,
    logout: async () => {
      await auth.logout();
      // Shared devices: drop cached working data on sign-out.
      navigator.serviceWorker?.controller?.postMessage({ type: 'CLEAR_DATA_CACHES' });
      setMe(null);
      setStatus('anonymous');
    },
    hasRole: (...roles) => !!me && roles.some((r) => me.roles.includes(r)),
    committeeRole: (id) => me?.committees.find((c) => c.committeeId === id)?.role ?? null,
    isOfficerAnywhere: !!me?.committees.some((c) => c.role !== 'MEMBER'),
    isAdminOnly: !!me && me.roles.length > 0 && me.roles.every((r) => r === 'INSTITUTION_ADMIN') && me.committees.length === 0,
  }), [status, me, loadMe]);

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionValue {
  const v = React.useContext(SessionContext);
  if (!v) throw new Error('useSession outside SessionProvider');
  return v;
}
