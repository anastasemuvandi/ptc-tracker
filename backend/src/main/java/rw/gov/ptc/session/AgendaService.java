package rw.gov.ptc.session;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.gov.ptc.audit.AuditService;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.file.FileStages;
import rw.gov.ptc.file.ProcFile;
import rw.gov.ptc.file.ProcFileRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Agenda scope is enforced, not advisory: every item is a procurement file
 * of a permitted type (A–H, with H only for files referred by the CBM) or a
 * type I administrative item about the committee's own business.
 */
@Service
public class AgendaService {

    private static final Set<String> FILE_TYPES = Set.of("A", "B", "C", "D", "E", "F", "G", "H");
    private static final Set<String> ADMIN_KINDS = Set.of("ADOPT_MINUTES", "WORK_PLANNING", "MEMBER_INDUCTION");

    public record AddItem(UUID fileId, String itemType, String adminKind, String adminNote) {
    }

    private final AgendaItemRepository items;
    private final SessionAccess sessions;
    private final ProcFileRepository files;
    private final FileStages stages;
    private final AuditService audit;
    private final rw.gov.ptc.decision.DecisionRepository decisions;

    public AgendaService(AgendaItemRepository items, SessionAccess sessions, ProcFileRepository files,
                         FileStages stages, AuditService audit, rw.gov.ptc.decision.DecisionRepository decisions) {
        this.items = items;
        this.sessions = sessions;
        this.files = files;
        this.stages = stages;
        this.audit = audit;
        this.decisions = decisions;
    }

    @Transactional
    public AgendaItem add(UUID sessionId, AddItem req) {
        CommitteeSession s = sessions.forOfficer(sessionId);
        SessionAccess.requireOpen(s);
        String type = req.itemType();
        AgendaItem item = new AgendaItem();
        item.sessionId = s.id;
        if ("I".equals(type)) {
            if (req.fileId() != null || req.adminKind() == null || !ADMIN_KINDS.contains(req.adminKind())) {
                throw ApiException.rule("agenda.out_of_scope",
                        "Administrative items are limited to adoption of minutes, work planning and member induction",
                        Map.of("allowed", ADMIN_KINDS));
            }
            item.itemType = "I";
            item.adminKind = req.adminKind();
            item.adminNote = req.adminNote();
        } else if (type != null && FILE_TYPES.contains(type)) {
            if (req.fileId() == null || req.adminKind() != null) {
                throw ApiException.rule("agenda.out_of_scope",
                        "An agenda item must be a procurement file of a permitted type; free-text items are not allowed");
            }
            ProcFile f = files.findById(req.fileId()).orElseThrow(() -> ApiException.notFound("File"));
            if (!f.committeeId.equals(s.committeeId)) {
                throw ApiException.rule("agenda.file_other_committee", "This file is assigned to another committee");
            }
            if (!f.itemType.equals(type)) {
                throw ApiException.rule("agenda.type_mismatch", "The item type must match the file's type",
                        Map.of("fileType", f.itemType, "itemType", type));
            }
            if (f.closedAt != null) {
                throw ApiException.rule("agenda.file_closed", "This file is closed");
            }
            boolean already = items.findBySessionIdOrderByPositionAsc(s.id).stream()
                    .anyMatch(i -> f.id.equals(i.fileId));
            if (already) {
                throw ApiException.conflict("agenda.duplicate", "This file is already on the agenda");
            }
            item.itemType = type;
            item.fileId = f.id;
            if (FileStages.RECEIVED.equals(f.stage) || FileStages.RETURNED_FOR_CORRECTION.equals(f.stage)) {
                stages.move(f, FileStages.SCHEDULED, "Tabled for session " + s.sessionNumber);
            }
        } else {
            throw ApiException.rule("agenda.out_of_scope", "Unknown agenda item type",
                    Map.of("itemType", String.valueOf(type)));
        }
        item.position = items.findBySessionIdOrderByPositionAsc(s.id).stream()
                .mapToInt(i -> i.position).max().orElse(0) + 1;
        items.saveAndFlush(item);
        audit.created("agenda_item", item.id, item);
        return item;
    }

    @Transactional
    public void remove(UUID sessionId, UUID itemId) {
        CommitteeSession s = sessions.forOfficer(sessionId);
        SessionAccess.requireOpen(s);
        AgendaItem item = items.findById(itemId).filter(i -> i.sessionId.equals(s.id))
                .orElseThrow(() -> ApiException.notFound("Agenda item"));
        if (decisions.findByAgendaItemId(item.id).isPresent()) {
            throw ApiException.rule("agenda.has_decision", "This item already carries a decision and cannot be removed");
        }
        items.delete(item);
        audit.record("DELETE", "agenda_item", item.id, item, null);
    }

    @Transactional
    public List<AgendaItem> reorder(UUID sessionId, List<UUID> order) {
        CommitteeSession s = sessions.forOfficer(sessionId);
        List<AgendaItem> current = items.findBySessionIdOrderByPositionAsc(s.id);
        Set<UUID> ids = new HashSet<>(current.stream().map(i -> i.id).toList());
        if (order == null || order.size() != ids.size() || !ids.equals(new HashSet<>(order))) {
            throw ApiException.badRequest("agenda.invalid_order", "The order must list every agenda item once");
        }
        for (AgendaItem i : current) {
            i.position = order.indexOf(i.id) + 1;
        }
        items.saveAllAndFlush(current);
        audit.record("REORDER", "committee_session", s.id, null, Map.of("order", order));
        return items.findBySessionIdOrderByPositionAsc(s.id);
    }
}
