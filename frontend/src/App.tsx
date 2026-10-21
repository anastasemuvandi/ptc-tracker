import * as React from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { SessionProvider, useSession } from '@/hooks/useSession';
import { AppShell } from '@/components/app/AppShell';
import { StepUpDialog } from '@/components/app/StepUpDialog';
import { UpdateBanner } from '@/components/app/UpdateBanner';
import { InstallPrompt } from '@/components/app/InstallPrompt';
import { Toaster } from '@/components/ui/toast';
import { Spinner } from '@/components/ui/spinner';
import { ApiError } from '@/api/errors';

const lazy = <M,>(load: () => Promise<M>, name: keyof M) =>
  React.lazy(async () => ({ default: (await load())[name] as unknown as React.ComponentType }));

const LoginPage = lazy(() => import('@/pages/auth/LoginPage'), 'LoginPage');
const AcceptInvitePage = lazy(() => import('@/pages/auth/TokenPages'), 'AcceptInvitePage');
const ForgotPasswordPage = lazy(() => import('@/pages/auth/TokenPages'), 'ForgotPasswordPage');
const ResetPasswordPage = lazy(() => import('@/pages/auth/TokenPages'), 'ResetPasswordPage');
const GuidePage = lazy(() => import('@/pages/guide/GuidePage'), 'GuidePage');
const HomePage = lazy(() => import('@/pages/dashboards/HomePage'), 'HomePage');
const ChairDashboardPage = lazy(() => import('@/pages/dashboards/ChairDashboardPage'), 'ChairDashboardPage');
const CbmDashboardPage = lazy(() => import('@/pages/dashboards/CbmDashboardPage'), 'CbmDashboardPage');
const ReportsPage = lazy(() => import('@/pages/dashboards/ReportsPage'), 'ReportsPage');
const CommitteesPage = lazy(() => import('@/pages/committee/CommitteesPage'), 'CommitteesPage');
const CommitteeDetailPage = lazy(() => import('@/pages/committee/CommitteeDetailPage'), 'CommitteeDetailPage');
const TasksPage = lazy(() => import('@/pages/committee/TasksPage'), 'TasksPage');
const FilesPage = lazy(() => import('@/pages/files/FilesPage'), 'FilesPage');
const NewFilePage = lazy(() => import('@/pages/files/NewFilePage'), 'NewFilePage');
const FileDetailPage = lazy(() => import('@/pages/files/FileDetailPage'), 'FileDetailPage');
const MyRequestsPage = lazy(() => import('@/pages/requests/RequestPages'), 'MyRequestsPage');
const NewRequestPage = lazy(() => import('@/pages/requests/RequestPages'), 'NewRequestPage');
const RequestInboxPage = lazy(() => import('@/pages/requests/RequestPages'), 'RequestInboxPage');
const RequestPage = lazy(() => import('@/pages/requests/RequestPages'), 'RequestPage');
const ContractsPage = lazy(() => import('@/pages/contracts/ContractsPage'), 'ContractsPage');
const ContractTimelinePage = lazy(() => import('@/pages/contracts/ContractTimelinePage'), 'ContractTimelinePage');
const SessionsPage = lazy(() => import('@/pages/sessions/SessionsPage'), 'SessionsPage');
const NewSessionPage = lazy(() => import('@/pages/sessions/NewSessionPage'), 'NewSessionPage');
const SessionPage = lazy(() => import('@/pages/sessions/SessionPage'), 'SessionPage');
const AgendaItemPage = lazy(() => import('@/pages/sessions/AgendaItemPage'), 'AgendaItemPage');
const MinutesPage = lazy(() => import('@/pages/minutes/MinutesPage'), 'MinutesPage');
const CbmPendingPage = lazy(() => import('@/pages/cbm/CbmPendingPage'), 'CbmPendingPage');
const UsersPage = lazy(() => import('@/pages/admin/UsersPage'), 'UsersPage');
const InstitutionPage = lazy(() => import('@/pages/admin/InstitutionPage'), 'InstitutionPage');
const ConfigPage = lazy(() => import('@/pages/admin/ConfigPage'), 'ConfigPage');
const HolidaysPage = lazy(() => import('@/pages/admin/HolidaysPage'), 'HolidaysPage');
const AuditPage = lazy(() => import('@/pages/admin/AuditPage'), 'AuditPage');
const PlatformPage = lazy(() => import('@/pages/admin/PlatformPage'), 'PlatformPage');
const SettingsPage = lazy(() => import('@/pages/settings/SettingsPage'), 'SettingsPage');
const NotificationsPage = lazy(() => import('@/pages/settings/NotificationsPage'), 'NotificationsPage');
const SyncConflictsPage = lazy(() => import('@/pages/settings/SyncConflictsPage'), 'SyncConflictsPage');
const BidValidityLanding = lazy(() => import('@/pages/notify/Landings'), 'BidValidityLanding');
const CureLanding = lazy(() => import('@/pages/notify/Landings'), 'CureLanding');
const TermLanding = lazy(() => import('@/pages/notify/Landings'), 'TermLanding');
const SessionLanding = lazy(() => import('@/pages/notify/Landings'), 'SessionLanding');
const DeclarationLanding = lazy(() => import('@/pages/notify/Landings'), 'DeclarationLanding');
const VoteLanding = lazy(() => import('@/pages/notify/Landings'), 'VoteLanding');
const MinutesLanding = lazy(() => import('@/pages/notify/Landings'), 'MinutesLanding');
const TaskLanding = lazy(() => import('@/pages/notify/Landings'), 'TaskLanding');
const ContractLanding = lazy(() => import('@/pages/notify/Landings'), 'ContractLanding');
const RecommendationLanding = lazy(() => import('@/pages/notify/Landings'), 'RecommendationLanding');

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      retry: (count, e) => !(e instanceof ApiError && e.status < 500) && count < 2,
      // Keep showing the last data (and the SW's cached copy) when offline.
      networkMode: 'offlineFirst',
    },
    mutations: { networkMode: 'always' },
  },
});

function RequireAuth({ children }: { children: React.ReactNode }) {
  const { status } = useSession();
  const location = useLocation();
  const { t } = useTranslation();
  if (status === 'loading') {
    return (
      <div className="grid min-h-screen place-items-center">
        <Spinner label={t('auth.restoringSession')} />
      </div>
    );
  }
  if (status === 'anonymous') return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  return <>{children}</>;
}

function AppRoutes() {
  const { t } = useTranslation();
  return (
    <React.Suspense fallback={<div className="grid min-h-[50vh] place-items-center"><Spinner label={t('common.loading')} /></div>}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/accept-invite" element={<AcceptInvitePage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/reset-password" element={<ResetPasswordPage />} />
        <Route path="/guide" element={<GuidePage />} />
        <Route element={<RequireAuth><AppShell /></RequireAuth>}>
          <Route index element={<HomePage />} />
          <Route path="dashboard/chair" element={<ChairDashboardPage />} />
          <Route path="dashboard/cbm" element={<CbmDashboardPage />} />
          <Route path="reports" element={<ReportsPage />} />
          <Route path="committees" element={<CommitteesPage />} />
          <Route path="committees/:id" element={<CommitteeDetailPage />} />
          <Route path="tasks" element={<TasksPage />} />
          <Route path="files" element={<FilesPage />} />
          <Route path="files/new" element={<NewFilePage />} />
          <Route path="files/:id" element={<FileDetailPage />} />
          <Route path="requests" element={<MyRequestsPage />} />
          <Route path="requests/new" element={<NewRequestPage />} />
          <Route path="requests/inbox" element={<RequestInboxPage />} />
          <Route path="requests/:id" element={<RequestPage />} />
          <Route path="contracts" element={<ContractsPage />} />
          <Route path="contracts/:id" element={<ContractTimelinePage />} />
          <Route path="sessions" element={<SessionsPage />} />
          <Route path="sessions/new" element={<NewSessionPage />} />
          <Route path="sessions/:id" element={<SessionPage />} />
          <Route path="sessions/:id/items/:itemId" element={<AgendaItemPage />} />
          <Route path="sessions/:id/minutes" element={<MinutesPage />} />
          <Route path="cbm" element={<CbmPendingPage />} />
          <Route path="admin/users" element={<UsersPage />} />
          <Route path="admin/institution" element={<InstitutionPage />} />
          <Route path="admin/config" element={<ConfigPage />} />
          <Route path="admin/holidays" element={<HolidaysPage />} />
          <Route path="audit" element={<AuditPage />} />
          <Route path="platform" element={<PlatformPage />} />
          <Route path="settings" element={<SettingsPage />} />
          <Route path="notifications" element={<NotificationsPage />} />
          <Route path="sync-conflicts" element={<SyncConflictsPage />} />
          <Route path="n/bid-validity/:fileId" element={<BidValidityLanding />} />
          <Route path="n/cure/:cureRequestId" element={<CureLanding />} />
          <Route path="n/term/:membershipId" element={<TermLanding />} />
          <Route path="n/session/:sessionId" element={<SessionLanding />} />
          <Route path="n/declaration/:agendaItemId" element={<DeclarationLanding />} />
          <Route path="n/vote/:decisionId" element={<VoteLanding />} />
          <Route path="n/minutes/:minutesId" element={<MinutesLanding />} />
          <Route path="n/task/:taskId" element={<TaskLanding />} />
          <Route path="n/contract/:contractId" element={<ContractLanding />} />
          <Route path="n/recommendation/:decisionId" element={<RecommendationLanding />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Route>
      </Routes>
    </React.Suspense>
  );
}

export function App() {
  const { t } = useTranslation();
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <SessionProvider>
          <AppRoutes />
          <StepUpDialog />
          <UpdateBanner />
          <InstallPrompt />
          <Toaster closeLabel={t('common.close')} />
        </SessionProvider>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
