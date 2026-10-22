package rw.gov.ptc.session.service.impl;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.gov.ptc.audit.service.AuditService;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.common.Enums;
import rw.gov.ptc.common.Rows;
import rw.gov.ptc.config.component.ConfigKeys;
import rw.gov.ptc.config.service.ConfigService;
import rw.gov.ptc.decision.enums.DecisionStatus;
import rw.gov.ptc.decision.repository.DecisionRepository;
import rw.gov.ptc.evaluation.component.StageCompletedEvent;
import rw.gov.ptc.evaluation.service.EvaluationService;
import rw.gov.ptc.file.entity.ProcFile;
import rw.gov.ptc.file.repository.ProcFileRepository;
import rw.gov.ptc.security.Access;
import rw.gov.ptc.session.component.SessionAccess;
import rw.gov.ptc.session.dto.RecordActivity;
import rw.gov.ptc.session.entity.AgendaActivity;
import rw.gov.ptc.session.entity.AgendaItem;
import rw.gov.ptc.session.entity.CommitteeSession;
import rw.gov.ptc.session.enums.ActivityStatus;
import rw.gov.ptc.session.enums.SessionStatus;
import rw.gov.ptc.session.repository.AgendaActivityRepository;
import rw.gov.ptc.session.repository.CommitteeSessionRepository;
import rw.gov.ptc.session.service.ActivityService;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class ActivityServiceImpl implements ActivityService {

    /** Completed by the adoption of the recommendation, not by hand. */
    private static final Set<String> RECOMMENDATION_CODES = Set.of("RECOMMENDATION", "A6");

    private final AgendaActivityRepository activities;
    private final CommitteeSessionRepository sessionRepo;
    private final SessionAccess sessions;
    private final DecisionRepository decisions;
    private final ProcFileRepository files;
    private final EvaluationService evaluation;
    private final ConfigService config;
    private final AuditService audit;
    private final Access access;
    private final JdbcTemplate jdbc;

    public ActivityServiceImpl(AgendaActivityRepository activities, CommitteeSessionRepository sessionRepo,
                               SessionAccess sessions, DecisionRepository decisions, ProcFileRepository files,
                               EvaluationService evaluation, ConfigService config, AuditService audit, Access access,
                               JdbcTemplate jdbc) {
        this.files = files;
        this.activities = activities;
        this.sessionRepo = sessionRepo;
        this.sessions = sessions;
        this.decisions = decisions;
        this.evaluation = evaluation;
        this.config = config;
        this.audit = audit;
        this.access = access;
        this.jdbc = jdbc;
    }

    @Override
    public List<String> allowed(String itemType) {
        List<String> out = new ArrayList<>();
        config.snapshot().get(ConfigKeys.AGENDA_ACTIVITIES).path(itemType).forEach(n -> out.add(n.asText()));
        return out;
    }

    /** Evaluation stage codes for type A files, which link activities to the evaluation. */
    private Set<String> stageCodes() {
        Set<String> out = new HashSet<>();
        config.snapshot().get(ConfigKeys.EVALUATION_STAGES).forEach(s -> out.add(s.path("code").asText()));
        return out;
    }

    private boolean linked(String itemType, String code) {
        return "A".equals(itemType) && !RECOMMENDATION_CODES.contains(code) && stageCodes().contains(code);
    }

    private Set<String> completedCodes(ProcFile f) {
        Set<String> done = new HashSet<>(jdbc.queryForList(
                "SELECT activity_code FROM agenda_activity WHERE file_id = ? AND status = 'COMPLETED'", String.class, f.id));
        if ("A".equals(f.itemType)) {
            done.addAll(jdbc.queryForList(
                    "SELECT stage_code FROM evaluation_stage WHERE file_id = ? AND status = 'COMPLETED'", String.class, f.id));
        }
        return done;
    }

    @Override
    public List<String> outstanding(ProcFile f) {
        Set<String> done = completedCodes(f);
        return allowed(f.itemType).stream().filter(c -> !done.contains(c)).toList();
    }

    @Override
    public List<AgendaActivity> plan(AgendaItem item, ProcFile f, List<String> codes, Map<String, UUID> carriedFrom) {
        if (codes == null || codes.isEmpty()) {
            throw ApiException.rule("agenda.activity_required", "Choose at least one activity for this file");
        }
        List<String> allowed = allowed(f.itemType);
        Set<String> done = completedCodes(f);
        List<AgendaActivity> existing = activities.findByAgendaItemIdOrderByPositionAsc(item.id);
        Set<String> planned = new HashSet<>(existing.stream().map(a -> a.activityCode).toList());
        List<AgendaActivity> out = new ArrayList<>();
        for (String code : codes) {
            if (!allowed.contains(code)) {
                throw ApiException.rule("agenda.activity_unknown", "This activity is not available for type " + f.itemType,
                        Map.of("activity", String.valueOf(code), "allowed", allowed));
            }
            if (done.contains(code)) {
                throw ApiException.rule("agenda.activity_done", "This activity is already completed for the file",
                        Map.of("activity", code));
            }
            if (!planned.add(code)) {
                continue;
            }
            AgendaActivity a = new AgendaActivity();
            a.sessionId = item.sessionId;
            a.agendaItemId = item.id;
            a.fileId = f.id;
            a.activityCode = code;
            a.position = allowed.indexOf(code) + 1;
            a.carriedFromId = carriedFrom.get(code);
            activities.saveAndFlush(a);
            audit.created("agenda_activity", a.id, a);
            out.add(a);
        }
        return out;
    }

    @Override
    public Map<String, Object> options(UUID sessionId, UUID fileId) {
        CommitteeSession s = sessions.forOfficer(sessionId);
        ProcFile f = files.findById(fileId).filter(x -> x.committeeId.equals(s.committeeId))
                .orElseThrow(() -> ApiException.notFound("File"));
        return Map.of("allowed", allowed(f.itemType), "outstanding", outstanding(f));
    }

    @Override
    public Map<String, Object> options(UUID fileId) {
        ProcFile f = files.findById(fileId).orElseThrow(() -> ApiException.notFound("File"));
        access.requireOfficer(f.committeeId);
        return Map.of("allowed", allowed(f.itemType), "outstanding", outstanding(f));
    }

    @Override
    @Transactional
    public AgendaActivity record(UUID sessionId, UUID activityId, RecordActivity req) {
        CommitteeSession s = sessions.forOfficer(sessionId);
        if (s.status != SessionStatus.IN_PROGRESS) {
            throw ApiException.rule("activity.session_not_in_progress", "Activities are recorded while the session is in progress");
        }
        AgendaActivity a = activities.findById(activityId).filter(x -> x.sessionId.equals(s.id))
                .orElseThrow(() -> ApiException.notFound("Activity"));
        ActivityStatus status = Enums.parse(ActivityStatus.class, req.status());
        if (status == null || status == ActivityStatus.PLANNED) {
            throw ApiException.badRequest("activity.invalid_status", "Record the activity as completed or not completed");
        }
        String note = req.note() == null || req.note().isBlank() ? null : req.note().trim();
        String itemType = jdbc.queryForObject("SELECT item_type FROM proc_file WHERE id = ?", String.class, a.fileId).trim();
        boolean stageDone = linked(itemType, a.activityCode) && jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM evaluation_stage WHERE file_id = ? AND stage_code = ? AND status = 'COMPLETED')",
                Boolean.class, a.fileId, a.activityCode);
        if (status == ActivityStatus.NOT_COMPLETED) {
            if (note == null) {
                throw ApiException.badRequest("activity.reason_required", "Say why the activity was not completed");
            }
            if (stageDone) {
                throw ApiException.rule("activity.stage_completed", "This evaluation stage is already completed");
            }
        } else if (RECOMMENDATION_CODES.contains(a.activityCode)) {
            boolean adopted = decisions.findByAgendaItemId(a.agendaItemId)
                    .map(d -> d.status == DecisionStatus.ADOPTED || d.status == DecisionStatus.ISSUED)
                    .orElse(false);
            if (!adopted) {
                throw ApiException.rule("activity.recommendation_not_adopted",
                        "The recommendation activity completes when the committee adopts a recommendation by vote");
            }
        } else if (linked(itemType, a.activityCode) && !stageDone) {
            // Completing the activity completes the evaluation stage, with its own checks.
            evaluation.completeStage(a.fileId, a.activityCode, note);
        }
        mark(a, status, note);
        return a;
    }

    private void mark(AgendaActivity a, ActivityStatus status, String note) {
        var before = audit.snapshot(a);
        a.status = status;
        a.note = note;
        a.recordedAt = Instant.now();
        a.recordedBy = access.userId();
        activities.saveAndFlush(a);
        audit.record("RECORD_ACTIVITY", "agenda_activity", a.id, before, a);
    }

    /** A stage completed in the evaluation completes the same activity in a session under way. */
    @EventListener
    public void onStageCompleted(StageCompletedEvent e) {
        for (AgendaActivity a : activities.findByFileIdAndActivityCodeAndStatus(e.fileId(), e.stageCode(), ActivityStatus.PLANNED)) {
            if (sessionRepo.findById(a.sessionId).map(s -> s.status == SessionStatus.IN_PROGRESS).orElse(false)) {
                mark(a, ActivityStatus.COMPLETED, a.note);
            }
        }
    }

    @Override
    public void votingClosed(UUID agendaItemId, boolean adopted) {
        if (!adopted) {
            return;
        }
        for (AgendaActivity a : activities.findByAgendaItemIdOrderByPositionAsc(agendaItemId)) {
            if (a.status == ActivityStatus.PLANNED && RECOMMENDATION_CODES.contains(a.activityCode)) {
                mark(a, ActivityStatus.COMPLETED, a.note);
            }
        }
    }

    @Override
    public List<Map<String, Object>> carryOverCandidates(UUID sessionId) {
        CommitteeSession s = sessions.readable(sessionId);
        return Rows.camel(jdbc.queryForList("""
                SELECT a.id, a.activity_code, a.note, a.file_id, p.file_number, p.title, p.item_type,
                       cs.session_number, cs.scheduled_at
                  FROM agenda_activity a
                  JOIN committee_session cs ON cs.id = a.session_id
                  JOIN proc_file p ON p.id = a.file_id
                 WHERE cs.committee_id = ? AND cs.id <> ? AND cs.status = 'HELD'
                   AND a.status = 'NOT_COMPLETED' AND p.closed_at IS NULL
                   AND NOT EXISTS (SELECT 1 FROM agenda_activity n WHERE n.carried_from_id = a.id)
                   AND NOT EXISTS (SELECT 1 FROM agenda_activity d WHERE d.file_id = a.file_id
                                     AND d.activity_code = a.activity_code AND d.status = 'COMPLETED')
                 ORDER BY cs.scheduled_at, p.file_number, a.position
                """, s.committeeId, s.id));
    }

    @Override
    public List<Map<String, Object>> forSession(UUID sessionId) {
        return Rows.camel(jdbc.queryForList("""
                SELECT a.id, a.agenda_item_id, a.activity_code, a.status, a.note, a.recorded_at,
                       u.full_name AS recorded_by_name, a.carried_from_id, fs.session_number AS carried_from_session
                  FROM agenda_activity a
                  LEFT JOIN app_user u ON u.id = a.recorded_by
                  LEFT JOIN agenda_activity f ON f.id = a.carried_from_id
                  LEFT JOIN committee_session fs ON fs.id = f.session_id
                 WHERE a.session_id = ? ORDER BY a.position
                """, sessionId));
    }
}
