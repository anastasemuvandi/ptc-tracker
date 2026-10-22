# PTC Tracker — HTTP API contract

Base path `/api`. JSON, camelCase. UUIDs are canonical lowercase strings and
**opaque** to clients. Dates `YYYY-MM-DD` (Africa/Kigali calendar dates);
instants ISO-8601 UTC. Money: `{ "amount": "123456.00", "currency": "RWF" }`
where a field is monetary, or a decimal string plus a sibling currency field.

## Conventions

* `Authorization: Bearer <access token>` on every call except auth bootstrap.
* Every authenticated `POST/PUT/PATCH/DELETE` under `/api/` (except
  `/api/auth/**` and multipart uploads) **must** carry
  `Idempotency-Key: <client-generated UUID>`. Reuse the same key when
  retrying the same logical action; a replay returns the original response
  with `Idempotent-Replay: true`.
* Errors: `{ "code": "…", "message": "English fallback", "params": {…} }`.
  The client translates `errors.<code>` with `params`.
  * `400` validation / malformed UUID (`invalid_uuid`)
  * `401` `auth.invalid_token` (refresh and retry once), `auth.step_up_required`
    (prompt for TOTP/password, call `/api/auth/step-up`, retry with the same
    idempotency key)
  * `403` forbidden, `read_only`
  * `409` `concurrent_modification` (reload), `constraint.<name>`
  * `422` configured rule blocks the action (`params` explain)
* Optimistic locking: mutable resources return `version`; updates send it back.

## Auth (`auth.internal`)

| Method | Path | Body → Response |
|---|---|---|
| GET | `/.well-known/jwks.json` | JWKS |
| POST | `/api/auth/login` | `{email,password}` → `{status:"OK",accessToken,expiresIn}` + refresh cookie, or `{status:"MFA_REQUIRED"\|"MFA_ENROLMENT_REQUIRED", challenge}` |
| POST | `/api/auth/mfa/verify` | `{challenge, code \| recoveryCode}` → tokens |
| POST | `/api/auth/mfa/enrol/start` | `{challenge?}` (or bearer) → `{secret, otpauthUri}` |
| POST | `/api/auth/mfa/enrol/confirm` | `{challenge?, code}` → `{recoveryCodes[], accessToken?}` |
| GET | `/api/auth/mfa/status` | → `{enrolled, required, recoveryCodesRemaining}` |
| POST | `/api/auth/refresh` | header `X-PTC-Client: web`, cookie → `{accessToken,expiresIn}` (rotates cookie) |
| POST | `/api/auth/logout` | header `X-PTC-Client: web` → 204 |
| POST | `/api/auth/step-up` | `{code}` or `{password}` (no MFA) → `{verifiedAt}` |
| POST | `/api/auth/invitations/accept` | `{token,password}` → 204 |
| POST | `/api/auth/password/forgot` | `{email}` → 202 |
| POST | `/api/auth/password/reset` | `{token,password}` → 204 |
| POST | `/api/auth/password/change` | `{currentPassword,newPassword}` → 204 |

Access-token claims: `iss, sub, aud, iat, nbf, exp, jti, institution_id,
roles[], committees[{committee_id, role}], token_version, sid, amr`.

## Identity, institution, configuration

| Method | Path | Notes |
|---|---|---|
| GET/PUT | `/api/me` | profile, `roles`, `committees[{committeeId,name,code,role,membershipId,termEnd}]`, `institution` |
| GET | `/api/users` | institution users (`roles[]`, `isProcurementOfficer`, `officeHolderCode`, `status`, `requesterCategory`, `accessUntil`) |
| POST | `/api/users` | invite (INSTITUTION_ADMIN) `{email,fullName,jobTitle,officeHolderCode,procurementOfficer,locale,roles[],requesterCategory?,accessUntil?}`; with role REQUESTER: `requesterCategory` SHORT_TERM (needs `accessUntil`) or LONG_TERM. 400 `user.requester_category_required`, `user.access_until_required`; 422 `user.access_until_past` |
| PUT | `/api/users/{id}/requester-access` | admin `{category, accessUntil?}`; short-term access ends automatically after `accessUntil` (daily job: the REQUESTER role is removed, or the account disabled if it had no other role) |
| PUT | `/api/users/{id}` · `/roles` · `/status` | admin edits |
| POST | `/api/users/{id}/resend-invitation` · `/reset-mfa` | |
| GET/PUT | `/api/institution` | settings, minutes letterhead/font, `holidayOverride` |
| POST/GET | `/api/institution/minutes-template` | multipart `.docx` upload / download |
| GET | `/api/config?at=` | resolved snapshot `{resolvedAt, values{}, sources{key:{scope,effectiveFrom}}}` |
| GET | `/api/config/{key}/history` | all versions |
| POST | `/api/config` | `{key,value,effectiveFrom,reason,legalReference,scope:"INSTITUTION"\|"PLATFORM"}` |
| GET/POST/DELETE | `/api/holidays?year=` | `{date,name,kind:"ADD"\|"REMOVE",scope}` |
| GET | `/api/audit?entityId&actorId&action&before&limit` | READ_ONLY / admin / CBM |
| GET | `/api/platform/metrics` | PLATFORM_ADMIN — operational counts only |
| POST | `/api/platform/institutions` | create institution + invite first admin |
| GET | `/api/platform/institutions/{id}/admins` | PLATFORM_ADMIN → `[{id,fullName,email,status,mfaEnrolled}]` (institution administrators only) |
| POST | `/api/platform/institutions/{id}/admins/{userId}/reset-mfa` | PLATFORM_ADMIN, step-up (act `MFA_RESET`) → 204; ends the user's sessions. 404 if the user is not in that institution; 422 `platform.not_institution_admin` |

## Requests (requesters submit; officers accept or return)

Request types come from `request.types` (default C, D, H). Numbers are `REQ-<year>/<nnnn>`.

| Method | Path | Notes |
|---|---|---|
| GET | `/api/requests/mine` | requester's own `[{id,requestNumber,itemType,title,status,contractRef,tenderRef,submittedAt,decidedAt,createdAt,requesterName,documentCount,fileNumber,fileStage,nextSessionNumber,nextSessionAt}]` |
| GET | `/api/requests/inbox?status` | officers of any committee and readers of all committees; never drafts |
| POST | `/api/requests` | REQUESTER `{itemType,title,description,contractRef?,tenderRef?}` → DRAFT; C/D need `contractRef`. 400 `request.invalid_type`, `request.invalid`, `request.contract_ref_required`; 403 `request.access_ended` |
| GET | `/api/requests/{id}` | requester, or a reviewer once submitted: request + `documents[]` + `file{id,fileNumber,stage,committeeCode,nextSession}` |
| PUT | `/api/requests/{id}` | requester, DRAFT/RETURNED; 422 `request.not_editable` |
| POST | `/api/requests/{id}/documents` | multipart `file`; requester, DRAFT/RETURNED; formats `request.documentTypes` (content checked against the declared type), size `request.maxDocumentBytes`, count `request.maxDocuments`. 422 `request.document_type`, `request.document_too_large`, `request.too_many_documents` |
| GET · DELETE | `/api/requests/{id}/documents/{docId}` | download (requester or reviewer) · remove (requester, DRAFT/RETURNED) |
| POST | `/api/requests/{id}/submit` | `{declarationConfirmed:true}` (no bid documents, bidder submissions or prices); needs `request.minDocuments`. Notifies committee officers |
| POST | `/api/requests/{id}/withdraw` | requester, before acceptance |
| POST | `/api/requests/{id}/accept` | officer of `committeeId` `{committeeId, contractId?, procurementOfficerId?, targetDate?, title?}` → registers the file (H: referral = request number and description) and notifies the requester |
| POST | `/api/requests/{id}/schedule` | officer of the session's committee `{sessionId, contractId?, procurementOfficerId?, activities?}`: accepts the request as a file of that committee and puts it on the session agenda in one transaction (agenda and activity errors as for `POST /api/sessions/{id}/agenda`; nothing is kept if either step fails) |
| POST | `/api/requests/{id}/return` | officer `{reason}` → RETURNED; requester may edit and submit again |
| GET | `/api/files/{fileId}/request` | the request behind a file with its documents (204 when none) |

## Committees (CBM constitutes; officers read)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/committees` | `[{id,name,code,size,status,regulatorAuthRef,regulatorAuthDate,activeMembers,quorumRequired,myRole,currentPeriod:{id,startsOn,endsOn,closedOn,appointmentRef}}]` |
| POST | `/api/committees` | CBM `{name,periodStart}` opens the first period (end = start + `committee.periodMonths` − 1 day; the start may be in the future). The name is unique in the institution, dissolved committees included, ignoring case and spacing. The code (session numbers) is derived from the name and the size is the smallest of `committee.allowedSizes`. 400 `committee.invalid`; 409 `committee.name_taken` `{name}` |
| GET/PUT | `/api/committees/{id}` | PUT: CBM `{name?,size?,regulatorAuthRef?,regulatorAuthDate?,version}`, 409 `committee.name_taken`. GET: detail + `members[]` (`membershipId,userId,fullName,role,termStart,termEnd,consecutiveTerm,status,isProcurementOfficer`) and `periods[]` (newest first) |
| POST | `/api/committees/{id}/periods` | CBM `{startsOn,appointmentRef?}` (400 `committee.period_start_required`) reappoints the committee: same name, new period; a running period closes the day before. Active seats end except the Secretary's, which continues into the new period. 422 `committee.period_overlap`, `committee.period_future` |
| GET | `/api/committees/{id}/composition` | `{compliant, issues:[{code,severity:"ERROR"\|"WARNING",message,params}]}` |
| GET | `/api/committees/{id}/candidates` | institution users with `eligible` + `ineligibleReason` codes |
| POST | `/api/committees/{id}/members/check` | dry-run of an appointment → composition result |
| POST | `/api/committees/{id}/members` | CBM `{userId,role,termStart,termEnd?,appointmentRef?,renewMembershipId?}`; `termEnd` defaults to the end of the current period (renewal ends the old seat and starts the next consecutive term) → `{membership, warnings[]}`; 422 on `composition.*` |
| POST | `/api/committees/{id}/members/{membershipId}/end` | `{reason}` |
| POST | `/api/committees/{id}/replacements` | CBM `{scope:"SINGLE"\|"ALL", reason, outgoingMembershipIds[], appointments[{userId,role,termStart,termEnd?}]}` → `{replacement, regulatorTask?, period?}`; ALL replaces every member except the Secretary and opens a new period from today |
| GET | `/api/committees/{id}/replacements` | history |
| GET | `/api/tasks?status=OPEN` | tracked tasks (regulator notifications, validity extension requests …) |
| POST | `/api/tasks/{id}/complete` | `{reference,note}` |

Composition codes: `composition.cbm_cannot_be_member`,
`composition.excluded_office`, `composition.procurement_officer_majority`,
`composition.size_exceeded`, `composition.role_taken`,
`composition.already_member`, `composition.term_limit_reached`;
warnings `composition.consecutive_terms_majority`,
`composition.below_size`, `composition.secretary_not_procurement_officer`,
`composition.no_procurement_officer`.

## Files register

| Method | Path | Notes |
|---|---|---|
| GET | `/api/files?committeeId&type&officerId&stage&overdue&procurementType&contractId` | `[{id,fileNumber,itemType,tenderRef,contractId,contractRef,title,procurementType,procurementMethod,procurementOfficerId,officerName,stage,dateReceived,targetDate,overdue,committeeId,committeeCode,bidValidityExpiry}]` |
| POST | `/api/files` | committee officer (Chair, Vice-Chair, Secretary) `{committeeId,itemType,tenderRef?,contractId?,title,procurementType?,procurementMethod?,procurementOfficerId?,dateReceived,targetDate?,referralRef?,referralReason?,bidValidityExpiry?}` |
| GET | `/api/files/{id}` | detail + `stageHistory[]`, `reassignments[]`, `decisions[]`, `cbmResponses[]`, `allowedStages[]` |
| PUT | `/api/files/{id}` | edit with `version` |
| POST | `/api/files/{id}/stage` | `{toStage,note}` |
| POST | `/api/files/{id}/reassign` | CBM `{toCommitteeId,reason}` |
| POST | `/api/files/{id}/officer` | CBM `{officerId,financialYear}` |
| POST | `/api/files/{id}/table-requests` | member `{reason}` |
| GET | `/api/committees/{id}/table-requests?status=` | |
| POST | `/api/table-requests/{id}/decide` | Chairperson `{accept,note,sessionId?}` |
| GET | `/api/workflow?type=A` | `{stages[], recommendationCodes[], checklist[]}` from config |

## Contracts

| Method | Path | Notes |
|---|---|---|
| GET/POST | `/api/contracts` | `{contractRef,tenderRef,title,supplierRef,procurementType,currency,originalValue,signedDate,originalCompletionDate,perfSecurityRef,perfSecurityAmount,perfSecurityCurrency,perfSecurityExpiry,isFramework,frameworkExpiry}` |
| GET/PUT | `/api/contracts/{id}` | |
| GET | `/api/contracts/{id}/timeline` | `{contract, events[{date,kind:"AMENDMENT"\|"EXTENSION",fileId,fileNumber,status,value?,runningCumulative?,runningPct?,newCompletionDate?}], cumulativeApproved, cumulativePct, ceilingPct, currentCompletionDate, perfSecurity:{expiry,state:"VALID"\|"EXPIRING"\|"EXPIRED"\|"NONE"}}` |

## Sessions, attendance, quorum

| Method | Path | Notes |
|---|---|---|
| GET | `/api/sessions?committeeId&from&to` | list with `sessionNumber,kind,scheduledAt,status,agendaCount` |
| POST | `/api/sessions` | officer `{committeeId,kind,scheduledAt,location,shortNoticeReason?, items?:[{fileId,activities}], requests?:[{requestId,contractId?,activities}]}`: schedules the session with its first agenda in one transaction; `requests` are accepted as files of the committee. 422 `session.short_notice_reason_required` `{earliestWithoutShortNotice}`, and agenda, activity and request errors (nothing is kept) |
| GET | `/api/sessions/activity-options?fileId` | officer of the file's committee `{allowed, outstanding}` (before a session exists) |
| GET | `/api/sessions/{id}` | `{session, committee, agenda[] (each with activities[{id,activityCode,status,note,recordedAt,recordedByName,carriedFromId,carriedFromSession}]), members[], quorum, myRole, myApology}` |
| PUT | `/api/sessions/{id}` | officer, with `version` |
| GET | `/api/sessions/{id}/quorum` | live quorum |
| POST | `/api/sessions/{id}/status` | `{status:"IN_PROGRESS"\|"HELD"\|"CANCELLED"}`; HELD 422 `session.activities_unrecorded` while an activity is still PLANNED |
| POST | `/api/sessions/{id}/agenda` | officer `{fileId?, itemType, adminKind?, adminNote?, activities?:[code]}`; codes from `agenda.activities[itemType]` (type A: the evaluation stage codes); omitted → the file's outstanding activities. 422 `agenda.out_of_scope`, `agenda.type_mismatch`, `agenda.file_other_committee`, `agenda.activity_required`, `agenda.activity_unknown`, `agenda.activity_done` |
| GET | `/api/sessions/{id}/activity-options?fileId` | officer `{allowed:[code], outstanding:[code]}` |
| POST | `/api/sessions/{id}/activities/{activityId}` | officer, session IN_PROGRESS `{status:"COMPLETED"\|"NOT_COMPLETED", note?}` (note required when not completed). Type A stage codes complete the evaluation stage (its checks apply) and are completed by it; RECOMMENDATION/A6 complete when voting adopts the recommendation. 422 `activity.session_not_in_progress`, `activity.stage_completed`, `activity.recommendation_not_adopted`; 400 `activity.reason_required` |
| GET | `/api/sessions/{id}/carry-over` | activities NOT_COMPLETED in earlier HELD sessions of the committee, not yet carried over: `[{id,activityCode,note,fileId,fileNumber,title,itemType,sessionNumber,scheduledAt}]` |
| POST | `/api/sessions/{id}/carry-over` | officer `{activityIds}` → activities planned on this agenda (item created if needed) with `carriedFromId`; each activity is carried once. 422 `agenda.carry_over_invalid` |
| DELETE | `/api/sessions/{id}/agenda/{itemId}` | removes its planned activities; 422 `agenda.has_activity_record` once one is recorded |
| PUT | `/api/sessions/{id}/agenda/order` | `[itemId…]` |
| PUT | `/api/sessions/{id}/attendance/{membershipId}` | officer `{status, clientRecordedAt, baseVersion?}` → `{attendance, quorum}`; 409 `attendance.conflict` `{server}` |
| POST | `/api/sessions/{id}/apologies` | self `{kind,reason,clientRecordedAt}` → apology (`timely`) |

`quorum = {committeeSize, required, present, met, exactlyAtQuorum, needed, rule:"3/5"}`

## Decisions, votes, conflict of interest, CBM

| Method | Path | Notes |
|---|---|---|
| GET | `/api/agenda-items/{id}/declarations` | `{declarations[], pendingMembers[]}` |
| POST | `/api/agenda-items/{id}/declarations` | **step-up**; self `{hasInterest, interestKind?, details?}` |
| GET | `/api/declarations/export?sessionId=` | CSV |
| GET | `/api/agenda-items/{id}/decision` | `{decision?, votes[], threshold:{present,excluded,eligible,required,rule}, myVote, canVote}` |
| POST | `/api/agenda-items/{id}/decision` | officer draft `{recommendationCode,recommendationText,recommendedBidderRef?,contractValue?,currency?,structuredOutcome?}` |
| GET | `/api/decisions/{id}` | decision record (with stored `configSnapshot`) |
| PUT | `/api/decisions/{id}` | draft edit with `version` |
| POST | `/api/decisions/{id}/open-voting` | officer; 422 `decision.declarations_pending`, `decision.no_quorum` |
| POST | `/api/decisions/{id}/votes` | **step-up**; `{choice:"FOR"\|"AGAINST"\|"ABSTAIN", dissentReason?}` (reason required unless FOR) |
| POST | `/api/decisions/{id}/close-voting` | officer → `ADOPTED`/`NOT_ADOPTED` |
| POST | `/api/decisions/{id}/issue` | Chairperson/Vice, **step-up** → `ISSUED` |
| POST | `/api/decisions/{id}/submit-to-cbm` | officer → file stage `SUBMITTED_TO_CBM` |
| GET | `/api/cbm/pending` | CBM: issued recommendations awaiting response |
| POST | `/api/decisions/{id}/cbm-response` | CBM `{response:"APPROVED"\|"RETURNED_FOR_CORRECTION"\|"OTHER", reasons}` |

## Type A evaluation, checklists

| Method | Path | Notes |
|---|---|---|
| GET | `/api/files/{id}/evaluation` | `{evaluation, criteriaLocked, stages[{code,sequence,status,outcome,data,completedByName,completedAt,blockers[]}], bidderResults[], abnormalPriceChecks[], cureRequests[], dueDiligence[], clarifications[], bidValidity}` |
| POST | `/api/files/{id}/evaluation/criteria-lock` | `{confirmed:true, defectFindings?}` |
| PUT | `/api/files/{id}/evaluation/findings` | `{defectFindings}` |
| POST | `/api/files/{id}/evaluation/stages/{code}/start` · `/complete` | complete `{outcome}`; 422 lists blockers |
| PUT | `/api/files/{id}/evaluation/stages/{code}` | `{data, outcome, version}` |
| PUT | `/api/files/{id}/evaluation/stages/{code}/bidders` | `{bidderRef,result,failedRequirement?,failureReason?,rank?}` |
| POST/PUT | `/api/files/{id}/abnormal-prices` · `/api/abnormal-prices/{id}` | 422 `evaluation.abnormal_rejection_without_exchange` |
| POST/PUT | `/api/files/{id}/cure-requests` · `/api/cure-requests/{id}` | deadline = issuedOn + N working days |
| POST/PUT | `/api/files/{id}/due-diligence` · `/api/due-diligence/{id}` | |
| POST/PUT | `/api/files/{id}/clarifications` · `/api/clarifications/{id}` | |
| POST | `/api/files/{id}/evaluation/raise-rejection-of-all` | `{ground,reason}` → new type F file |
| GET | `/api/files/{id}/checklist` / PUT `/api/files/{id}/checklist/{itemCode}` | `{answer:"YES"\|"NO"\|"NOT_APPLICABLE", note, version?}` (note required unless YES) → `{itemType, items[{code,labelKey,answer,note,answeredAt,version}], summary{total,answered,yes,no,notApplicable,complete,findings,suggestedOutcome}}` |

`bidValidity = {expiry, regulatorRequestDeadline, daysToRequestDeadline, daysToExpiry, state:"OK"\|"REQUEST_DUE_SOON"\|"REQUEST_OVERDUE"\|"EXPIRING"\|"EXPIRED"\|"NOT_SET"}`

## Type C / D

| Method | Path | Notes |
|---|---|---|
| GET/POST/PUT | `/api/files/{id}/amendment` | request + `assessment` |
| GET/POST/PUT | `/api/files/{id}/extension` | request + `assessment` |
| POST | `/api/files/{id}/extension/cost-claim` | `{requestedValue, description?}` → `{fileId, fileNumber}` of the linked type C file |
| POST | `/api/files/{id}/amendment/freeze` | officer: freeze assessment + config snapshot (also automatic once the file's recommendation is ISSUED) |

`assessment` (C) = `{currency, originalValue, priorApprovedCumulative,
pendingOtherAmendments, requestedValue, resultingCumulative, resultingPct,
ceilingPct, warningPct, absoluteCeiling, level:"OK"\|"APPROACHING"\|"EXCEEDED",
qualitative:{answered, affectsSubstance, justification, passes},
quantitative:{withinPct, withinAbsolute, passes},
route:"ENTITY"\|"ESCALATED", escalation:{leadDays, submitBy, daysRemaining,
sufficient}, warnings[]}`

`assessment` (D) = `{receivedBeforeCompletion, ground, bidderFault,
penaltiesPrompt, reasoningRequired, costClaim, linkedAmendmentFileId,
forceMajeure:{days, autoCancelDays, exceeded}, extensionDays}`

Additions (B2): amendment GET returns `{fileId, fileNumber, contract, request, assessment,
frozen, frozenAt?, otherAmendments[]}`; assessment also carries `priorApprovedPct,
includingPendingPct, barred, admissible, preview, prompts{perfSecurityReviewRequired,
perfSecurityExtend, perfSecurityIncrease, deliverablesChange, keyPersonnelApplies,
keyPersonnelChange, replacementEqualOrBetter}`; `warnings[]` are `{code, params}`.
Extension assessment also carries `warnings[], blockers[], readyForRecommendation`
(bidder-fault ground requires `penaltiesConsidered=true` and `bidderFaultReasoning`).
Money fields are decimal strings. Status follows the decision log on read: ISSUED →
RECOMMENDED (assessment frozen); CBM APPROVED of APPROVE_AMENDMENT / GRANT_* →
APPROVED (extension moves `contract.currentCompletionDate`, using
`structuredOutcome.approvedCompletionDate` if present); of REJECT_AMENDMENT /
REFUSE_EXTENSION / APPLY_DELAY_PENALTIES → REJECTED. Timeline events also carry
`projectedCumulative/projectedPct` for pending amendments; result adds `currency,
warningPct, level, headroom, originalCompletionDate`, `perfSecurity.coversCompletion`.

Evaluation view (B2): stages carry `nameKey, nextAction:"START"\|"COMPLETE"\|"NONE",
blockers[{code:"evaluation.blocker.*", params}]`; payload adds `currentStage, complete,
config{preliminaryRequirements, cureItems, cureWorkingDays, bidSecurityDeclarationThreshold,
allBidsGrounds}, issuedDecision`. Start/complete failures are 422 `evaluation.stage_blocked`
with `params.blockers`. Cure requests add `workingDaysRemaining, overdue, suppliedLate`.
Stage data keys containing score/price/amount/cost/points are refused (422
`evaluation.scores_not_stored`).

## Minutes

| Method | Path | Notes |
|---|---|---|
| POST | `/api/sessions/{id}/minutes/generate` | (re)generate draft content |
| GET | `/api/sessions/{id}/minutes` | `{minutes, acknowledgements[], pendingAcknowledgements[]}` |
| PUT | `/api/minutes/{id}` | `{secretaryNotes, version}` |
| POST | `/api/minutes/{id}/submit-for-review` · `/acknowledge` · `/finalise` | |
| GET | `/api/minutes/{id}/docx` | download (server-side Apache POI on the institution template) |

## Notifications, dashboards, reports

| Method | Path | Notes |
|---|---|---|
| GET | `/api/notifications?unread=true` | `[{id,kind,title,body,link,createdAt,readAt}]` |
| POST | `/api/notifications/{id}/read` | |
| GET | `/api/push/vapid-public-key` | |
| POST/DELETE | `/api/push/subscriptions` | `{endpoint, keys:{p256dh, auth}}` |
| GET | `/api/dashboard/member` | my files, sessions, pending declarations, attendance |
| GET | `/api/dashboard/chair?committeeId=` | workload, overdue by stage/type, quorum risk, turnaround, validity expiring, ceilings |
| GET | `/api/dashboard/cbm` | committees, pending recommendations, compliance, term expiries, workload |
| GET | `/api/reports/summary?from&to&committeeId` (+ `.csv`) | files by type, sessions held, attendance |

## Notification landing routes (frontend)

Each reminder links to one screen that answers one question with one action:
`/n/bid-validity/:fileId`, `/n/cure/:cureRequestId`, `/n/term/:membershipId`,
`/n/session/:sessionId`, `/n/declaration/:agendaItemId`,
`/n/vote/:decisionId`, `/n/minutes/:minutesId`, `/n/task/:taskId`,
`/n/contract/:contractId`, `/n/recommendation/:decisionId`.
