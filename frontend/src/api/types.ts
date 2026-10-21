/**
 * Types mirroring docs/API.md. UUID-typed fields are opaque strings: never
 * parse, slice, sort by or display them.
 */
export type Uuid = string;
export type IsoDate = string; // YYYY-MM-DD (Africa/Kigali calendar date)
export type IsoInstant = string; // ISO-8601 UTC
export type Decimal = string | number;

export type InstitutionRole = 'PLATFORM_ADMIN' | 'INSTITUTION_ADMIN' | 'CBM' | 'READ_ONLY';
export type CommitteeRole = 'CHAIRPERSON' | 'VICE_CHAIRPERSON' | 'SECRETARY' | 'MEMBER';
export type ItemType = 'A' | 'B' | 'C' | 'D' | 'E' | 'F' | 'G' | 'H' | 'I';
export type ProcurementType = 'WORKS' | 'GOODS' | 'CONSULTANCY' | 'NON_CONSULTANCY';
export type Locale = 'en' | 'fr' | 'rw';

// ------------------------------------------------------------------ auth
export type LoginStatus = 'OK' | 'MFA_REQUIRED' | 'MFA_ENROLMENT_REQUIRED';
export interface LoginResponse {
  status: LoginStatus;
  accessToken?: string;
  expiresIn?: number;
  challenge?: string;
}
export interface TokenResponse {
  status: 'OK';
  accessToken: string;
  expiresIn: number;
}
export interface Enrolment {
  secret: string;
  otpauthUri: string;
}
export interface EnrolmentConfirmed {
  recoveryCodes: string[];
  accessToken?: string;
  expiresIn?: number;
}
export interface MfaStatus {
  enrolled: boolean;
  required: boolean;
  recoveryCodesRemaining: number;
}

// ------------------------------------------------------------- identity
export interface MyCommittee {
  committeeId: Uuid;
  name: string;
  code: string;
  role: CommitteeRole;
  membershipId: Uuid;
  termEnd: IsoDate;
}
export interface Me {
  id: Uuid;
  email: string;
  fullName: string;
  locale: Locale;
  institutionId: Uuid | null;
  roles: InstitutionRole[];
  mfaVerified: boolean;
  isProcurementOfficer: boolean;
  committees: MyCommittee[];
  institution?: { name: string; abbreviation: string; defaultCurrency?: string };
}
export interface UserRow {
  id: Uuid;
  email: string;
  fullName: string;
  jobTitle: string | null;
  officeHolderCode: string | null;
  isProcurementOfficer: boolean;
  status: 'INVITED' | 'ACTIVE' | 'LOCKED' | 'DISABLED';
  locale: Locale;
  roles: InstitutionRole[];
}
export interface Institution {
  id: Uuid;
  name: string;
  abbreviation: string;
  institutionType: string;
  supervisingAuthority: string | null;
  minutesLetterhead: string | null;
  minutesFontFamily: string;
  minutesFontSize: number;
  holidayOverride: boolean;
  defaultCurrency: string;
  status: string;
  hasMinutesTemplate: boolean;
  version: number;
}
export interface ConfigSnapshot {
  resolvedAt: IsoInstant;
  values: Record<string, unknown>;
  sources: Record<string, { scope: 'PLATFORM' | 'INSTITUTION'; effectiveFrom: IsoInstant; ruleId: Uuid }>;
}
export interface ConfigHistoryRow {
  id: Uuid;
  ruleKey: string;
  ruleValue: string;
  effectiveFrom: IsoInstant;
  reason: string | null;
  legalReference: string | null;
  createdAt: IsoInstant;
  createdByName: string | null;
  scope: 'PLATFORM' | 'INSTITUTION';
}
export interface Holiday {
  id: Uuid;
  holidayDate: IsoDate;
  name: string;
  kind: 'ADD' | 'REMOVE';
  scope: 'PLATFORM' | 'INSTITUTION';
}
export interface AuditRow {
  id: Uuid;
  occurredAt: IsoInstant;
  actorId: Uuid | null;
  actorName: string | null;
  action: string;
  entityType: string;
  entityId: Uuid | null;
  previousValue: string | null;
  newValue: string | null;
  ipAddress: string | null;
}
export interface PlatformMetric {
  institutionId: Uuid;
  name: string;
  abbreviation: string;
  status: string;
  committees: number;
  activeUsers: number;
  openFiles: number;
  overdueFiles: number;
  sessionsLast30Days: number;
  lastActivity: IsoInstant | null;
}

// ------------------------------------------------------------ committees
export interface CommitteePeriod {
  id: Uuid;
  startsOn: IsoDate;
  endsOn: IsoDate;
  closedOn: IsoDate | null;
  appointmentRef: string | null;
}
export interface CommitteeSummary {
  id: Uuid;
  name: string;
  code: string;
  size: number;
  status: 'ACTIVE' | 'DISSOLVED';
  regulatorAuthRef: string | null;
  regulatorAuthDate: IsoDate | null;
  activeMembers: number;
  quorumRequired: number;
  myRole: CommitteeRole | null;
  currentPeriod: CommitteePeriod | null;
  version?: number;
}
export interface CommitteeMember {
  membershipId: Uuid;
  userId: Uuid;
  fullName: string;
  role: CommitteeRole;
  termStart: IsoDate;
  termEnd: IsoDate;
  consecutiveTerm: number;
  status: 'ACTIVE' | 'ENDED' | 'REPLACED';
  isProcurementOfficer: boolean;
}
export interface CommitteeDetail extends CommitteeSummary {
  members: CommitteeMember[];
  periods: CommitteePeriod[];
}
export interface RuleIssue {
  code: string;
  severity: 'ERROR' | 'WARNING';
  message: string;
  params: Record<string, unknown>;
}
export interface CompositionResult {
  compliant: boolean;
  issues: RuleIssue[];
}
export interface Candidate {
  userId: Uuid;
  fullName: string;
  jobTitle?: string | null;
  isProcurementOfficer: boolean;
  eligible: boolean;
  ineligibleReason: string | null;
}
export interface Appointment {
  userId: Uuid;
  role: CommitteeRole;
  termStart: IsoDate;
  termEnd?: IsoDate;
  appointmentRef?: string;
}
export interface Replacement {
  id: Uuid;
  scope: 'SINGLE' | 'ALL';
  reason: string;
  performedAt: IsoInstant;
  regulatorTaskId: Uuid | null;
}
export interface TrackedTask {
  id: Uuid;
  committeeId: Uuid | null;
  kind: string;
  title: string;
  details: string | null;
  dueDate: IsoDate | null;
  status: 'OPEN' | 'DONE' | 'CANCELLED';
  entityType: string | null;
  entityId: Uuid | null;
  doneAt: IsoInstant | null;
  doneReference: string | null;
  doneNote: string | null;
}

// ----------------------------------------------------------------- files
export interface FileRow {
  id: Uuid;
  fileNumber: string;
  itemType: ItemType;
  tenderRef: string | null;
  contractId: Uuid | null;
  contractRef: string | null;
  title: string;
  procurementType: ProcurementType | null;
  procurementMethod: string | null;
  procurementOfficerId: Uuid | null;
  officerName: string | null;
  stage: string;
  dateReceived: IsoDate;
  targetDate: IsoDate | null;
  overdue: boolean;
  committeeId: Uuid;
  committeeCode: string;
  bidValidityExpiry: IsoDate | null;
}
export interface StageHistoryRow {
  fromStage: string | null;
  toStage: string;
  note: string | null;
  changedAt: IsoInstant;
  changedByName?: string | null;
}
export interface FileDetail extends FileRow {
  referralRef: string | null;
  referralReason: string | null;
  version: number;
  stageHistory: StageHistoryRow[];
  reassignments: { fromCommitteeCode?: string; toCommitteeCode?: string; reason: string; performedAt: IsoInstant }[];
  decisions: DecisionSummary[];
  cbmResponses: CbmResponse[];
  allowedStages: string[];
}
export interface NewFile {
  committeeId: Uuid;
  itemType: ItemType;
  tenderRef?: string;
  contractId?: Uuid;
  title: string;
  procurementType?: ProcurementType;
  procurementMethod?: string;
  procurementOfficerId?: Uuid;
  dateReceived: IsoDate;
  targetDate?: IsoDate;
  referralRef?: string;
  referralReason?: string;
  bidValidityExpiry?: IsoDate;
}
export interface TableRequest {
  id: Uuid;
  fileId: Uuid;
  fileNumber?: string;
  title?: string;
  requestedByName?: string;
  requestedAt: IsoInstant;
  reason: string | null;
  status: 'PENDING' | 'ACCEPTED' | 'DECLINED';
}
export interface Workflow {
  stages: string[];
  recommendationCodes: string[];
  checklist: string[];
}

// ------------------------------------------------------------- contracts
export interface Contract {
  id: Uuid;
  contractRef: string;
  tenderRef: string | null;
  title: string;
  supplierRef: string | null;
  procurementType: ProcurementType;
  currency: string;
  originalValue: Decimal;
  signedDate: IsoDate | null;
  originalCompletionDate: IsoDate;
  currentCompletionDate: IsoDate;
  perfSecurityRef: string | null;
  perfSecurityAmount: Decimal | null;
  perfSecurityCurrency: string | null;
  perfSecurityExpiry: IsoDate | null;
  isFramework: boolean;
  frameworkExpiry: IsoDate | null;
  status: string;
  version?: number;
}
export interface TimelineEvent {
  date: IsoDate;
  kind: 'AMENDMENT' | 'EXTENSION';
  fileId: Uuid;
  fileNumber: string;
  status: string;
  value?: Decimal | null;
  runningCumulative?: Decimal | null;
  runningPct?: Decimal | null;
  projectedCumulative?: Decimal | null;
  projectedPct?: Decimal | null;
  newCompletionDate?: IsoDate | null;
  previousCompletionDate?: IsoDate | null;
  groundCode?: string | null;
  description?: string | null;
}
export interface ContractTimeline {
  contract: Contract;
  events: TimelineEvent[];
  currency: string;
  cumulativeApproved: Decimal;
  cumulativePct: Decimal;
  ceilingPct: Decimal;
  warningPct?: Decimal;
  level?: 'OK' | 'APPROACHING' | 'EXCEEDED';
  headroom?: Decimal;
  originalCompletionDate?: IsoDate;
  currentCompletionDate: IsoDate;
  perfSecurity: { expiry: IsoDate | null; state: 'VALID' | 'EXPIRING' | 'EXPIRED' | 'NONE'; coversCompletion?: boolean; ref?: string | null; amount?: Decimal | null; currency?: string | null };
}

// -------------------------------------------------------------- sessions
export type AttendanceStatus = 'PRESENT' | 'ABSENT_WITH_PERMISSION' | 'ON_LEAVE' | 'ABSENT_UNJUSTIFIED';
export interface Quorum {
  committeeSize: number;
  required: number;
  present: number;
  met: boolean;
  exactlyAtQuorum: boolean;
  needed: number;
  rule: string;
}
export interface SessionRow {
  id: Uuid;
  committeeId: Uuid;
  committeeCode?: string;
  sessionNumber: string;
  kind: 'ORDINARY' | 'EXTRAORDINARY';
  scheduledAt: IsoInstant;
  location: string | null;
  status: 'PLANNED' | 'IN_PROGRESS' | 'HELD' | 'CANCELLED';
  agendaCount?: number;
  shortNotice?: boolean;
  shortNoticeReason?: string | null;
  version?: number;
}
export interface AgendaItem {
  id: Uuid;
  position: number;
  itemType: ItemType;
  fileId: Uuid | null;
  fileNumber: string | null;
  title: string | null;
  adminKind: 'ADOPT_MINUTES' | 'WORK_PLANNING' | 'MEMBER_INDUCTION' | null;
  adminNote: string | null;
  status: 'PENDING' | 'DISCUSSED' | 'DEFERRED';
  decisionId?: Uuid | null;
  decisionStatus?: string | null;
  activities: AgendaActivity[];
}
export type ActivityStatus = 'PLANNED' | 'COMPLETED' | 'NOT_COMPLETED';
export interface AgendaActivity {
  id: Uuid;
  agendaItemId: Uuid;
  activityCode: string;
  status: ActivityStatus;
  note: string | null;
  recordedAt: IsoInstant | null;
  recordedByName: string | null;
  carriedFromId: Uuid | null;
  carriedFromSession: string | null;
}
export interface CarryOverCandidate {
  id: Uuid;
  activityCode: string;
  note: string;
  fileId: Uuid;
  fileNumber: string;
  title: string;
  itemType: ItemType;
  sessionNumber: string;
  scheduledAt: IsoInstant;
}
export interface Apology {
  kind: 'ABSENT_WITH_PERMISSION' | 'ON_LEAVE';
  reason: string;
  submittedAt: IsoInstant;
  timely: boolean;
}
export interface SessionMember {
  membershipId: Uuid;
  userId: Uuid;
  fullName: string;
  role: CommitteeRole;
  attendance: { status: AttendanceStatus; version: number; recordedAt?: IsoInstant } | null;
  apology: Apology | null;
  unnotifiedAbsence?: boolean;
}
export interface SessionDetail {
  session: SessionRow;
  committee: { id: Uuid; name: string; code: string; size: number };
  agenda: AgendaItem[];
  members: SessionMember[];
  quorum: Quorum;
  myRole: CommitteeRole | null;
  myApology: Apology | null;
}

// ------------------------------------------------- decisions, votes, COI
export type VoteChoice = 'FOR' | 'AGAINST' | 'ABSTAIN';
export interface Declaration {
  userId: Uuid;
  fullName: string;
  hasInterest: boolean;
  interestKind: 'DIRECT' | 'INDIRECT' | null;
  details: string | null;
  declaredAt: IsoInstant;
}
export interface DeclarationState {
  declarations: Declaration[];
  pendingMembers: { userId: Uuid; fullName: string }[];
}
export type DecisionStatus = 'DRAFT' | 'VOTING' | 'ADOPTED' | 'NOT_ADOPTED' | 'ISSUED';
export interface DecisionSummary {
  id: Uuid;
  sessionId?: Uuid;
  sessionNumber?: string;
  itemType: ItemType;
  recommendationCode: string;
  recommendationText: string;
  status: DecisionStatus;
  issuedAt?: IsoInstant | null;
}
export interface Decision extends DecisionSummary {
  agendaItemId: Uuid;
  fileId: Uuid;
  recommendedBidderRef: string | null;
  contractValue: Decimal | null;
  currency: string | null;
  structuredOutcome: Record<string, unknown> | null;
  presentCount: number | null;
  eligibleVoters: number | null;
  requiredVotes: number | null;
  votesFor: number;
  votesAgainst: number;
  votesAbstained: number;
  version: number;
}
export interface VoteRow {
  userId: Uuid;
  fullName: string;
  choice: VoteChoice;
  dissentReason: string | null;
  castAt: IsoInstant;
}
export interface Threshold {
  present: number;
  excluded: number;
  eligible: number;
  required: number;
  rule: string;
}
export interface DecisionState {
  decision: Decision | null;
  votes: VoteRow[];
  threshold: Threshold;
  myVote: VoteRow | null;
  canVote: boolean;
}
export interface DecisionDraft {
  recommendationCode: string;
  recommendationText: string;
  recommendedBidderRef?: string;
  contractValue?: string;
  currency?: string;
  structuredOutcome?: Record<string, unknown>;
}
export interface CbmResponse {
  id?: Uuid;
  decisionId: Uuid;
  response: 'APPROVED' | 'RETURNED_FOR_CORRECTION' | 'OTHER';
  reasons: string | null;
  respondedAt: IsoInstant;
  respondedByName?: string;
}
export interface PendingRecommendation {
  decisionId: Uuid;
  fileId: Uuid;
  fileNumber: string;
  title: string;
  itemType: ItemType;
  committeeCode: string;
  sessionNumber: string;
  recommendationCode: string;
  recommendationText: string;
  issuedAt: IsoInstant;
  votesFor: number;
  votesAgainst: number;
  votesAbstained: number;
}

// ------------------------------------------------------------ evaluation
export type StageStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED';
export interface EvaluationStage {
  code: string;
  sequence: number;
  status: StageStatus;
  outcome: string | null;
  data: Record<string, unknown>;
  completedByName: string | null;
  completedAt: IsoInstant | null;
  /** Server-computed: what prevents starting/completing this stage. code is an i18n key. */
  blockers: { code: string; params: Record<string, unknown> }[];
  nextAction?: 'START' | 'COMPLETE' | 'NONE';
  version?: number;
}
export interface BidderResult {
  id: Uuid;
  stageCode: string;
  bidderRef: string;
  result: 'PASS' | 'FAIL';
  failedRequirement: string | null;
  failureReason: string | null;
  rank: number | null;
}
export interface AbnormalPriceCheck {
  id: Uuid;
  bidderRef: string;
  direction: 'ABNORMALLY_HIGH' | 'ABNORMALLY_LOW';
  requestRef: string | null;
  requestDate: IsoDate | null;
  explanationSummary: string | null;
  explanationDate: IsoDate | null;
  analysis: string | null;
  doubtLifted: boolean | null;
  decision: 'PENDING' | 'ACCEPTED' | 'REJECTED';
  decisionReasons: string | null;
  version: number;
}
export interface CureItem {
  code: string;
  label?: string;
  supplied?: boolean;
  suppliedOn?: IsoDate | null;
  note?: string | null;
}
export interface CureRequest {
  id: Uuid;
  bidderRef: string;
  outstandingItems: CureItem[];
  issuedOn: IsoDate;
  workingDays: number;
  deadline: IsoDate;
  suppliedSummary: string | null;
  suppliedOn: IsoDate | null;
  outcome: 'PENDING' | 'COMPLIED' | 'NOT_COMPLIED';
  outcomeNote: string | null;
  workingDaysRemaining?: number | null;
  overdue?: boolean;
  suppliedLate?: boolean;
  version: number;
}
export interface DueDiligence {
  id: Uuid;
  bidderRef: string;
  performedByName: string;
  performedOn: IsoDate;
  itemsVerified: string;
  result: 'CONFIRMED' | 'FALSE' | 'MATERIALLY_CONFUSING' | 'INCOMPLETE';
  disqualified: boolean;
  disqualificationGround: string | null;
  version: number;
}
export interface Clarification {
  id: Uuid;
  bidderRef: string;
  issuedOn: IsoDate;
  subject: string;
  responseReceivedOn: IsoDate | null;
  treatment: string | null;
  effect: 'PENDING' | 'CLARIFIED' | 'ATTEMPTED_ALTERATION';
  version: number;
}
export type BidValidityState = 'OK' | 'REQUEST_DUE_SOON' | 'REQUEST_OVERDUE' | 'EXPIRING' | 'EXPIRED' | 'NOT_SET';
export interface BidValidity {
  expiry: IsoDate | null;
  regulatorRequestDeadline: IsoDate | null;
  daysToRequestDeadline: number | null;
  daysToExpiry: number | null;
  state: BidValidityState;
}
export interface EvaluationView {
  evaluation: { id: Uuid; criteriaLockedAt: IsoInstant | null; criteriaDefectFindings: string | null; version: number } | null;
  criteriaLocked: boolean;
  stages: EvaluationStage[];
  bidderResults: BidderResult[];
  abnormalPriceChecks: AbnormalPriceCheck[];
  cureRequests: CureRequest[];
  dueDiligence: DueDiligence[];
  clarifications: Clarification[];
  bidValidity: BidValidity;
  config?: {
    preliminaryRequirements: string[];
    cureItems: string[];
    cureWorkingDays: number;
    allBidsGrounds: string[];
  };
  currentStage?: string | null;
}
export interface ChecklistItem {
  code: string;
  labelKey: string;
  answer: 'YES' | 'NO' | 'NOT_APPLICABLE' | null;
  note: string | null;
  answeredAt: IsoInstant | null;
  version: number | null;
}
export interface ChecklistView {
  items: ChecklistItem[];
  summary: {
    total: number; answered: number; yes: number; no: number; notApplicable: number;
    complete: boolean; findings: number; suggestedOutcome: string | null;
  };
}
/** A server-computed warning or blocker; code is an i18n key (errors.<code>). */
export interface Issue {
  code: string;
  params: Record<string, unknown>;
}

// --------------------------------------------------------------- C and D
export interface AmendmentAssessment {
  currency: string;
  originalValue: Decimal;
  priorApprovedCumulative: Decimal;
  pendingOtherAmendments: Decimal;
  requestedValue: Decimal;
  resultingCumulative: Decimal;
  resultingPct: Decimal;
  ceilingPct: Decimal;
  warningPct: Decimal;
  absoluteCeiling: Decimal | null;
  level: 'OK' | 'APPROACHING' | 'EXCEEDED';
  qualitative: { answered: boolean; affectsSubstance: boolean | null; justification: string | null; passes: boolean | null };
  quantitative: { withinPct: boolean; withinAbsolute: boolean; passes: boolean };
  route: 'ENTITY' | 'ESCALATED';
  escalation: { applies: boolean; leadDays: number; completionDate: IsoDate; submitBy: IsoDate | null; daysRemaining: number | null; sufficient: boolean } | null;
  prompts?: { perfSecurityReviewRequired: boolean; keyPersonnelApplies: boolean };
  barred?: boolean;
  admissible?: boolean;
  preview?: boolean;
  warnings: Issue[];
}
export interface AmendmentRequest {
  id: Uuid;
  contractId: Uuid;
  currency: string;
  requestedValue: Decimal;
  description: string;
  affectsSubstance: boolean | null;
  substanceJustification: string | null;
  deliverablesChange: boolean | null;
  perfSecurityExtend: boolean | null;
  perfSecurityIncrease: boolean | null;
  keyPersonnelChange: boolean | null;
  replacementEqualOrBetter: boolean | null;
  status: string;
  version: number;
}
export interface ContractSummary {
  id: Uuid;
  contractRef: string;
  title: string;
  currency: string;
  originalValue: Decimal;
  originalCompletionDate: IsoDate;
  currentCompletionDate: IsoDate;
  procurementType: ProcurementType;
}
export interface AmendmentView {
  request: AmendmentRequest | null;
  assessment: AmendmentAssessment | null;
  contract?: ContractSummary;
  frozen?: boolean;
  frozenAt?: string | null;
  otherAmendments?: { fileId: Uuid; fileNumber: string | null; status: string; requestedValue: Decimal; approvedValue: Decimal | null }[];
}
export interface ExtensionAssessment {
  receivedBeforeCompletion: boolean;
  ground: { code: string; bidderFault: boolean } | null;
  bidderFault: boolean;
  penaltiesPrompt: boolean;
  reasoningRequired: boolean;
  costClaim: boolean;
  linkedAmendmentFileId: Uuid | null;
  forceMajeure: { days: number; autoCancelDays: number; daysUntilAutoCancel: number; exceeded: boolean; ongoing: boolean } | null;
  extensionDays: number;
  warnings: Issue[];
  blockers: Issue[];
  readyForRecommendation: boolean;
}
export interface ExtensionRequest {
  id: Uuid;
  contractId: Uuid;
  completionDateBefore: IsoDate;
  requestedCompletionDate: IsoDate;
  groundCode: string;
  groundDetails: string;
  requestReceivedOn: IsoDate;
  receivedBeforeCompletion: boolean | null;
  penaltiesConsidered: boolean | null;
  bidderFaultReasoning: string | null;
  hasCostClaim: boolean;
  linkedAmendmentFileId: Uuid | null;
  forceMajeureStart: IsoDate | null;
  forceMajeureEnd: IsoDate | null;
  status: string;
  version: number;
}
export interface ExtensionView {
  request: ExtensionRequest | null;
  assessment: ExtensionAssessment | null;
  contract?: ContractSummary;
  grounds?: { code: string; bidderFault: boolean }[];
}

// --------------------------------------------------------------- minutes
export interface MinutesSection {
  heading?: string;
  [key: string]: unknown;
}
export interface Minutes {
  id: Uuid;
  sessionId: Uuid;
  status: 'DRAFT' | 'IN_REVIEW' | 'FINAL';
  content: { sections?: MinutesSection[]; [key: string]: unknown };
  secretaryNotes: string | null;
  generatedAt: IsoInstant;
  finalisedAt: IsoInstant | null;
  version: number;
}
export interface MinutesView {
  minutes: Minutes | null;
  canAcknowledge?: boolean;
  acknowledgements: { userId: Uuid; fullName: string; acknowledgedAt: IsoInstant; comment: string | null }[];
  pendingAcknowledgements: { userId: Uuid; fullName: string }[];
}

// ---------------------------------------------------------- notification
export interface NotificationRow {
  id: Uuid;
  kind: string;
  title: string;
  body: string;
  link: string;
  createdAt: IsoInstant;
  readAt: IsoInstant | null;
}

// ------------------------------------------------------------ dashboards
export type Dashboard = Record<string, unknown>;
