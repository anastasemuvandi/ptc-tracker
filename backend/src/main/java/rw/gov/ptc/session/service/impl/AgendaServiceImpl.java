package rw.gov.ptc.session.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.gov.ptc.audit.service.AuditService;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.file.component.FileStages;
import rw.gov.ptc.file.entity.ProcFile;
import rw.gov.ptc.file.repository.ProcFileRepository;
import rw.gov.ptc.session.component.SessionAccess;
import rw.gov.ptc.session.dto.AddItem;
import rw.gov.ptc.session.entity.AgendaActivity;
import rw.gov.ptc.session.entity.AgendaItem;
import rw.gov.ptc.session.entity.CommitteeSession;
import rw.gov.ptc.session.enums.ActivityStatus;
import rw.gov.ptc.session.repository.AgendaActivityRepository;
import rw.gov.ptc.session.repository.AgendaItemRepository;
import rw.gov.ptc.session.service.ActivityService;
import rw.gov.ptc.session.service.AgendaService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AgendaServiceImpl implements AgendaService {

    private static final Set<String> FILE_TYPES = Set.of("A", "B", "C", "D", "E", "F", "G", "H");
    private static final Set<String> ADMIN_KINDS = Set.of("ADOPT_MINUTES", "WORK_PLANNING", "MEMBER_INDUCTION");

    private final AgendaItemRepository items;
    private final SessionAccess sessions;
    private final ProcFileRepository files;
    private final FileStages stages;
    private final AuditService audit;
    private final rw.gov.ptc.decision.repository.DecisionRepository decisions;
    private final ActivityService activities;
    private final AgendaActivityRepository activityRepo;

    public AgendaServiceImpl(AgendaItemRepository items, SessionAccess sessions, ProcFileRepository files,
                         FileStages stages, AuditService audit, rw.gov.ptc.decision.repository.DecisionRepository decisions,
                         ActivityService activities, AgendaActivityRepository activityRepo) {
        this.activities = activities;
        this.activityRepo = activityRepo;
        this.items = items;
        this.sessions = sessions;
        this.files = files;
        this.stages = stages;
        this.audit = audit;
        this.decisions = decisions;
    }

    @Override
    @Transactional
    public AgendaItem add(UUID sessionId, AddItem req) {
        CommitteeSession s = sessions.forOfficer(sessionId);
        SessionAccess.requireOpen(s);
        AgendaItem item = insert(s, req);
        if (item.fileId != null) {
            ProcFile f = files.findById(item.fileId).orElseThrow();
            // Without named activities (e.g. an accepted tabling request), plan what is still outstanding.
            activities.plan(item, f, req.activities() == null ? activities.outstanding(f) : req.activities(), Map.of());
        }
        return item;
    }

    private AgendaItem insert(CommitteeSession s, AddItem req) {
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

    @Override
    @Transactional
    public List<AgendaActivity> carryOver(UUID sessionId, List<UUID> activityIds) {
        CommitteeSession s = sessions.forOfficer(sessionId);
        SessionAccess.requireOpen(s);
        Map<UUID, Map<String, Object>> candidates = new HashMap<>();
        for (Map<String, Object> c : activities.carryOverCandidates(sessionId)) {
            candidates.put((UUID) c.get("id"), c);
        }
        if (activityIds == null || activityIds.isEmpty() || !candidates.keySet().containsAll(activityIds)) {
            throw ApiException.rule("agenda.carry_over_invalid", "Choose activities still waiting to be carried over");
        }
        Map<UUID, Map<String, UUID>> byFile = new LinkedHashMap<>();
        for (UUID id : activityIds) {
            Map<String, Object> c = candidates.get(id);
            byFile.computeIfAbsent((UUID) c.get("fileId"), k -> new LinkedHashMap<>()).put((String) c.get("activityCode"), id);
        }
        List<AgendaActivity> out = new ArrayList<>();
        byFile.forEach((fileId, carried) -> {
            ProcFile f = files.findById(fileId).orElseThrow();
            AgendaItem item = items.findBySessionIdOrderByPositionAsc(s.id).stream()
                    .filter(i -> fileId.equals(i.fileId)).findFirst()
                    .orElseGet(() -> insert(s, new AddItem(fileId, f.itemType, null, null, null)));
            out.addAll(activities.plan(item, f, new ArrayList<>(carried.keySet()), carried));
        });
        return out;
    }

    @Override
    @Transactional
    public void remove(UUID sessionId, UUID itemId) {
        CommitteeSession s = sessions.forOfficer(sessionId);
        SessionAccess.requireOpen(s);
        AgendaItem item = items.findById(itemId).filter(i -> i.sessionId.equals(s.id))
                .orElseThrow(() -> ApiException.notFound("Agenda item"));
        if (decisions.findByAgendaItemId(item.id).isPresent()) {
            throw ApiException.rule("agenda.has_decision", "This item already carries a decision and cannot be removed");
        }
        List<AgendaActivity> planned = activityRepo.findByAgendaItemIdOrderByPositionAsc(item.id);
        if (planned.stream().anyMatch(a -> a.status != ActivityStatus.PLANNED)) {
            throw ApiException.rule("agenda.has_activity_record", "Activities of this item are already recorded");
        }
        planned.forEach(a -> audit.record("DELETE", "agenda_activity", a.id, a, null));
        activityRepo.deleteAll(planned);
        activityRepo.flush();
        items.delete(item);
        audit.record("DELETE", "agenda_item", item.id, item, null);
    }

    @Override
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
