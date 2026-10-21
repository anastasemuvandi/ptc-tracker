package rw.gov.ptc.config.component;

/**
 * Catalogue of configuration keys. Platform defaults are seeded in
 * V10__reference_data.sql; institutions override with their own rows.
 */
public final class ConfigKeys {

    private ConfigKeys() {
    }

    // Committee composition
    public static final String COMMITTEE_ALLOWED_SIZES = "committee.allowedSizes";
    public static final String COMMITTEE_MINIMUM_SIZE = "committee.minimumSize";
    public static final String COMMITTEE_PERIOD_MONTHS = "committee.periodMonths";
    public static final String EXCLUDED_OFFICES = "composition.excludedOffices";
    public static final String BLOCK_PO_MAJORITY = "composition.blockProcurementOfficerMajority";
    public static final String TERM_LENGTH_MONTHS = "term.lengthMonths";
    public static final String TERM_MAX_RENEWALS = "term.maxRenewals";
    public static final String TERM_LIMIT_BLOCKS = "term.limitBlocks";
    public static final String TERM_CONSECUTIVE_WARNING_SHARE = "term.consecutiveWarningShare";
    public static final String TERM_EXPIRY_ALERT_DAYS = "term.expiryAlertDays";
    public static final String REGULATOR_NOTIFICATION_DAYS = "replacement.regulatorNotificationDays";

    // Sessions, quorum, voting
    public static final String NOTICE_ORDINARY_WORKING_DAYS = "session.noticeOrdinaryWorkingDays";
    public static final String NOTICE_EXTRAORDINARY_HOURS = "session.noticeExtraordinaryHours";
    public static final String QUORUM_RULE = "quorum.rule";
    public static final String VOTING_RULE = "voting.rule";

    // Authentication
    public static final String MFA_MEMBER_REQUIRED = "mfa.memberRequired";
    public static final String STEP_UP_WINDOW_MINUTES = "stepUp.windowMinutes";

    // Workflow
    public static final String WORKFLOW_STAGES = "workflow.stages";
    public static final String AGENDA_ACTIVITIES = "agenda.activities";
    public static final String WORKFLOW_TARGET_WORKING_DAYS = "workflow.targetWorkingDays";
    public static final String RECOMMENDATION_CODES = "recommendation.codes";
    public static final String CHECKLIST_ITEMS = "checklist.items";

    // Type A
    public static final String EVALUATION_STAGES = "evaluation.stages";
    public static final String PRELIMINARY_REQUIREMENTS = "evaluation.preliminaryRequirements";
    public static final String CURE_WORKING_DAYS = "evaluation.cureWorkingDays";
    public static final String CURE_ITEMS = "evaluation.cureItems";
    public static final String BID_SECURITY_DECLARATION_THRESHOLD = "bidSecurity.declarationThreshold";
    public static final String BID_VALIDITY_REGULATOR_LEAD_DAYS = "bidValidity.regulatorLeadDays";
    public static final String BID_VALIDITY_ALERT_BEFORE_REQUEST_DAYS = "bidValidity.alertBeforeRequestDays";
    public static final String BID_VALIDITY_ALERT_BEFORE_EXPIRY_DAYS = "bidValidity.alertBeforeExpiryDays";

    // Type C / D
    public static final String AMENDMENT_CEILING_PCT = "amendment.ceilingPct";
    public static final String AMENDMENT_WARNING_PCT = "amendment.warningPct";
    public static final String AMENDMENT_ABSOLUTE_CEILINGS = "amendment.absoluteCeilings";
    public static final String AMENDMENT_ESCALATION_LEAD_DAYS = "amendment.escalationLeadDays";
    public static final String EXTENSION_GROUNDS = "extension.grounds";
    public static final String FORCE_MAJEURE_AUTO_CANCEL_DAYS = "forceMajeure.autoCancelDays";

    // Reminders
    public static final String CONTRACT_COMPLETION_ALERT_DAYS = "contract.completionAlertDays";
    public static final String FRAMEWORK_EXPIRY_ALERT_DAYS = "framework.expiryAlertDays";
    public static final String FRAMEWORK_ANNUAL_EVALUATION_ALERT_DAYS = "framework.annualEvaluationAlertDays";
}
