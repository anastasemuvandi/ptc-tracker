package rw.gov.ptc.decision;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.gov.ptc.audit.AuditService;
import rw.gov.ptc.auth.api.StepUpVerifier;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.common.Rows;
import rw.gov.ptc.config.ConfigKeys;
import rw.gov.ptc.config.ConfigService;
import rw.gov.ptc.config.ConfigSnapshot;
import rw.gov.ptc.file.FileStages;
import rw.gov.ptc.file.ProcFile;
import rw.gov.ptc.file.ProcFileRepository;
import rw.gov.ptc.security.Access;
import rw.gov.ptc.session.AgendaItem;
import rw.gov.ptc.session.AgendaItemRepository;
import rw.gov.ptc.session.CommitteeSession;
import rw.gov.ptc.session.Quorum;
import rw.gov.ptc.session.SessionAccess;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The decision log: a recommendation per agenda item, the vote on it with
 * dissent, and its issue to the CBM. The voting threshold is a share of the
 * members PRESENT and eligible (not of the committee size, which is the
 * quorum base). Each decision stores the configuration it was judged under.
 */
@Service
public class DecisionService {

    private static final Set<String> CHOICES = Set.of("FOR", "AGAINST", "ABSTAIN");

    public record DraftRequest(String recommendationCode, String recommendationText, String recommendedBidderRef,
                               BigDecimal contractValue, String currency, JsonNode structuredOutcome, Long version) {
    }

    public record VoteRequest(String choice, String dissentReason) {
    }

    public record Threshold(int present, int excluded, int eligible, int required, String rule) {
    }

    private final DecisionRepository decisions;
    private final VoteRepository votes;
    private final AgendaItemRepository items;
    private final ProcFileRepository files;
    private final FileStages stages;
    private final SessionAccess sessions;
    private final DeclarationService declarations;
    private final ConfigService config;
    private final StepUpVerifier stepUp;
    private final AuditService audit;
    private final Access access;
    private final JdbcTemplate jdbc;

    public DecisionService(DecisionRepository decisions, VoteRepository votes, AgendaItemRepository items,
                           ProcFileRepository files, FileStages stages, SessionAccess sessions,
                           DeclarationService declarations, ConfigService config, StepUpVerifier stepUp,
                           AuditService audit, Access access, JdbcTemplate jdbc) {
        this.decisions = decisions;
        this.votes = votes;
        this.items = items;
        this.files = files;
        this.stages = stages;
        this.sessions = sessions;
        this.declarations = declarations;
        this.config = config;
        this.stepUp = stepUp;
        this.audit = audit;
        this.access = access;
        this.jdbc = jdbc;
    }

    public Decision get(UUID id) {
        Decision d = decisions.findById(id).orElseThrow(() -> ApiException.notFound("Decision"));
        sessions.readable(d.sessionId);
        return d;
    }

    // ---------------------------------------------------------------- view

    public Map<String, Object> forItem(UUID agendaItemId) {
        AgendaItem item = declarations.item(agendaItemId);
        CommitteeSession s = sessions.readable(item.sessionId);
        Decision d = decisions.findByAgendaItemId(item.id).orElse(null);
        UUID me = access.userId();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("decision", d);
        out.put("votes", d == null ? List.of() : Rows.camel(jdbc.queryForList("""
                SELECT v.id, v.user_id, u.full_name, v.choice, v.dissent_reason, v.cast_at
                  FROM vote v JOIN app_user u ON u.id = v.user_id WHERE v.decision_id = ? ORDER BY v.cast_at
                """, d.id)));
        out.put("threshold", d != null && d.requiredVotes != null
                ? new Threshold(d.presentCount, d.presentCount - d.eligibleVoters, d.eligibleVoters, d.requiredVotes,
                config.fromStored(d.configSnapshot).getFraction(ConfigKeys.VOTING_RULE).toString())
                : threshold(s, item, d == null ? config.fromStored(s.configSnapshot) : config.fromStored(d.configSnapshot)));
        out.put("quorum", sessions.quorum(s));
        Vote mine = d == null ? null : votes.findByDecisionIdAndUserId(d.id, me).orElse(null);
        out.put("myVote", mine);
        out.put("canVote", d != null && "VOTING".equals(d.status) && mine == null && eligibleVoter(s, item, me));
        out.put("pendingDeclarations", declarations.pending(item));
        return out;
    }

    /** Live threshold from current attendance and declarations. */
    Threshold threshold(CommitteeSession s, AgendaItem item, ConfigSnapshot cfg) {
        Integer present = jdbc.queryForObject(
                "SELECT count(*) FROM attendance WHERE session_id = ? AND status = 'PRESENT'", Integer.class, s.id);
        Integer excluded = jdbc.queryForObject("""
                SELECT count(*) FROM coi_declaration d
                  JOIN attendance a ON a.session_id = d.session_id AND a.user_id = d.user_id AND a.status = 'PRESENT'
                 WHERE d.agenda_item_id = ? AND d.has_interest
                """, Integer.class, item.id);
        int p = present == null ? 0 : present;
        int x = excluded == null ? 0 : excluded;
        ConfigSnapshot.Fraction rule = cfg.getFraction(ConfigKeys.VOTING_RULE);
        return new Threshold(p, x, p - x, rule.ceilOf(p - x), rule.toString());
    }

    private boolean eligibleVoter(CommitteeSession s, AgendaItem item, UUID userId) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM attendance a
                 WHERE a.session_id = ? AND a.user_id = ? AND a.status = 'PRESENT'
                   AND NOT EXISTS (SELECT 1 FROM coi_declaration d
                                    WHERE d.agenda_item_id = ? AND d.user_id = a.user_id AND d.has_interest)
                """, Integer.class, s.id, userId, item.id);
        return n != null && n > 0;
    }

    // --------------------------------------------------------------- draft

    @Transactional
    public Decision draft(UUID agendaItemId, DraftRequest req) {
        AgendaItem item = declarations.item(agendaItemId);
        CommitteeSession s = sessions.forOfficer(item.sessionId);
        SessionAccess.requireOpen(s);
        if ("I".equals(item.itemType) || item.fileId == null) {
            throw ApiException.rule("decision.not_applicable",
                    "Administrative items cannot carry a procurement recommendation");
        }
        if (decisions.findByAgendaItemId(item.id).isPresent()) {
            throw ApiException.conflict("decision.exists", "This item already has a decision");
        }
        ConfigSnapshot cfg = config.snapshot();
        Decision d = new Decision();
        d.sessionId = s.id;
        d.agendaItemId = item.id;
        d.fileId = item.fileId;
        d.itemType = item.itemType;
        d.configSnapshot = cfg.json();
        d.createdBy = access.userId();
        apply(d, req, cfg);
        decisions.saveAndFlush(d);
        audit.created("decision", d.id, d);
        ProcFile f = files.findById(item.fileId).orElseThrow();
        if (FileStages.SCHEDULED.equals(f.stage) || FileStages.RECEIVED.equals(f.stage)) {
            stages.move(f, FileStages.UNDER_REVIEW, "Under review in session " + s.sessionNumber);
        }
        return d;
    }

    @Transactional
    public Decision update(UUID id, DraftRequest req) {
        Decision d = get(id);
        sessions.forOfficer(d.sessionId);
        if (!"DRAFT".equals(d.status)) {
            throw ApiException.rule("decision.not_draft", "Only a draft recommendation can be edited");
        }
        if (req.version() != null && !req.version().equals(d.version)) {
            throw ApiException.conflict("concurrent_modification", "Reload and try again");
        }
        var before = audit.snapshot(d);
        apply(d, req, config.fromStored(d.configSnapshot));
        decisions.saveAndFlush(d);
        audit.updated("decision", d.id, before, d);
        return d;
    }

    private void apply(Decision d, DraftRequest req, ConfigSnapshot cfg) {
        List<String> codes = new ArrayList<>();
        cfg.get(ConfigKeys.RECOMMENDATION_CODES).path(d.itemType).forEach(n -> codes.add(n.asText()));
        if (req.recommendationCode() == null || !codes.contains(req.recommendationCode())) {
            throw ApiException.badRequest("decision.invalid_code", "Choose one of the recommendations for this item type",
                    Map.of("allowed", codes));
        }
        if (req.recommendationText() == null || req.recommendationText().isBlank()) {
            throw ApiException.badRequest("decision.text_required", "State the recommendation and its grounds");
        }
        d.recommendationCode = req.recommendationCode();
        d.recommendationText = req.recommendationText().trim();
        d.recommendedBidderRef = req.recommendedBidderRef() == null || req.recommendedBidderRef().isBlank()
                ? null : req.recommendedBidderRef().trim();
        d.contractValue = req.contractValue();
        d.currency = req.currency() == null ? null : req.currency().toUpperCase();
        d.structuredOutcome = req.structuredOutcome();
        if ("A".equals(d.itemType) && "AWARD".equals(d.recommendationCode)
                && (d.recommendedBidderRef == null || d.contractValue == null || d.currency == null)) {
            throw ApiException.badRequest("decision.award_details_required",
                    "An award recommendation needs the bidder reference, contract value and currency");
        }
        if (d.contractValue != null && (d.currency == null || d.contractValue.signum() < 0)) {
            throw ApiException.badRequest("decision.invalid_value", "Give a non-negative value with its currency");
        }
    }

    // -------------------------------------------------------------- voting

    @Transactional
    public Decision openVoting(UUID id) {
        Decision d = get(id);
        CommitteeSession s = sessions.forOfficer(d.sessionId);
        if (!"DRAFT".equals(d.status)) {
            throw ApiException.rule("decision.not_draft", "Voting has already been opened");
        }
        if (!"IN_PROGRESS".equals(s.status)) {
            throw ApiException.rule("decision.session_not_in_progress", "Votes are taken while the session is in progress");
        }
        Quorum q = sessions.quorum(s);
        if (!q.met()) {
            throw ApiException.rule("decision.no_quorum", "Quorum is not met: " + q.needed() + " more member(s) needed",
                    Map.of("present", q.present(), "required", q.required(), "needed", q.needed()));
        }
        AgendaItem item = items.findById(d.agendaItemId).orElseThrow();
        List<Map<String, Object>> pending = declarations.pending(item);
        if (!pending.isEmpty()) {
            throw ApiException.rule("decision.declarations_pending",
                    "Every member present must declare any interest before the vote",
                    Map.of("members", pending.stream().map(m -> m.get("fullName")).toList()));
        }
        Threshold t = threshold(s, item, config.fromStored(d.configSnapshot));
        if (t.eligible() <= 0) {
            throw ApiException.rule("decision.no_eligible_voters", "No member present is eligible to vote on this file");
        }
        var before = audit.snapshot(d);
        d.presentCount = t.present();
        d.eligibleVoters = t.eligible();
        d.requiredVotes = t.required();
        d.status = "VOTING";
        d.votingOpenedAt = Instant.now();
        decisions.saveAndFlush(d);
        audit.record("OPEN_VOTING", "decision", d.id, before, d);
        return d;
    }

    @Transactional
    public Vote vote(UUID id, VoteRequest req) {
        Decision d = get(id);
        CommitteeSession s = sessions.readable(d.sessionId);
        if (!"VOTING".equals(d.status)) {
            throw ApiException.rule("vote.not_open", "Voting is not open on this recommendation");
        }
        if (req.choice() == null || !CHOICES.contains(req.choice())) {
            throw ApiException.badRequest("vote.invalid_choice", "Vote for, against or abstain");
        }
        if (!"FOR".equals(req.choice()) && (req.dissentReason() == null || req.dissentReason().isBlank())) {
            throw ApiException.badRequest("vote.dissent_reason_required",
                    "Record your reasons: they appear in the minutes and protect you from joint liability");
        }
        UUID me = access.userId();
        access.requireMember(s.committeeId);
        AgendaItem item = items.findById(d.agendaItemId).orElseThrow();
        if (!eligibleVoter(s, item, me)) {
            throw ApiException.rule("vote.not_eligible",
                    "Only members present without a declared interest in this file may vote");
        }
        if (votes.findByDecisionIdAndUserId(d.id, me).isPresent()) {
            throw ApiException.conflict("vote.already_cast", "You have already voted on this recommendation");
        }
        var verifiedAt = stepUp.requireRecent(access.principal(), "VOTE");
        UUID membershipId = jdbc.queryForObject(
                "SELECT membership_id FROM attendance WHERE session_id = ? AND user_id = ?", UUID.class, s.id, me);
        Vote v = new Vote();
        v.decisionId = d.id;
        v.membershipId = membershipId;
        v.userId = me;
        v.choice = req.choice();
        v.dissentReason = "FOR".equals(req.choice()) ? null : req.dissentReason().trim();
        v.stepUpVerifiedAt = verifiedAt;
        votes.saveAndFlush(v);
        // Tally on the decision row under optimistic locking: concurrent votes
        // serialise through its version column.
        switch (v.choice) {
            case "FOR" -> d.votesFor++;
            case "AGAINST" -> d.votesAgainst++;
            default -> d.votesAbstained++;
        }
        decisions.saveAndFlush(d);
        audit.record("VOTE", "vote", v.id, null, v);
        return v;
    }

    @Transactional
    public Decision closeVoting(UUID id) {
        Decision d = get(id);
        sessions.forOfficer(d.sessionId);
        if (!"VOTING".equals(d.status)) {
            throw ApiException.rule("vote.not_open", "Voting is not open on this recommendation");
        }
        var before = audit.snapshot(d);
        List<Vote> cast = votes.findByDecisionId(d.id);
        d.votesFor = (int) cast.stream().filter(v -> "FOR".equals(v.choice)).count();
        d.votesAgainst = (int) cast.stream().filter(v -> "AGAINST".equals(v.choice)).count();
        d.votesAbstained = (int) cast.stream().filter(v -> "ABSTAIN".equals(v.choice)).count();
        d.status = d.votesFor >= d.requiredVotes ? "ADOPTED" : "NOT_ADOPTED";
        d.votingClosedAt = Instant.now();
        decisions.saveAndFlush(d);
        audit.record("CLOSE_VOTING", "decision", d.id, before, d);
        AgendaItem item = items.findById(d.agendaItemId).orElseThrow();
        item.status = "DISCUSSED";
        items.saveAndFlush(item);
        return d;
    }

    @Transactional
    public Decision issue(UUID id) {
        Decision d = get(id);
        CommitteeSession s = sessions.readable(d.sessionId);
        access.requireChair(s.committeeId);
        if (!"ADOPTED".equals(d.status)) {
            throw ApiException.rule("decision.not_adopted", "Only an adopted recommendation can be issued");
        }
        requireBidderFaultReasoning(d);
        var verifiedAt = stepUp.requireRecent(access.principal(), "ISSUE_RECOMMENDATION");
        var before = audit.snapshot(d);
        d.status = "ISSUED";
        d.issuedAt = Instant.now();
        d.issuedBy = access.userId();
        d.issuedStepUpAt = verifiedAt;
        decisions.saveAndFlush(d);
        audit.record("ISSUE_RECOMMENDATION", "decision", d.id, before, d);
        stages.move(files.findById(d.fileId).orElseThrow(), FileStages.RECOMMENDATION_ISSUED,
                "Recommendation issued in session " + s.sessionNumber);
        return d;
    }

    /**
     * Type D on the bidder's-fault ground: delay penalties may apply instead,
     * so granting an extension needs the committee's recorded reasoning.
     * The ground list is read from the decision's own configuration snapshot.
     */
    private void requireBidderFaultReasoning(Decision d) {
        if (!"D".equals(d.itemType) || d.recommendationCode == null || !d.recommendationCode.startsWith("GRANT_")) {
            return;
        }
        var rows = jdbc.queryForList("""
                SELECT ground_code, penalties_considered, bidder_fault_reasoning
                  FROM extension_request WHERE file_id = ?
                """, d.fileId);
        if (rows.isEmpty()) {
            throw ApiException.rule("decision.extension_request_missing",
                    "Record the extension request before recommending an extension");
        }
        var row = rows.get(0);
        boolean bidderFault = false;
        for (var g : config.fromStored(d.configSnapshot).get(rw.gov.ptc.config.ConfigKeys.EXTENSION_GROUNDS)) {
            if (g.path("code").asText().equals(row.get("ground_code"))) {
                bidderFault = g.path("bidderFault").asBoolean(false);
            }
        }
        Object reasoning = row.get("bidder_fault_reasoning");
        if (bidderFault && (!Boolean.TRUE.equals(row.get("penalties_considered"))
                || reasoning == null || reasoning.toString().isBlank())) {
            throw ApiException.rule("extension.blocker.bidder_fault_reasoning",
                    "The delay is the bidder's fault: record that penalties were considered and why an extension is still recommended");
        }
    }

    @Transactional
    public Decision submitToCbm(UUID id) {
        Decision d = get(id);
        sessions.forOfficer(d.sessionId);
        if (!"ISSUED".equals(d.status)) {
            throw ApiException.rule("decision.not_issued", "Issue the recommendation first");
        }
        stages.move(files.findById(d.fileId).orElseThrow(), FileStages.SUBMITTED_TO_CBM, "Submitted to the CBM");
        audit.record("SUBMIT_TO_CBM", "decision", d.id, null, Map.of("fileId", d.fileId));
        return d;
    }
}
