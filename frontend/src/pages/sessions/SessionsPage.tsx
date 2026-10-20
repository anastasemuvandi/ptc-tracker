import * as React from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Plus } from 'lucide-react';
import { sessions } from '@/api/endpoints';
import { useSession } from '@/hooks/useSession';
import { PageHeader, QueryState, StatusBadge } from '@/components/app/common';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { SimpleSelect } from '@/components/ui/select';
import { formatInstant } from '@/lib/time';

export function SessionsPage() {
  const { t, i18n } = useTranslation();
  const { me, isOfficerAnywhere } = useSession();
  const [committeeId, setCommitteeId] = React.useState('');
  const q = useQuery({ queryKey: ['sessions', committeeId], queryFn: () => sessions.list({ committeeId: committeeId || undefined }) });
  const committees = me?.committees ?? [];
  return (
    <div>
      <PageHeader
        title={t('sessions.title')}
        actions={isOfficerAnywhere && (
          <Button asChild><Link to="/sessions/new"><Plus className="h-4 w-4" aria-hidden />{t('sessions.schedule')}</Link></Button>
        )}
      />
      {committees.length > 1 && (
        <div className="mb-3 max-w-xs">
          <SimpleSelect
            value={committeeId}
            onChange={setCommitteeId}
            allowEmpty
            emptyLabel={t('common.allCommittees')}
            options={committees.map((c) => ({ value: c.committeeId, label: `${c.code} — ${c.name}` }))}
          />
        </div>
      )}
      <QueryState query={q} empty={(d) => d.length === 0}>
        {(list) => (
          <ul className="grid gap-2">
            {[...list].sort((a, b) => b.scheduledAt.localeCompare(a.scheduledAt)).map((s) => (
              <li key={s.id}>
                <Card className="hover:border-primary">
                  <Link to={`/sessions/${s.id}`} className="flex flex-wrap items-center gap-x-4 gap-y-1 p-4">
                    <span className="font-semibold">{s.sessionNumber}</span>
                    <span className="text-sm text-muted-foreground">{s.committeeCode}</span>
                    <span className="text-sm">{formatInstant(s.scheduledAt, i18n.language)}</span>
                    <span className="text-sm text-muted-foreground">{t(`sessions.kind.${s.kind}`)}</span>
                    {s.agendaCount !== undefined && <span className="text-sm">{t('sessions.agendaCount', { count: s.agendaCount })}</span>}
                    <span className="ml-auto"><StatusBadge ns="sessions.status" value={s.status} /></span>
                  </Link>
                </Card>
              </li>
            ))}
          </ul>
        )}
      </QueryState>
    </div>
  );
}
