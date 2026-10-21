package rw.gov.ptc.minutes.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.common.Rows;
import rw.gov.ptc.common.Time;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds the structured content of a session's minutes from the recorded
 * proceedings. The structure adapts to each agenda item's type. Only
 * human-facing references (file numbers, session numbers, tender/contract
 * and bidder references) are included — never internal identifiers.
 *
 * <p>Vote thresholds are taken from the figures stored on each decision
 * when it was judged; configuration is never re-resolved here.
 */
@Component
public class MinutesContentBuilder {

    static final String DISCLAIMER =
            "Internal working document — not an official procurement record (system of record: Umucyo).";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
    private static final Map<String, Integer> ROLE_ORDER =
            Map.of("CHAIRPERSON", 0, "VICE_CHAIRPERSON", 1, "SECRETARY", 2, "MEMBER", 3);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public MinutesContentBuilder(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public ObjectNode build(UUID sessionId) {
        List<Map<String, Object>> sessions = jdbc.queryForList("""
                SELECT s.session_number, s.kind, s.scheduled_at, s.location, s.status, s.short_notice,
                       s.short_notice_reason, s.committee_size, s.committee_id, s.config_snapshot::text AS config_snapshot,
                       c.name AS committee_name, c.code AS committee_code,
                       i.name AS institution_name, i.abbreviation, i.minutes_letterhead
                  FROM committee_session s
                  JOIN committee c ON c.id = s.committee_id
                  JOIN institution i ON i.id = s.institution_id
                 WHERE s.id = ?
                """, sessionId);
        if (sessions.isEmpty()) {
            throw ApiException.notFound("Session");
        }
        Map<String, Object> s = sessions.get(0);
        ObjectNode root = mapper.createObjectNode();
        root.put("disclaimer", DISCLAIMER);

        Instant scheduled = ((java.sql.Timestamp) s.get("scheduled_at")).toInstant();
        var local = scheduled.atZone(Time.KIGALI);
        ObjectNode header = root.putObject("header");
        header.put("institutionName", (String) s.get("institution_name"));
        header.put("institutionAbbreviation", (String) s.get("abbreviation"));
        header.put("letterhead", (String) s.get("minutes_letterhead"));
        header.put("committeeName", (String) s.get("committee_name"));
        header.put("committeeCode", (String) s.get("committee_code"));
        header.put("sessionNumber", (String) s.get("session_number"));
        header.put("kind", (String) s.get("kind"));
        header.put("date", DATE.format(local));
        header.put("time", TIME.format(local));
        header.put("timeZone", "Africa/Kigali");
        header.put("location", (String) s.get("location"));
        header.put("status", (String) s.get("status"));
        header.put("shortNotice", (Boolean) s.get("short_notice"));
        header.put("shortNoticeReason", (String) s.get("short_notice_reason"));

        UUID committeeId = (UUID) s.get("committee_id");
        Map<String, String> names = new LinkedHashMap<>();
        ArrayNode attendance = root.putArray("attendance");
        int present = 0;
        for (Map<String, Object> m : jdbc.queryForList("""
                SELECT m.user_id, u.full_name, m.role, a.status AS attendance_status,
                       ap.kind AS apology_kind, ap.reason AS apology_reason, ap.timely
                  FROM committee_membership m
                  JOIN app_user u ON u.id = m.user_id
                  LEFT JOIN attendance a ON a.membership_id = m.id AND a.session_id = ?
                  LEFT JOIN absence_apology ap ON ap.session_id = ? AND ap.user_id = m.user_id
                 WHERE m.committee_id = ? AND (m.status = 'ACTIVE' OR a.id IS NOT NULL)
                """, sessionId, sessionId, committeeId).stream()
                .sorted((x, y) -> {
                    int r = Integer.compare(ROLE_ORDER.getOrDefault((String) x.get("role"), 9),
                            ROLE_ORDER.getOrDefault((String) y.get("role"), 9));
                    return r != 0 ? r : ((String) x.get("full_name")).compareTo((String) y.get("full_name"));
                }).toList()) {
            String status = (String) m.get("attendance_status");
            String apologyKind = (String) m.get("apology_kind");
            Boolean timely = (Boolean) m.get("timely");
            ObjectNode row = attendance.addObject();
            row.put("fullName", (String) m.get("full_name"));
            row.put("role", (String) m.get("role"));
            String effective = status != null ? status : (apologyKind != null ? apologyKind : "NOT_RECORDED");
            row.put("status", effective);
            boolean unnotified = !"PRESENT".equals(effective) && (apologyKind == null || !Boolean.TRUE.equals(timely));
            row.put("unnotifiedAbsence", unnotified);
            row.put("annotation", annotation(effective, apologyKind, timely, (String) m.get("apology_reason")));
            if ("PRESENT".equals(effective)) {
                present++;
                names.put(m.get("user_id").toString(), (String) m.get("full_name") + "|" + m.get("role"));
            }
        }

        ObjectNode quorum = root.putObject("quorum");
        int size = (Integer) s.get("committee_size");
        quorum.put("committeeSize", size);
        quorum.put("present", present);
        JsonNode rule = readJson((String) s.get("config_snapshot")).path("values").path("quorum.rule");
        if (rule.has("numerator") && rule.has("denominator")) {
            int required = (int) Math.ceil((double) size * rule.get("numerator").asInt() / rule.get("denominator").asInt());
            quorum.put("rule", rule.get("numerator").asInt() + "/" + rule.get("denominator").asInt());
            quorum.put("required", required);
            quorum.put("met", present >= required);
        }

        Set<String> dissenters = new HashSet<>();
        ArrayNode items = root.putArray("items");
        for (Map<String, Object> ai : jdbc.queryForList("""
                SELECT ai.id, ai.position, ai.item_type, ai.admin_kind, ai.admin_note, ai.status, ai.file_id,
                       f.file_number, f.title, f.tender_ref, f.procurement_type, f.procurement_method,
                       f.referral_ref, f.referral_reason, ct.contract_ref
                  FROM agenda_item ai
                  LEFT JOIN proc_file f ON f.id = ai.file_id
                  LEFT JOIN contract ct ON ct.id = f.contract_id
                 WHERE ai.session_id = ?
                 ORDER BY ai.position
                """, sessionId)) {
            items.add(item(ai, dissenters));
        }

        ArrayNode signatures = root.putArray("signatures");
        names.values().stream()
                .map(v -> v.split("\\|", 2))
                .sorted((a, b) -> {
                    int r = Integer.compare(ROLE_ORDER.getOrDefault(a[1], 9), ROLE_ORDER.getOrDefault(b[1], 9));
                    return r != 0 ? r : a[0].compareTo(b[0]);
                })
                .forEach(v -> {
                    ObjectNode sig = signatures.addObject();
                    sig.put("fullName", v[0]);
                    sig.put("role", v[1]);
                    sig.put("dissented", dissenters.contains(v[0]));
                });
        return root;
    }

    private ObjectNode item(Map<String, Object> ai, Set<String> dissenters) {
        UUID itemId = (UUID) ai.get("id");
        UUID fileId = (UUID) ai.get("file_id");
        String type = ((String) ai.get("item_type")).trim();
        ObjectNode item = mapper.createObjectNode();
        item.put("position", (Integer) ai.get("position"));
        item.put("itemType", type);
        item.put("fileNumber", (String) ai.get("file_number"));
        item.put("title", (String) ai.get("title"));
        item.put("tenderRef", (String) ai.get("tender_ref"));
        item.put("contractRef", (String) ai.get("contract_ref"));
        item.put("procurementType", (String) ai.get("procurement_type"));
        item.put("procurementMethod", (String) ai.get("procurement_method"));
        item.put("status", (String) ai.get("status"));

        ArrayNode declarations = item.putArray("declarations");
        for (Map<String, Object> d : jdbc.queryForList("""
                SELECT u.full_name, d.has_interest, d.interest_kind, d.details
                  FROM coi_declaration d JOIN app_user u ON u.id = d.user_id
                 WHERE d.agenda_item_id = ? ORDER BY u.full_name
                """, itemId)) {
            declarations.add(mapper.valueToTree(Rows.camel(d)));
        }

        item.set("activities", list("""
                SELECT a.activity_code AS code, a.status, a.note, fs.session_number AS carried_from_session
                  FROM agenda_activity a
                  LEFT JOIN agenda_activity f ON f.id = a.carried_from_id
                  LEFT JOIN committee_session fs ON fs.id = f.session_id
                 WHERE a.agenda_item_id = ? ORDER BY a.position
                """, itemId));

        ObjectNode analysis = item.putObject("analysis");
        switch (type) {
            case "A" -> evaluation(fileId, analysis);
            case "C" -> amendment(fileId, itemId, analysis);
            case "D" -> extension(fileId, analysis);
            case "B", "E", "F", "G" -> analysis.set("checklist", list("""
                    SELECT item_code, answer, note FROM checklist_response WHERE file_id = ? ORDER BY item_code
                    """, fileId));
            case "H" -> {
                analysis.put("referralRef", (String) ai.get("referral_ref"));
                analysis.put("referralReason", (String) ai.get("referral_reason"));
            }
            case "I" -> {
                analysis.put("adminKind", (String) ai.get("admin_kind"));
                analysis.put("adminNote", (String) ai.get("admin_note"));
            }
            default -> {
            }
        }

        List<Map<String, Object>> decisions = jdbc.queryForList("""
                SELECT id, recommendation_code, recommendation_text, recommended_bidder_ref, contract_value, currency,
                       structured_outcome::text AS structured_outcome, present_count, eligible_voters, required_votes,
                       votes_for, votes_against, votes_abstained, status
                  FROM decision WHERE agenda_item_id = ?
                """, itemId);
        if (!decisions.isEmpty()) {
            Map<String, Object> d = decisions.get(0);
            ObjectNode decision = item.putObject("decision");
            decision.put("recommendationCode", (String) d.get("recommendation_code"));
            decision.put("recommendationText", (String) d.get("recommendation_text"));
            decision.put("recommendedBidderRef", (String) d.get("recommended_bidder_ref"));
            if (d.get("contract_value") != null) {
                decision.put("contractValue", ((BigDecimal) d.get("contract_value")).toPlainString());
            }
            decision.put("currency", d.get("currency") == null ? null : ((String) d.get("currency")).trim());
            decision.set("structuredOutcome", readJson((String) d.get("structured_outcome")));
            putInt(decision, "presentCount", d.get("present_count"));
            putInt(decision, "eligibleVoters", d.get("eligible_voters"));
            putInt(decision, "requiredVotes", d.get("required_votes"));
            putInt(decision, "votesFor", d.get("votes_for"));
            putInt(decision, "votesAgainst", d.get("votes_against"));
            putInt(decision, "votesAbstained", d.get("votes_abstained"));
            decision.put("status", (String) d.get("status"));
            ArrayNode dissents = item.putArray("dissents");
            for (Map<String, Object> v : jdbc.queryForList("""
                    SELECT u.full_name, v.choice, v.dissent_reason
                      FROM vote v JOIN app_user u ON u.id = v.user_id
                     WHERE v.decision_id = ? AND v.choice <> 'FOR'
                     ORDER BY u.full_name
                    """, d.get("id"))) {
                ObjectNode dn = dissents.addObject();
                dn.put("fullName", (String) v.get("full_name"));
                dn.put("choice", (String) v.get("choice"));
                dn.put("reason", (String) v.get("dissent_reason"));
                dissenters.add((String) v.get("full_name"));
            }
        }
        return item;
    }

    private void evaluation(UUID fileId, ObjectNode a) {
        List<Map<String, Object>> ev = jdbc.queryForList(
                "SELECT criteria_locked_at, criteria_defect_findings FROM evaluation WHERE file_id = ?", fileId);
        if (!ev.isEmpty()) {
            a.put("criteriaLocked", ev.get(0).get("criteria_locked_at") != null);
            a.put("criteriaDefectFindings", (String) ev.get(0).get("criteria_defect_findings"));
        }
        ArrayNode stages = a.putArray("stages");
        for (Map<String, Object> st : jdbc.queryForList("""
                SELECT s.id, s.stage_code, s.sequence, s.status, s.outcome, s.data::text AS data
                  FROM evaluation_stage s WHERE s.file_id = ? ORDER BY s.sequence
                """, fileId)) {
            ObjectNode stage = stages.addObject();
            stage.put("code", (String) st.get("stage_code"));
            stage.put("sequence", (Integer) st.get("sequence"));
            stage.put("status", (String) st.get("status"));
            stage.put("outcome", (String) st.get("outcome"));
            stage.set("data", stripIds(readJson((String) st.get("data"))));
            List<Map<String, Object>> bidders = jdbc.queryForList("""
                    SELECT bidder_ref, result, failed_requirement, failure_reason, rank
                      FROM bidder_result WHERE stage_id = ? ORDER BY rank NULLS LAST, bidder_ref
                    """, st.get("id"));
            stage.put("evaluated", bidders.size());
            stage.put("passed", bidders.stream().filter(b -> "PASS".equals(b.get("result"))).count());
            ArrayNode failures = stage.putArray("failures");
            ArrayNode ranked = stage.putArray("rankedOrder");
            for (Map<String, Object> b : bidders) {
                if ("FAIL".equals(b.get("result"))) {
                    ObjectNode f = failures.addObject();
                    f.put("bidderRef", (String) b.get("bidder_ref"));
                    f.put("failedRequirement", (String) b.get("failed_requirement"));
                    f.put("reason", (String) b.get("failure_reason"));
                } else if (b.get("rank") != null) {
                    ranked.add((String) b.get("bidder_ref"));
                }
            }
        }
        a.set("abnormalPrices", list("""
                SELECT bidder_ref, direction, request_ref, request_date, explanation_summary, explanation_date,
                       analysis, doubt_lifted, decision, decision_reasons
                  FROM abnormal_price_check WHERE file_id = ? ORDER BY recorded_at
                """, fileId));
        a.set("cureRequests", list("""
                SELECT bidder_ref, outstanding_items, issued_on, working_days, deadline, supplied_summary,
                       supplied_on, outcome, outcome_note
                  FROM admin_cure_request WHERE file_id = ? ORDER BY issued_on
                """, fileId));
        a.set("dueDiligence", list("""
                SELECT bidder_ref, performed_by_name, performed_on, items_verified, result, disqualified,
                       disqualification_ground
                  FROM due_diligence WHERE file_id = ? ORDER BY performed_on
                """, fileId));
        a.set("clarifications", list("""
                SELECT bidder_ref, issued_on, subject, response_received_on, treatment, effect
                  FROM clarification WHERE file_id = ? ORDER BY issued_on
                """, fileId));
    }

    private void amendment(UUID fileId, UUID agendaItemId, ObjectNode a) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT ar.id, ar.contract_id, ar.requested_value, ar.currency, ar.description, ar.affects_substance,
                       ar.substance_justification, ar.deliverables_change, ar.perf_security_extend,
                       ar.perf_security_increase, ar.key_personnel_change, ar.replacement_equal_or_better,
                       ar.assessment_snapshot::text AS assessment_snapshot, ar.status,
                       c.contract_ref, c.original_value
                  FROM amendment_request ar JOIN contract c ON c.id = ar.contract_id
                 WHERE ar.file_id = ?
                """, fileId);
        if (rows.isEmpty()) {
            return;
        }
        Map<String, Object> r = rows.get(0);
        a.put("contractRef", (String) r.get("contract_ref"));
        a.put("description", (String) r.get("description"));
        String currency = ((String) r.get("currency")).trim();
        JsonNode stored = readJson((String) r.get("assessment_snapshot"));
        ObjectNode calc = a.putObject("calculation");
        if (stored.size() > 0) {
            calc.setAll((ObjectNode) stripIds(stored));
        } else {
            BigDecimal original = (BigDecimal) r.get("original_value");
            BigDecimal requested = (BigDecimal) r.get("requested_value");
            BigDecimal prior = jdbc.queryForObject("""
                    SELECT coalesce(sum(approved_value), 0) FROM amendment_request
                     WHERE contract_id = ? AND status = 'APPROVED' AND id <> ?
                    """, BigDecimal.class, r.get("contract_id"), r.get("id"));
            BigDecimal resulting = prior.add(requested);
            BigDecimal pct = resulting.multiply(BigDecimal.valueOf(100)).divide(original, 2, RoundingMode.HALF_UP);
            calc.put("currency", currency);
            calc.put("originalValue", original.toPlainString());
            calc.put("priorApprovedCumulative", prior.toPlainString());
            calc.put("requestedValue", requested.toPlainString());
            calc.put("resultingCumulative", resulting.toPlainString());
            calc.put("resultingPct", pct.toPlainString());
            // Ceiling as recorded on the decision's configuration snapshot.
            JsonNode values = decisionSnapshotValues(agendaItemId);
            JsonNode ceiling = values.path("amendment.ceilingPct");
            if (!ceiling.isMissingNode()) {
                BigDecimal ceilingPct = new BigDecimal(ceiling.asText());
                calc.put("ceilingPct", ceilingPct.toPlainString());
                JsonNode abs = values.path("amendment.absoluteCeilings").path(currency);
                boolean withinPct = pct.compareTo(ceilingPct) <= 0;
                boolean withinAbs = abs.isMissingNode() || resulting.compareTo(new BigDecimal(abs.asText())) <= 0;
                if (!abs.isMissingNode()) {
                    calc.put("absoluteCeiling", abs.asText());
                }
                ObjectNode quantitative = calc.putObject("quantitative");
                quantitative.put("withinPct", withinPct);
                quantitative.put("withinAbsolute", withinAbs);
                quantitative.put("passes", withinPct && withinAbs);
                calc.put("route", withinPct && withinAbs ? "ENTITY" : "ESCALATED");
            }
            ObjectNode qualitative = calc.putObject("qualitative");
            qualitative.put("answered", r.get("affects_substance") != null);
            if (r.get("affects_substance") != null) {
                qualitative.put("affectsSubstance", (Boolean) r.get("affects_substance"));
                qualitative.put("passes", !(Boolean) r.get("affects_substance"));
            }
            qualitative.put("justification", (String) r.get("substance_justification"));
        }
        ObjectNode prompts = a.putObject("prompts");
        for (String k : List.of("deliverables_change", "perf_security_extend", "perf_security_increase",
                "key_personnel_change", "replacement_equal_or_better")) {
            Object v = r.get(k);
            if (v != null) {
                prompts.put(Rows.camel(Map.of(k, v)).keySet().iterator().next(), (Boolean) v);
            }
        }
    }

    private void extension(UUID fileId, ObjectNode a) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.contract_ref, e.completion_date_before, e.requested_completion_date, e.ground_code,
                       e.ground_details, e.request_received_on, e.received_before_completion, e.penalties_considered,
                       e.bidder_fault_reasoning, e.has_cost_claim, e.force_majeure_start, e.force_majeure_end,
                       lf.file_number AS linked_amendment_file_number
                  FROM extension_request e
                  JOIN contract c ON c.id = e.contract_id
                  LEFT JOIN proc_file lf ON lf.id = e.linked_amendment_file_id
                 WHERE e.file_id = ?
                """, fileId);
        if (!rows.isEmpty()) {
            a.setAll((ObjectNode) mapper.valueToTree(Rows.camel(rows.get(0))));
        }
    }

    private JsonNode decisionSnapshotValues(UUID agendaItemId) {
        List<String> snaps = jdbc.queryForList(
                "SELECT config_snapshot::text FROM decision WHERE agenda_item_id = ?", String.class, agendaItemId);
        return snaps.isEmpty() ? mapper.createObjectNode() : readJson(snaps.get(0)).path("values");
    }

    private ArrayNode list(String sql, Object... args) {
        ArrayNode out = mapper.createArrayNode();
        for (Map<String, Object> row : jdbc.queryForList(sql, args)) {
            out.add(mapper.valueToTree(Rows.camel(row)));
        }
        return out;
    }

    /** Defensive: drop identifier-like fields from free-form stage data. */
    private JsonNode stripIds(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            ObjectNode copy = obj.deepCopy();
            List<String> drop = new java.util.ArrayList<>();
            copy.fieldNames().forEachRemaining(f -> {
                if (f.equals("id") || f.endsWith("Id") || f.endsWith("_id")) {
                    drop.add(f);
                }
            });
            copy.remove(drop);
            copy.fields().forEachRemaining(e -> e.setValue(stripIds(e.getValue())));
            return copy;
        }
        if (node instanceof ArrayNode arr) {
            ArrayNode copy = mapper.createArrayNode();
            arr.forEach(n -> copy.add(stripIds(n)));
            return copy;
        }
        return node;
    }

    private JsonNode readJson(String json) {
        if (json == null) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void putInt(ObjectNode n, String key, Object v) {
        if (v == null) {
            n.putNull(key);
        } else {
            n.put(key, ((Number) v).intValue());
        }
    }

    private static String annotation(String status, String apologyKind, Boolean timely, String reason) {
        String base = switch (status) {
            case "PRESENT" -> "Present";
            case "ABSENT_WITH_PERMISSION" -> "Absent with permission";
            case "ON_LEAVE" -> "On leave";
            case "ABSENT_UNJUSTIFIED" -> "Absent — unjustified";
            default -> "Absent — attendance not recorded";
        };
        if ("PRESENT".equals(status)) {
            return base;
        }
        if (apologyKind == null) {
            return base + "; no apology received (unnotified absence)";
        }
        String note = Boolean.TRUE.equals(timely) ? "apology received before the session"
                : "apology received after the session started";
        return base + "; " + note + (reason == null || reason.isBlank() ? "" : ": " + reason);
    }
}
