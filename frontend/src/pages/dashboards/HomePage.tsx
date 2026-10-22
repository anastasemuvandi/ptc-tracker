import { Link, Navigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { CalendarDays, Gavel, ShieldAlert, Vote } from 'lucide-react';
import { dashboards } from '@/api/endpoints';
import { useSession } from '@/hooks/useSession';
import { PageHeader, QueryState } from '@/components/app/common';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { DataTable, cols } from '@/components/domain/DataTable';
import { formatInstant } from '@/lib/time';
import { RequesterDashboard } from '@/pages/requests/RequestPages';

type Row = Record<string, unknown>;

/** Member dashboard: my files, my sessions, my pending declarations and votes, my attendance. */
export function HomePage() {
  const { t, i18n } = useTranslation();
  const { me, hasRole } = useSession();
  // A requester outside the committee sees only their requests' progress.
  const requesterOnly = hasRole('REQUESTER') && !me?.committees.length && !hasRole('CBM', 'INSTITUTION_ADMIN', 'READ_ONLY');
  const q = useQuery({ queryKey: ['dashboard', 'member'], queryFn: dashboards.member, enabled: !hasRole('PLATFORM_ADMIN') && !requesterOnly });
  if (hasRole('PLATFORM_ADMIN')) return <Navigate to="/platform" replace />;
  if (requesterOnly) {
    return (
      <div className="grid gap-4">
        <PageHeader title={t('dashboard.welcome', { name: me?.fullName ?? '' })} subtitle={me?.institution?.name} />
        <RequesterDashboard />
      </div>
    );
  }
  return (
    <div className="grid gap-4">
      <PageHeader title={t('dashboard.welcome', { name: me?.fullName ?? '' })} subtitle={me?.institution?.name} />
      {hasRole('REQUESTER') && <RequesterDashboard />}
      <QueryState query={q}>
        {(d) => {
          const decl = (d.myPendingDeclarations as Row[]) ?? [];
          const votes = (d.myPendingVotes as Row[]) ?? [];
          const sessions = (d.mySessions as Row[]) ?? [];
          return (
            <>
              {(decl.length > 0 || votes.length > 0) && (
                <div className="grid gap-3 sm:grid-cols-2">
                  {decl.map((x) => (
                    <Card key={String(x.agendaItemId)} className="border-2 border-warning">
                      <CardContent className="flex items-center gap-3 pt-5">
                        <ShieldAlert className="h-8 w-8 text-warning" aria-hidden />
                        <div className="flex-1">
                          <p className="font-semibold">{t('dashboard.declarationNeeded')}</p>
                          <p className="text-sm">{String(x.fileNumber)} · {t('sessions.session')} {String(x.sessionNumber)}</p>
                        </div>
                        <Button asChild size="lg"><Link to={`/sessions/${String(x.sessionId)}/items/${String(x.agendaItemId)}`}>{t('dashboard.declare')}</Link></Button>
                      </CardContent>
                    </Card>
                  ))}
                  {votes.map((x) => (
                    <Card key={String(x.decisionId)} className="border-2 border-primary">
                      <CardContent className="flex items-center gap-3 pt-5">
                        <Vote className="h-8 w-8 text-primary" aria-hidden />
                        <div className="flex-1">
                          <p className="font-semibold">{t('dashboard.voteNeeded')}</p>
                          <p className="text-sm">{String(x.fileNumber)} · {t('sessions.session')} {String(x.sessionNumber)}</p>
                        </div>
                        <Button asChild size="lg"><Link to={`/n/vote/${String(x.decisionId)}`}>{t('dashboard.vote')}</Link></Button>
                      </CardContent>
                    </Card>
                  ))}
                </div>
              )}
              <Card>
                <CardHeader><CardTitle className="flex items-center gap-2"><CalendarDays className="h-5 w-5" aria-hidden />{t('dashboard.mySessions')}</CardTitle></CardHeader>
                <CardContent>
                  {sessions.length === 0 ? <p className="text-sm text-muted-foreground">{t('dashboard.noUpcoming')}</p> : (
                    <ul className="grid gap-2">
                      {sessions.map((s) => (
                        <li key={String(s.id)}>
                          <Link to={`/sessions/${String(s.id)}`} className="flex min-h-[48px] flex-wrap items-center gap-x-3 rounded-md border p-3 hover:bg-muted">
                            <span className="font-semibold">{String(s.sessionNumber)}</span>
                            <span className="text-sm text-muted-foreground">{String(s.committeeCode)}</span>
                            <span className="text-sm">{formatInstant(String(s.scheduledAt), i18n.language)}</span>
                            <span className="text-sm">{t('sessions.agendaCount', { count: Number(s.agendaCount ?? 0) })}</span>
                            {s.myApology === true && <span className="text-sm text-muted-foreground">{t('attendance.apologyReceived')}</span>}
                          </Link>
                        </li>
                      ))}
                    </ul>
                  )}
                </CardContent>
              </Card>
              <Card>
                <CardHeader><CardTitle className="flex items-center gap-2"><Gavel className="h-5 w-5" aria-hidden />{t('dashboard.myFiles')}</CardTitle></CardHeader>
                <CardContent>
                  <DataTable rows={d.myFiles as Row[]} columns={cols({ key: 'fileNumber', link: (r: Row) => `/files/${String(r.id)}` }, 'itemType', 'title', 'committeeCode', 'stage', 'targetDate', 'overdue')} />
                </CardContent>
              </Card>
              <div className="grid gap-4 lg:grid-cols-2">
                <Card>
                  <CardHeader><CardTitle>{t('dashboard.myAttendance')}</CardTitle></CardHeader>
                  <CardContent><DataTable rows={d.myAttendance as Row[]} columns={cols('committeeCode', 'sessionsHeld', 'present', 'absentWithPermission', 'onLeave', 'absentUnjustified', 'ratePct')} /></CardContent>
                </Card>
                <Card>
                  <CardHeader><CardTitle>{t('dashboard.myTasks')}</CardTitle></CardHeader>
                  <CardContent><DataTable rows={d.myTasks as Row[]} columns={cols({ key: 'title', link: (r: Row) => `/n/task/${String(r.id)}` }, 'dueDate')} /></CardContent>
                </Card>
              </div>
            </>
          );
        }}
      </QueryState>
    </div>
  );
}
