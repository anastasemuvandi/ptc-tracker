-- =====================================================================
-- Platform reference data: item types, platform default configuration
-- and the national public holiday calendar.
--
-- Every legal threshold lives here as configuration (see ConfigKeys.java
-- for the catalogue). Values derive from the Ministerial Order on public
-- procurement and must be checked by the platform administrator against
-- the current text; institutions override them with their own rows.
-- =====================================================================

INSERT INTO file_type (code, name_key, can_recommend, needs_file, needs_contract) VALUES
    ('A', 'itemType.A', true,  true,  false),
    ('B', 'itemType.B', true,  true,  false),
    ('C', 'itemType.C', true,  true,  true),
    ('D', 'itemType.D', true,  true,  true),
    ('E', 'itemType.E', true,  true,  false),
    ('F', 'itemType.F', true,  true,  false),
    ('G', 'itemType.G', true,  true,  true),
    ('H', 'itemType.H', true,  true,  false),
    ('I', 'itemType.I', false, false, false);

INSERT INTO config_rule (institution_id, rule_key, rule_value, effective_from, reason) VALUES
-- Committee composition --------------------------------------------------
(NULL, 'committee.allowedSizes',            '[5, 7]', '2020-01-01', 'Platform default'),
(NULL, 'committee.minimumSize',             '3', '2020-01-01', 'Platform default: small institutions'),
(NULL, 'composition.excludedOffices',       '["HEAD_OF_INSTITUTION","CBM","HEAD_OF_FINANCE","INTERNAL_AUDITOR","LEGAL_ADVISOR","LOGISTICS_OFFICER","LOWEST_JOB_CATEGORY"]', '2020-01-01', 'Platform default'),
(NULL, 'composition.blockProcurementOfficerMajority', 'true', '2020-01-01', 'Platform default'),
(NULL, 'term.lengthMonths',                 '36', '2020-01-01', 'Platform default'),
(NULL, 'term.maxRenewals',                  '1', '2020-01-01', 'Platform default'),
(NULL, 'term.consecutiveWarningShare',      '0.5', '2020-01-01', 'Warn when more than this share serves consecutive terms'),
(NULL, 'term.expiryAlertDays',              '60', '2020-01-01', 'Platform default'),
(NULL, 'replacement.regulatorNotificationDays', '7', '2020-01-01', 'Days to notify the regulator after replacing all members'),
-- Sessions, quorum, voting ------------------------------------------------
(NULL, 'session.noticeOrdinaryWorkingDays', '3', '2020-01-01', 'Platform default'),
(NULL, 'session.noticeExtraordinaryHours',  '24', '2020-01-01', 'Platform default'),
(NULL, 'quorum.rule',                       '{"numerator": 3, "denominator": 5}', '2020-01-01', 'Quorum: fraction of committee size'),
(NULL, 'voting.rule',                       '{"numerator": 3, "denominator": 5}', '2020-01-01', 'Adoption: fraction of eligible members present'),
-- Authentication ----------------------------------------------------------
(NULL, 'mfa.memberRequired',                'false', '2020-01-01', 'MFA for ordinary members'),
(NULL, 'stepUp.windowMinutes',              '5', '2020-01-01', 'Recent-authentication window for legally significant acts'),
-- Workflow per item type --------------------------------------------------
(NULL, 'workflow.stages', '{"default": ["RECEIVED","SCHEDULED","UNDER_REVIEW","RECOMMENDATION_ISSUED","SUBMITTED_TO_CBM","CLOSED","RETURNED_FOR_CORRECTION"]}', '2020-01-01', 'Platform default'),
(NULL, 'workflow.targetWorkingDays', '{"A": 15, "B": 10, "C": 10, "D": 7, "E": 15, "F": 10, "G": 10, "H": 10, "I": 5}', '2020-01-01', 'Default target turnaround'),
(NULL, 'recommendation.codes', '{
  "A": ["AWARD", "REJECT_ALL_BIDS", "CANCEL_TENDER", "RETURN_FOR_RE_EVALUATION"],
  "B": ["AS_SUBMITTED", "WITH_CHANGES", "RETURN_FOR_REVISION"],
  "C": ["APPROVE_AMENDMENT", "REJECT_AMENDMENT", "ESCALATE_TO_SUPERVISING_AUTHORITY"],
  "D": ["GRANT_EXTENSION", "GRANT_PARTIAL_EXTENSION", "REFUSE_EXTENSION", "APPLY_DELAY_PENALTIES"],
  "E": ["PREQUALIFY_LIST", "NO_APPLICANT_QUALIFIES", "RETURN_FOR_RE_EVALUATION"],
  "F": ["CANCEL_TENDER", "REJECT_ALL_BIDS", "DO_NOT_CANCEL"],
  "G": ["CONTINUE_FRAMEWORK", "CANCEL_FRAMEWORK"],
  "H": ["RECOMMEND", "DO_NOT_RECOMMEND", "OTHER"]
}', '2020-01-01', 'Platform default'),
(NULL, 'checklist.items', '{
  "B": ["SCOPE_DEFINED", "SPECS_NEUTRAL", "QUALIFICATION_PROPORTIONATE", "NO_DISCRIMINATORY_CLAUSES", "CRITERIA_COMPLETE", "DELIVERABLES_TIMELINES", "ON_PLAN_BUDGET_AVAILABLE"],
  "E": ["CRITERIA_PUBLISHED", "APPLICATIONS_ASSESSED_AGAINST_CRITERIA", "REASONS_FOR_NON_QUALIFICATION"],
  "F": ["GROUNDS_FOR_CANCELLATION", "ALL_BIDS_ABNORMAL_OR_OVER_BUDGET", "BIDDERS_TO_BE_NOTIFIED"],
  "G": ["PERFORMANCE_REVIEWED", "PRICES_STILL_COMPETITIVE", "NEED_CONTINUES"]
}', '2020-01-01', 'Platform default'),
-- Type A evaluation -------------------------------------------------------
(NULL, 'evaluation.stages', '[
  {"code": "A1", "sequence": 1, "nameKey": "evaluation.stage.A1"},
  {"code": "A2", "sequence": 2, "nameKey": "evaluation.stage.A2"},
  {"code": "A3", "sequence": 3, "nameKey": "evaluation.stage.A3"},
  {"code": "A4", "sequence": 4, "nameKey": "evaluation.stage.A4"},
  {"code": "A5", "sequence": 5, "nameKey": "evaluation.stage.A5"},
  {"code": "A6", "sequence": 6, "nameKey": "evaluation.stage.A6"}
]', '2020-01-01', 'Sequence set by Ministerial Order'),
(NULL, 'evaluation.preliminaryRequirements', '["SUBMITTED_ON_TIME_VIA_EPROCUREMENT","BID_FORM_AUTHORISED","BID_SECURITY_OR_DECLARATION","BID_VALIDITY","JOINT_VENTURE_AGREEMENT","NOT_DEBARRED","BENEFICIAL_OWNERSHIP","CATEGORISATION"]', '2020-01-01', 'Platform default'),
(NULL, 'evaluation.cureWorkingDays',        '5', '2020-01-01', 'Administrative document cure period'),
(NULL, 'evaluation.cureItems', '["TAX_CLEARANCE","SOCIAL_SECURITY_CERTIFICATE","VAT_CERTIFICATE","SITE_VISIT_CERTIFICATE","SECTOR_REGULATOR_LICENCE"]', '2020-01-01', 'Platform default'),
(NULL, 'bidSecurity.declarationThreshold',  '{"amount": "50000000", "currency": "RWF"}', '2020-01-01', 'Below this, a bid-securing declaration may replace the bid security'),
(NULL, 'bidValidity.regulatorLeadDays',     '15', '2020-01-01', 'Validity extension request must reach the regulator this many days before expiry'),
(NULL, 'bidValidity.alertBeforeRequestDays','10', '2020-01-01', 'Alert this many days before the extension request deadline'),
(NULL, 'bidValidity.alertBeforeExpiryDays', '7', '2020-01-01', 'Second alert before expiry'),
-- Type C / D --------------------------------------------------------------
(NULL, 'amendment.ceilingPct',              '20', '2020-01-01', 'Cumulative amendment ceiling, % of original contract value'),
(NULL, 'amendment.warningPct',              '15', '2020-01-01', 'Warn as cumulative figure approaches the ceiling'),
(NULL, 'amendment.absoluteCeilings',        '{"RWF": "1000000000", "USD": "800000", "EUR": "750000"}', '2020-01-01', 'Entity-level absolute ceiling per contract currency (no conversion)'),
(NULL, 'amendment.escalationLeadDays',      '45', '2020-01-01', 'Calendar days needed before completion for escalated approval'),
(NULL, 'extension.grounds', '[
  {"code": "FORCE_MAJEURE", "bidderFault": false},
  {"code": "ACT_OF_GOVERNMENT", "bidderFault": false},
  {"code": "UNFORESEEN_PHYSICAL_CONDITIONS", "bidderFault": false},
  {"code": "PROCURING_ENTITY_DELAY", "bidderFault": false},
  {"code": "BIDDER_FAULT", "bidderFault": true}
]', '2020-01-01', 'Platform default'),
(NULL, 'forceMajeure.autoCancelDays',       '90', '2020-01-01', 'Contract cancelled after force majeure of this duration'),
-- Reminders ---------------------------------------------------------------
(NULL, 'contract.completionAlertDays',      '30', '2020-01-01', 'Platform default'),
(NULL, 'framework.expiryAlertDays',         '60', '2020-01-01', 'Platform default'),
(NULL, 'framework.annualEvaluationAlertDays', '30', '2020-01-01', 'Platform default');

-- National public holidays. Eid dates depend on moon sighting and are
-- confirmed annually by government notice; adjust when announced.
INSERT INTO holiday (institution_id, holiday_date, name) VALUES
    (NULL, '2026-01-01', 'New Year''s Day'),
    (NULL, '2026-01-02', 'Day after New Year''s Day'),
    (NULL, '2026-02-01', 'National Heroes'' Day'),
    (NULL, '2026-03-20', 'Eid al-Fitr (provisional)'),
    (NULL, '2026-04-03', 'Good Friday'),
    (NULL, '2026-04-06', 'Easter Monday'),
    (NULL, '2026-04-07', 'Genocide against the Tutsi Memorial Day'),
    (NULL, '2026-05-01', 'Labour Day'),
    (NULL, '2026-05-27', 'Eid al-Adha (provisional)'),
    (NULL, '2026-07-01', 'Independence Day'),
    (NULL, '2026-07-04', 'Liberation Day'),
    (NULL, '2026-08-07', 'Umuganura Day'),
    (NULL, '2026-08-15', 'Assumption Day'),
    (NULL, '2026-12-25', 'Christmas Day'),
    (NULL, '2026-12-26', 'Boxing Day'),
    (NULL, '2027-01-01', 'New Year''s Day'),
    (NULL, '2027-01-02', 'Day after New Year''s Day'),
    (NULL, '2027-02-01', 'National Heroes'' Day'),
    (NULL, '2027-03-10', 'Eid al-Fitr (provisional)'),
    (NULL, '2027-03-26', 'Good Friday'),
    (NULL, '2027-03-29', 'Easter Monday'),
    (NULL, '2027-04-07', 'Genocide against the Tutsi Memorial Day'),
    (NULL, '2027-05-01', 'Labour Day'),
    (NULL, '2027-05-16', 'Eid al-Adha (provisional)'),
    (NULL, '2027-07-01', 'Independence Day'),
    (NULL, '2027-07-04', 'Liberation Day'),
    (NULL, '2027-08-06', 'Umuganura Day'),
    (NULL, '2027-08-15', 'Assumption Day'),
    (NULL, '2027-12-25', 'Christmas Day'),
    (NULL, '2027-12-26', 'Boxing Day');
