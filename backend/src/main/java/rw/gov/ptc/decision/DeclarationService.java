package rw.gov.ptc.decision;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.gov.ptc.audit.AuditService;
import rw.gov.ptc.auth.api.StepUpVerifier;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.common.Rows;
import rw.gov.ptc.common.Time;
import rw.gov.ptc.security.Access;
import rw.gov.ptc.session.AgendaItem;
import rw.gov.ptc.session.AgendaItemRepository;
import rw.gov.ptc.session.CommitteeSession;
import rw.gov.ptc.session.SessionAccess;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Conflict-of-interest declarations: before a file is opened, each attending
 * member declares any direct or indirect interest. A member with an interest
 * is excluded from that file's discussion and vote.
 */
@Service
public class DeclarationService {

    public record DeclarationRequest(Boolean hasInterest, String interestKind, String details) {
    }

    private final CoiDeclarationRepository declarations;
    private final AgendaItemRepository items;
    private final DecisionRepository decisions;
    private final SessionAccess sessions;
    private final StepUpVerifier stepUp;
    private final AuditService audit;
    private final Access access;
    private final JdbcTemplate jdbc;

    public DeclarationService(CoiDeclarationRepository declarations, AgendaItemRepository items,
                              DecisionRepository decisions, SessionAccess sessions, StepUpVerifier stepUp,
                              AuditService audit, Access access, JdbcTemplate jdbc) {
        this.declarations = declarations;
        this.items = items;
        this.decisions = decisions;
        this.sessions = sessions;
        this.stepUp = stepUp;
        this.audit = audit;
        this.access = access;
        this.jdbc = jdbc;
    }

    AgendaItem item(UUID agendaItemId) {
        AgendaItem item = items.findById(agendaItemId).orElseThrow(() -> ApiException.notFound("Agenda item"));
        sessions.readable(item.sessionId);
        return item;
    }

    public Map<String, Object> list(UUID agendaItemId) {
        AgendaItem item = item(agendaItemId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("declarations", Rows.camel(jdbc.queryForList("""
                SELECT d.id, d.user_id, u.full_name, d.has_interest, d.interest_kind, d.details, d.declared_at
                  FROM coi_declaration d JOIN app_user u ON u.id = d.user_id
                 WHERE d.agenda_item_id = ? ORDER BY u.full_name
                """, item.id)));
        out.put("pendingMembers", pending(item));
        return out;
    }

    /** Present members who have not yet declared on this item. */
    public List<Map<String, Object>> pending(AgendaItem item) {
        return Rows.camel(jdbc.queryForList("""
                SELECT a.membership_id, a.user_id, u.full_name
                  FROM attendance a JOIN app_user u ON u.id = a.user_id
                 WHERE a.session_id = ? AND a.status = 'PRESENT'
                   AND NOT EXISTS (SELECT 1 FROM coi_declaration d WHERE d.agenda_item_id = ? AND d.user_id = a.user_id)
                 ORDER BY u.full_name
                """, item.sessionId, item.id));
    }

    @Transactional
    public CoiDeclaration declare(UUID agendaItemId, DeclarationRequest req) {
        AgendaItem item = item(agendaItemId);
        CommitteeSession s = sessions.readable(item.sessionId);
        SessionAccess.requireOpen(s);
        if (item.fileId == null) {
            throw ApiException.rule("coi.not_applicable", "Declarations are made on procurement files");
        }
        UUID me = access.userId();
        access.requireMember(s.committeeId);
        Integer present = jdbc.queryForObject(
                "SELECT count(*) FROM attendance WHERE session_id = ? AND user_id = ? AND status = 'PRESENT'",
                Integer.class, s.id, me);
        if (present == null || present == 0) {
            throw ApiException.rule("coi.not_present", "Only members marked present declare on agenda files");
        }
        decisions.findByAgendaItemId(item.id).filter(d -> !"DRAFT".equals(d.status)).ifPresent(d -> {
            throw ApiException.rule("coi.voting_started", "Voting on this item has started; declarations are closed");
        });
        if (declarations.findByAgendaItemIdAndUserId(item.id, me).isPresent()) {
            throw ApiException.conflict("coi.already_declared", "You have already declared on this item");
        }
        if (req.hasInterest() == null) {
            throw ApiException.badRequest("coi.answer_required", "State whether you have an interest");
        }
        if (req.hasInterest() && (req.interestKind() == null || !Set.of("DIRECT", "INDIRECT").contains(req.interestKind())
                || req.details() == null || req.details().isBlank())) {
            throw ApiException.badRequest("coi.details_required", "Say whether the interest is direct or indirect and describe it");
        }
        // Legally significant: individually attributable, recent re-authentication.
        var verifiedAt = stepUp.requireRecent(access.principal(), "COI_DECLARATION");
        CoiDeclaration d = new CoiDeclaration();
        d.sessionId = s.id;
        d.agendaItemId = item.id;
        d.fileId = item.fileId;
        d.userId = me;
        d.hasInterest = req.hasInterest();
        d.interestKind = req.hasInterest() ? req.interestKind() : null;
        d.details = req.hasInterest() ? req.details().trim() : null;
        d.stepUpVerifiedAt = verifiedAt;
        declarations.saveAndFlush(d);
        audit.record("DECLARE_INTEREST", "coi_declaration", d.id, null, d);
        return d;
    }

    /** CSV of declarations, by business references only. */
    public String exportCsv(UUID sessionId) {
        CommitteeSession s = sessions.readable(sessionId);
        StringBuilder csv = new StringBuilder("session,file,member,has_interest,kind,details,declared_at\n");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(Time.KIGALI);
        jdbc.query("""
                SELECT s.session_number, p.file_number, u.full_name, d.has_interest, d.interest_kind, d.details, d.declared_at
                  FROM coi_declaration d JOIN committee_session s ON s.id = d.session_id
                  JOIN proc_file p ON p.id = d.file_id JOIN app_user u ON u.id = d.user_id
                 WHERE d.session_id = ? ORDER BY p.file_number, u.full_name
                """, rs -> {
            csv.append(cell(rs.getString(1))).append(',').append(cell(rs.getString(2))).append(',')
                    .append(cell(rs.getString(3))).append(',').append(rs.getBoolean(4) ? "yes" : "no").append(',')
                    .append(cell(rs.getString(5))).append(',').append(cell(rs.getString(6))).append(',')
                    .append(fmt.format(rs.getTimestamp(7).toInstant())).append('\n');
        }, s.id);
        return csv.toString();
    }

    static String cell(String v) {
        if (v == null) {
            return "";
        }
        String safe = v.startsWith("=") || v.startsWith("+") || v.startsWith("-") || v.startsWith("@") ? "'" + v : v;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
