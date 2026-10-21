import { api, download, qs, type RequestOptions } from './client';
import { CLIENT_HEADER } from './refresh';
import { tokenStore } from './tokenStore';
import { ApiError } from './errors';
import type * as T from './types';

// ------------------------------------------------------------------ auth
// Bootstrap auth calls go through plain fetch: they must not trigger the
// refresh/step-up machinery of the client.
async function authPost<R>(path: string, body: unknown, withClientHeader = false): Promise<R> {
  const res = await fetch(path, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(withClientHeader ? CLIENT_HEADER : {}),
      ...(tokenStore.get() ? { Authorization: `Bearer ${tokenStore.get()}` } : {}),
    },
    body: JSON.stringify(body ?? {}),
  });
  if (!res.ok) {
    let payload: { code?: string; message?: string; params?: Record<string, unknown> } = {};
    try {
      payload = await res.json();
    } catch {
      // empty
    }
    throw new ApiError(res.status, payload.code ?? `http_${res.status}`, payload.message ?? res.statusText, payload.params ?? {});
  }
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as R;
}

function keep(r: { accessToken?: string; expiresIn?: number }) {
  if (r.accessToken && r.expiresIn) tokenStore.set(r.accessToken, r.expiresIn);
}

export const auth = {
  async login(email: string, password: string) {
    const r = await authPost<T.LoginResponse>('/api/auth/login', { email, password });
    keep(r);
    return r;
  },
  async verifyMfa(challenge: string, code?: string, recoveryCode?: string) {
    const r = await authPost<T.TokenResponse>('/api/auth/mfa/verify', { challenge, code, recoveryCode });
    keep(r);
    return r;
  },
  startEnrolment: (challenge?: string) => authPost<T.Enrolment>('/api/auth/mfa/enrol/start', { challenge }),
  async confirmEnrolment(code: string, challenge?: string) {
    const r = await authPost<T.EnrolmentConfirmed>('/api/auth/mfa/enrol/confirm', { challenge, code });
    keep(r);
    return r;
  },
  mfaStatus: () => api.get<T.MfaStatus>('/api/auth/mfa/status'),
  stepUp: (body: { code?: string; password?: string }) => authPost<{ verifiedAt: string }>('/api/auth/step-up', body),
  async logout() {
    try {
      await authPost('/api/auth/logout', {}, true);
    } finally {
      tokenStore.clear();
    }
  },
  acceptInvitation: (token: string, password: string) => authPost('/api/auth/invitations/accept', { token, password }),
  forgotPassword: (email: string) => authPost('/api/auth/password/forgot', { email }),
  resetPassword: (token: string, password: string) => authPost('/api/auth/password/reset', { token, password }),
  changePassword: (currentPassword: string, newPassword: string) =>
    authPost('/api/auth/password/change', { currentPassword, newPassword }),
};

// ------------------------------------------------------------- identity
export const identity = {
  me: () => api.get<T.Me>('/api/me'),
  updateMe: (body: { fullName?: string; phone?: string; locale?: T.Locale }) => api.put<unknown>('/api/me', body),
  users: () => api.get<T.UserRow[]>('/api/users'),
  invite: (body: {
    email: string; fullName: string; jobTitle?: string; officeHolderCode?: string;
    procurementOfficer: boolean; locale: T.Locale; roles: T.InstitutionRole[];
    requesterCategory?: T.RequesterCategory; accessUntil?: string;
  }) => api.post<{ id: T.Uuid }>('/api/users', body),
  setRequesterAccess: (id: T.Uuid, body: { category: T.RequesterCategory; accessUntil?: string }) =>
    api.put<unknown>(`/api/users/${id}/requester-access`, body),
  updateUser: (id: T.Uuid, body: Record<string, unknown>) => api.put<unknown>(`/api/users/${id}`, body),
  setRoles: (id: T.Uuid, roles: T.InstitutionRole[]) => api.put<void>(`/api/users/${id}/roles`, roles),
  setStatus: (id: T.Uuid, status: string) => api.put<void>(`/api/users/${id}/status`, { status }),
  resendInvitation: (id: T.Uuid) => api.post<void>(`/api/users/${id}/resend-invitation`),
  resetMfa: (id: T.Uuid) => api.post<void>(`/api/users/${id}/reset-mfa`),
};

export const institution = {
  get: () => api.get<T.Institution>('/api/institution'),
  update: (body: Partial<T.Institution>) => api.put<T.Institution>('/api/institution', body),
  uploadTemplate: (file: File) => {
    const form = new FormData();
    form.append('file', file);
    return api.post<T.Institution>('/api/institution/minutes-template', form);
  },
  downloadTemplate: () => download('/api/institution/minutes-template', 'minutes-template.docx'),
};

export const config = {
  resolved: (at?: string) => api.get<T.ConfigSnapshot>(`/api/config${qs({ at })}`),
  history: (key: string) => api.get<T.ConfigHistoryRow[]>(`/api/config/${encodeURIComponent(key)}/history`),
  add: (body: { key: string; value: unknown; effectiveFrom?: string; reason: string; legalReference?: string; scope: 'INSTITUTION' | 'PLATFORM' }) =>
    api.post<{ id: T.Uuid }>('/api/config', body),
};

export const holidays = {
  list: (year: number) => api.get<T.Holiday[]>(`/api/holidays${qs({ year })}`),
  add: (body: { date: string; name: string; kind: 'ADD' | 'REMOVE'; scope: 'INSTITUTION' | 'PLATFORM' }) =>
    api.post<{ id: T.Uuid }>('/api/holidays', body),
  remove: (id: T.Uuid) => api.del<void>(`/api/holidays/${id}`),
};

export const audit = {
  list: (params: { entityId?: string; actorId?: string; action?: string; before?: string; limit?: number }) =>
    api.get<T.AuditRow[]>(`/api/audit${qs(params)}`),
};

export const platform = {
  metrics: () => api.get<T.PlatformMetric[]>('/api/platform/metrics'),
  createInstitution: (body: {
    name: string; abbreviation: string; institutionType: string; supervisingAuthority?: string;
    defaultCurrency?: string; adminEmail: string; adminFullName: string;
  }) => api.post<{ id: T.Uuid; adminUserId: T.Uuid }>('/api/platform/institutions', body),
  rotateKeys: () => api.post<{ kid: string }>('/api/platform/keys/rotate'),
};

// ------------------------------------------------------------ committees
export const committees = {
  list: () => api.get<T.CommitteeSummary[]>('/api/committees'),
  create: (body: { name: string; code: string; size: number; regulatorAuthRef?: string; regulatorAuthDate?: string; periodStart?: string; appointmentRef?: string }) =>
    api.post<T.CommitteeSummary>('/api/committees', body),
  get: (id: T.Uuid) => api.get<T.CommitteeDetail>(`/api/committees/${id}`),
  update: (id: T.Uuid, body: Record<string, unknown>) => api.put<T.CommitteeDetail>(`/api/committees/${id}`, body),
  reappoint: (id: T.Uuid, body: { startsOn?: string; appointmentRef?: string }) =>
    api.post<T.CommitteePeriod>(`/api/committees/${id}/periods`, body),
  composition: (id: T.Uuid) => api.get<T.CompositionResult>(`/api/committees/${id}/composition`),
  candidates: (id: T.Uuid) => api.get<T.Candidate[]>(`/api/committees/${id}/candidates`),
  checkAppointment: (id: T.Uuid, body: T.Appointment) =>
    api.post<T.CompositionResult>(`/api/committees/${id}/members/check`, body),
  appoint: (id: T.Uuid, body: T.Appointment, opts?: RequestOptions) =>
    api.post<{ membership: T.CommitteeMember; warnings: T.RuleIssue[] }>(`/api/committees/${id}/members`, body, opts),
  endMembership: (id: T.Uuid, membershipId: T.Uuid, reason: string) =>
    api.post<void>(`/api/committees/${id}/members/${membershipId}/end`, { reason }),
  replace: (id: T.Uuid, body: { scope: 'SINGLE' | 'ALL'; reason: string; outgoingMembershipIds: T.Uuid[]; appointments: T.Appointment[] }) =>
    api.post<{ replacement: T.Replacement; regulatorTask: T.TrackedTask | null }>(`/api/committees/${id}/replacements`, body),
  replacements: (id: T.Uuid) => api.get<T.Replacement[]>(`/api/committees/${id}/replacements`),
  tableRequests: (id: T.Uuid, status?: string) =>
    api.get<T.TableRequest[]>(`/api/committees/${id}/table-requests${qs({ status })}`),
};

export const tasks = {
  list: (status?: string) => api.get<T.TrackedTask[]>(`/api/tasks${qs({ status })}`),
  complete: (id: T.Uuid, body: { reference: string; note?: string }) => api.post<void>(`/api/tasks/${id}/complete`, body),
};

// ----------------------------------------------------------------- files
export interface FileFilters {
  committeeId?: string; type?: string; officerId?: string; stage?: string;
  overdue?: boolean; procurementType?: string; contractId?: string;
}
export const files = {
  list: (f: FileFilters = {}) => api.get<T.FileRow[]>(`/api/files${qs({ ...f, overdue: f.overdue ? true : undefined })}`),
  create: (body: T.NewFile) => api.post<T.FileDetail>('/api/files', body),
  get: async (id: T.Uuid) => normaliseFile(await api.get<RawFileDetail>(`/api/files/${id}`)),
  update: (id: T.Uuid, body: Record<string, unknown>) => api.put<T.FileDetail>(`/api/files/${id}`, body),
  setStage: (id: T.Uuid, toStage: string, note?: string) => api.post<T.FileDetail>(`/api/files/${id}/stage`, { toStage, note }),
  reassign: (id: T.Uuid, toCommitteeId: T.Uuid, reason: string) =>
    api.post<T.FileDetail>(`/api/files/${id}/reassign`, { toCommitteeId, reason }),
  assignOfficer: (id: T.Uuid, officerId: T.Uuid, financialYear: string) =>
    api.post<T.FileDetail>(`/api/files/${id}/officer`, { officerId, financialYear }),
  requestTabling: (id: T.Uuid, reason: string) => api.post<T.TableRequest>(`/api/files/${id}/table-requests`, { reason }),
  decideTableRequest: (id: T.Uuid, body: { accept: boolean; note?: string; sessionId?: T.Uuid }) =>
    api.post<T.TableRequest>(`/api/table-requests/${id}/decide`, body),
  workflow: (type: T.ItemType) => api.get<T.Workflow>(`/api/workflow${qs({ type })}`),
};

/** GET /api/files/{id} nests the entity under "file"; flatten it for the UI. */
interface RawFileDetail {
  file: Omit<T.FileDetail, 'committeeCode' | 'contractRef' | 'officerName' | 'overdue' | 'stageHistory' | 'reassignments' | 'decisions' | 'cbmResponses' | 'allowedStages'>;
  committee: { id: T.Uuid; name: string; code: string };
  contract: T.Contract | null;
  officerName: string | null;
  overdue: boolean;
  allowedStages: string[];
  stageHistory: T.StageHistoryRow[];
  reassignments: { fromCommittee: string; toCommittee: string; reason: string; performedAt: string }[];
  decisions: T.DecisionSummary[];
  cbmResponses: T.CbmResponse[];
}

function normaliseFile(raw: RawFileDetail | T.FileDetail): T.FileDetail {
  if (!('file' in raw)) return raw;
  return {
    ...raw.file,
    committeeCode: raw.committee.code,
    contractRef: raw.contract?.contractRef ?? null,
    officerName: raw.officerName,
    overdue: raw.overdue,
    allowedStages: raw.allowedStages ?? [],
    stageHistory: raw.stageHistory ?? [],
    reassignments: (raw.reassignments ?? []).map((r) => ({ ...r, fromCommitteeCode: r.fromCommittee, toCommitteeCode: r.toCommittee })),
    decisions: raw.decisions ?? [],
    cbmResponses: raw.cbmResponses ?? [],
  };
}

// ------------------------------------------------------------- contracts
export const contracts = {
  list: () => api.get<T.Contract[]>('/api/contracts'),
  create: (body: Partial<T.Contract>) => api.post<T.Contract>('/api/contracts', body),
  get: (id: T.Uuid) => api.get<T.Contract>(`/api/contracts/${id}`),
  update: (id: T.Uuid, body: Partial<T.Contract>) => api.put<T.Contract>(`/api/contracts/${id}`, body),
  timeline: (id: T.Uuid) => api.get<T.ContractTimeline>(`/api/contracts/${id}/timeline`),
};

// -------------------------------------------------------------- sessions
export const sessions = {
  list: (p: { committeeId?: string; from?: string; to?: string } = {}) => api.get<T.SessionRow[]>(`/api/sessions${qs(p)}`),
  create: (body: { committeeId: T.Uuid; kind: string; scheduledAt: string; location?: string; shortNoticeReason?: string }, opts?: RequestOptions) =>
    api.post<T.SessionRow>('/api/sessions', body, opts),
  get: (id: T.Uuid) => api.get<T.SessionDetail>(`/api/sessions/${id}`),
  update: (id: T.Uuid, body: Record<string, unknown>) => api.put<T.SessionRow>(`/api/sessions/${id}`, body),
  setStatus: (id: T.Uuid, status: string) => api.post<T.SessionRow>(`/api/sessions/${id}/status`, { status }),
  addAgendaItem: (id: T.Uuid, body: { fileId?: T.Uuid; itemType: T.ItemType; adminKind?: string; adminNote?: string; activities?: string[] }) =>
    api.post<T.AgendaItem>(`/api/sessions/${id}/agenda`, body),
  activityOptions: (id: T.Uuid, fileId: T.Uuid) =>
    api.get<{ allowed: string[]; outstanding: string[] }>(`/api/sessions/${id}/activity-options${qs({ fileId })}`),
  recordActivity: (id: T.Uuid, activityId: T.Uuid, body: { status: 'COMPLETED' | 'NOT_COMPLETED'; note?: string }) =>
    api.post<T.AgendaActivity>(`/api/sessions/${id}/activities/${activityId}`, body),
  carryOverCandidates: (id: T.Uuid) => api.get<T.CarryOverCandidate[]>(`/api/sessions/${id}/carry-over`),
  carryOver: (id: T.Uuid, activityIds: T.Uuid[]) => api.post<T.AgendaActivity[]>(`/api/sessions/${id}/carry-over`, { activityIds }),
  removeAgendaItem: (id: T.Uuid, itemId: T.Uuid) => api.del<void>(`/api/sessions/${id}/agenda/${itemId}`),
  reorderAgenda: (id: T.Uuid, itemIds: T.Uuid[]) => api.put<void>(`/api/sessions/${id}/agenda/order`, itemIds),
};

// ------------------------------------------------- decisions, votes, COI
export const decisions = {
  declarations: (agendaItemId: T.Uuid) => api.get<T.DeclarationState>(`/api/agenda-items/${agendaItemId}/declarations`),
  declare: (agendaItemId: T.Uuid, body: { hasInterest: boolean; interestKind?: string; details?: string }, opts?: RequestOptions) =>
    api.post<T.Declaration>(`/api/agenda-items/${agendaItemId}/declarations`, body, opts),
  exportDeclarations: (sessionId: T.Uuid) =>
    download(`/api/declarations/export${qs({ sessionId })}`, 'declarations.csv'),
  forItem: (agendaItemId: T.Uuid) => api.get<T.DecisionState>(`/api/agenda-items/${agendaItemId}/decision`),
  draft: (agendaItemId: T.Uuid, body: T.DecisionDraft) => api.post<T.Decision>(`/api/agenda-items/${agendaItemId}/decision`, body),
  update: (id: T.Uuid, body: T.DecisionDraft & { version: number }) => api.put<T.Decision>(`/api/decisions/${id}`, body),
  openVoting: (id: T.Uuid) => api.post<T.Decision>(`/api/decisions/${id}/open-voting`),
  vote: (id: T.Uuid, body: { choice: T.VoteChoice; dissentReason?: string }, opts?: RequestOptions) =>
    api.post<T.VoteRow>(`/api/decisions/${id}/votes`, body, opts),
  closeVoting: (id: T.Uuid) => api.post<T.Decision>(`/api/decisions/${id}/close-voting`),
  issue: (id: T.Uuid, opts?: RequestOptions) => api.post<T.Decision>(`/api/decisions/${id}/issue`, undefined, opts),
  get: (id: T.Uuid) => api.get<T.Decision & { sessionId: T.Uuid }>(`/api/decisions/${id}`),
  submitToCbm: (id: T.Uuid) => api.post<T.Decision>(`/api/decisions/${id}/submit-to-cbm`),
  pendingForCbm: () => api.get<T.PendingRecommendation[]>('/api/cbm/pending'),
  cbmRespond: (id: T.Uuid, body: { response: T.CbmResponse['response']; reasons?: string }) =>
    api.post<T.CbmResponse>(`/api/decisions/${id}/cbm-response`, body),
};

// ------------------------------------------------------------ evaluation
export const evaluation = {
  get: (fileId: T.Uuid) => api.get<T.EvaluationView>(`/api/files/${fileId}/evaluation`),
  lockCriteria: (fileId: T.Uuid, defectFindings?: string) =>
    api.post<T.EvaluationView>(`/api/files/${fileId}/evaluation/criteria-lock`, { confirmed: true, defectFindings }),
  saveFindings: (fileId: T.Uuid, defectFindings: string) =>
    api.put<T.EvaluationView>(`/api/files/${fileId}/evaluation/findings`, { defectFindings }),
  startStage: (fileId: T.Uuid, code: string) => api.post<T.EvaluationStage>(`/api/files/${fileId}/evaluation/stages/${code}/start`),
  saveStage: (fileId: T.Uuid, code: string, body: { data: Record<string, unknown>; outcome?: string; version?: number }) =>
    api.put<T.EvaluationStage>(`/api/files/${fileId}/evaluation/stages/${code}`, body),
  completeStage: (fileId: T.Uuid, code: string, outcome: string) =>
    api.post<T.EvaluationStage>(`/api/files/${fileId}/evaluation/stages/${code}/complete`, { outcome }),
  saveBidder: (fileId: T.Uuid, code: string, body: Partial<T.BidderResult>) =>
    api.put<T.BidderResult>(`/api/files/${fileId}/evaluation/stages/${code}/bidders`, body),
  addAbnormal: (fileId: T.Uuid, body: Partial<T.AbnormalPriceCheck>) =>
    api.post<T.AbnormalPriceCheck>(`/api/files/${fileId}/abnormal-prices`, body),
  updateAbnormal: (id: T.Uuid, body: Partial<T.AbnormalPriceCheck>) => api.put<T.AbnormalPriceCheck>(`/api/abnormal-prices/${id}`, body),
  addCure: (fileId: T.Uuid, body: { bidderRef: string; outstandingItems: T.CureItem[]; issuedOn: string }) =>
    api.post<T.CureRequest>(`/api/files/${fileId}/cure-requests`, body),
  updateCure: (id: T.Uuid, body: Partial<T.CureRequest>) => api.put<T.CureRequest>(`/api/cure-requests/${id}`, body),
  addDueDiligence: (fileId: T.Uuid, body: Partial<T.DueDiligence>) => api.post<T.DueDiligence>(`/api/files/${fileId}/due-diligence`, body),
  updateDueDiligence: (id: T.Uuid, body: Partial<T.DueDiligence>) => api.put<T.DueDiligence>(`/api/due-diligence/${id}`, body),
  addClarification: (fileId: T.Uuid, body: Partial<T.Clarification>) =>
    api.post<T.Clarification>(`/api/files/${fileId}/clarifications`, body),
  updateClarification: (id: T.Uuid, body: Partial<T.Clarification>) => api.put<T.Clarification>(`/api/clarifications/${id}`, body),
  raiseRejectionOfAll: (fileId: T.Uuid, ground: string, reason: string) =>
    api.post<T.FileDetail>(`/api/files/${fileId}/evaluation/raise-rejection-of-all`, { ground, reason }),
  checklist: (fileId: T.Uuid) => api.get<T.ChecklistView>(`/api/files/${fileId}/checklist`),
  answer: (fileId: T.Uuid, itemCode: string, answer: string, note?: string, version?: number | null) =>
    api.put<T.ChecklistView>(`/api/files/${fileId}/checklist/${encodeURIComponent(itemCode)}`, { answer, note, version }),
};

// ------------------------------------------------------- contract changes
export const contractChanges = {
  amendment: (fileId: T.Uuid) => api.get<T.AmendmentView>(`/api/files/${fileId}/amendment`),
  createAmendment: (fileId: T.Uuid, body: Partial<T.AmendmentRequest>) => api.post<T.AmendmentView>(`/api/files/${fileId}/amendment`, body),
  updateAmendment: (fileId: T.Uuid, body: Partial<T.AmendmentRequest>) => api.put<T.AmendmentView>(`/api/files/${fileId}/amendment`, body),
  freezeAmendment: (fileId: T.Uuid) => api.post<T.AmendmentView>(`/api/files/${fileId}/amendment/freeze`),
  extension: (fileId: T.Uuid) => api.get<T.ExtensionView>(`/api/files/${fileId}/extension`),
  createExtension: (fileId: T.Uuid, body: Partial<T.ExtensionRequest>) => api.post<T.ExtensionView>(`/api/files/${fileId}/extension`, body),
  updateExtension: (fileId: T.Uuid, body: Partial<T.ExtensionRequest>) => api.put<T.ExtensionView>(`/api/files/${fileId}/extension`, body),
  costClaim: (fileId: T.Uuid, body: { requestedValue: string; description: string }) =>
    api.post<{ fileId: T.Uuid; fileNumber: string }>(`/api/files/${fileId}/extension/cost-claim`, body),
};

// --------------------------------------------------------------- minutes
export const minutes = {
  forSession: (sessionId: T.Uuid) => api.get<T.MinutesView>(`/api/sessions/${sessionId}/minutes`),
  generate: (sessionId: T.Uuid) => api.post<T.Minutes>(`/api/sessions/${sessionId}/minutes/generate`),
  update: (id: T.Uuid, body: { secretaryNotes: string; version: number }) => api.put<T.Minutes>(`/api/minutes/${id}`, body),
  submitForReview: (id: T.Uuid) => api.post<T.Minutes>(`/api/minutes/${id}/submit-for-review`),
  acknowledge: (id: T.Uuid, comment?: string) => api.post<void>(`/api/minutes/${id}/acknowledge`, { comment }),
  finalise: (id: T.Uuid) => api.post<T.Minutes>(`/api/minutes/${id}/finalise`),
  docx: (id: T.Uuid, name: string) => download(`/api/minutes/${id}/docx`, name),
};

// ---------------------------------------------- notifications, dashboards
export const notifications = {
  list: (unread?: boolean) => api.get<T.NotificationRow[]>(`/api/notifications${qs({ unread: unread ? true : undefined })}`),
  markRead: (id: T.Uuid) => api.post<void>(`/api/notifications/${id}/read`),
  markAllRead: () => api.post<void>('/api/notifications/read-all'),
  vapidKey: () => api.get<{ publicKey: string } | string>('/api/push/vapid-public-key'),
  subscribe: (body: { endpoint: string; keys: { p256dh: string; auth: string } }) => api.post<void>('/api/push/subscriptions', body),
  unsubscribe: (endpoint: string) => api.del<void>(`/api/push/subscriptions${qs({ endpoint })}`),
};

export const dashboards = {
  member: () => api.get<T.Dashboard>('/api/dashboard/member'),
  chair: (committeeId: T.Uuid) => api.get<T.Dashboard>(`/api/dashboard/chair${qs({ committeeId })}`),
  cbm: () => api.get<T.Dashboard>('/api/dashboard/cbm'),
  report: (p: { from: string; to: string; committeeId?: string }) => api.get<T.Dashboard>(`/api/reports/summary${qs(p)}`),
  reportCsv: (p: { from: string; to: string; committeeId?: string }) =>
    download(`/api/reports/summary.csv${qs(p)}`, `ptc-report-${p.from}-${p.to}.csv`),
};

// ------------------------------------------------------------- requests
export const requests = {
  mine: () => api.get<T.RequestRow[]>('/api/requests/mine'),
  inbox: (status?: string) => api.get<T.RequestRow[]>(`/api/requests/inbox${qs({ status })}`),
  get: (id: T.Uuid) => api.get<T.RequestDetail>(`/api/requests/${id}`),
  create: (body: T.RequestInput) => api.post<{ id: T.Uuid }>('/api/requests', body),
  update: (id: T.Uuid, body: T.RequestInput) => api.put<unknown>(`/api/requests/${id}`, body),
  submit: (id: T.Uuid) => api.post<unknown>(`/api/requests/${id}/submit`, { declarationConfirmed: true }),
  withdraw: (id: T.Uuid) => api.post<unknown>(`/api/requests/${id}/withdraw`),
  upload: (id: T.Uuid, file: File) => {
    const form = new FormData();
    form.append('file', file);
    return api.post<T.RequestDocumentRow>(`/api/requests/${id}/documents`, form);
  },
  deleteDocument: (id: T.Uuid, documentId: T.Uuid) => api.del<void>(`/api/requests/${id}/documents/${documentId}`),
  downloadDocument: (id: T.Uuid, doc: T.RequestDocumentRow) =>
    download(`/api/requests/${id}/documents/${doc.id}`, doc.fileName),
  accept: (id: T.Uuid, body: { committeeId: T.Uuid; contractId?: T.Uuid; procurementOfficerId?: T.Uuid; targetDate?: string; title?: string }) =>
    api.post<{ fileId: T.Uuid }>(`/api/requests/${id}/accept`, body),
  returnToRequester: (id: T.Uuid, reason: string) => api.post<unknown>(`/api/requests/${id}/return`, { reason }),
  forFile: (fileId: T.Uuid) => api.get<T.FileRequestSummary | null>(`/api/files/${fileId}/request`),
};
