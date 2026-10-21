package rw.gov.ptc.minutes.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.gov.ptc.audit.service.AuditService;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.common.Ids;
import rw.gov.ptc.common.Rows;
import rw.gov.ptc.institution.entity.Institution;
import rw.gov.ptc.institution.repository.InstitutionRepository;
import rw.gov.ptc.minutes.component.MinutesContentBuilder;
import rw.gov.ptc.minutes.component.MinutesDocxRenderer;
import rw.gov.ptc.minutes.dto.Download;
import rw.gov.ptc.minutes.dto.SessionRef;
import rw.gov.ptc.minutes.entity.Minutes;
import rw.gov.ptc.minutes.enums.MinutesStatus;
import rw.gov.ptc.minutes.repository.MinutesRepository;
import rw.gov.ptc.minutes.service.MinutesService;
import rw.gov.ptc.notification.service.NotificationService;
import rw.gov.ptc.security.Access;
import rw.gov.ptc.tenancy.TenantContext;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class MinutesServiceImpl implements MinutesService {

    private final MinutesRepository minutes;
    private final MinutesContentBuilder builder;
    private final MinutesDocxRenderer renderer;
    private final InstitutionRepository institutions;
    private final NotificationService notifications;
    private final Access access;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public MinutesServiceImpl(MinutesRepository minutes, MinutesContentBuilder builder, MinutesDocxRenderer renderer,
                          InstitutionRepository institutions, NotificationService notifications, Access access,
                          AuditService audit, JdbcTemplate jdbc) {
        this.minutes = minutes;
        this.builder = builder;
        this.renderer = renderer;
        this.institutions = institutions;
        this.notifications = notifications;
        this.access = access;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Override
    public SessionRef session(UUID sessionId) {
        return jdbc.query("SELECT committee_id, session_number, status FROM committee_session WHERE id = ?",
                (rs, i) -> new SessionRef(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)), sessionId)
                .stream().findFirst().orElseThrow(() -> ApiException.notFound("Session"));
    }

    @Override
    @Transactional
    public Minutes generate(UUID sessionId) {
        SessionRef s = session(sessionId);
        access.requireOfficer(s.committeeId());
        if ("CANCELLED".equals(s.status())) {
            throw ApiException.rule("minutes.session_cancelled", "A cancelled session has no minutes");
        }
        Minutes m = minutes.findBySessionId(sessionId).orElse(null);
        JsonNode before = null;
        if (m == null) {
            m = new Minutes();
            m.sessionId = sessionId;
        } else {
            requireNotFinal(m);
            before = audit.snapshot(m);
        }
        m.content = builder.build(sessionId);
        m.status = MinutesStatus.DRAFT;
        m.generatedAt = Instant.now();
        m.generatedBy = access.userId();
        m = minutes.saveAndFlush(m);
        audit.record("MINUTES_GENERATED", "minutes", m.id, before == null ? null : Map.of("version", before.path("version")),
                Map.of("version", m.version, "session", s.sessionNumber()));
        return m;
    }

    @Override
    public Map<String, Object> view(UUID sessionId) {
        SessionRef s = session(sessionId);
        access.requireCanRead(s.committeeId());
        Minutes m = minutes.findBySessionId(sessionId).orElseThrow(() -> ApiException.notFound("Minutes"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("minutes", m);
        out.put("acknowledgements", Rows.camel(jdbc.queryForList("""
                SELECT a.user_id, u.full_name, a.acknowledged_at, a.comment
                  FROM minutes_acknowledgement a JOIN app_user u ON u.id = a.user_id
                 WHERE a.minutes_id = ? AND a.minutes_version = ? ORDER BY a.acknowledged_at
                """, m.id, m.version)));
        out.put("pendingAcknowledgements", Rows.camel(jdbc.queryForList("""
                SELECT u.id AS user_id, u.full_name FROM attendance a JOIN app_user u ON u.id = a.user_id
                 WHERE a.session_id = ? AND a.status = 'PRESENT'
                   AND NOT EXISTS (SELECT 1 FROM minutes_acknowledgement k
                                    WHERE k.minutes_id = ? AND k.user_id = a.user_id AND k.minutes_version = ?)
                 ORDER BY u.full_name
                """, sessionId, m.id, m.version)));
        out.put("canAcknowledge", m.status == MinutesStatus.IN_REVIEW && presentUserIds(sessionId).contains(access.userId()));
        return out;
    }

    @Override
    @Transactional
    public Minutes updateNotes(UUID minutesId, String notes, Long version) {
        Minutes m = load(minutesId);
        access.requireOfficer(session(m.sessionId).committeeId());
        requireNotFinal(m);
        checkVersion(m, version);
        var before = audit.snapshot(m);
        m.secretaryNotes = notes;
        m = minutes.saveAndFlush(m);
        audit.updated("minutes", m.id, Map.of("secretaryNotes", String.valueOf(before.path("secretaryNotes").asText(null))),
                Map.of("secretaryNotes", String.valueOf(notes), "version", m.version));
        return m;
    }

    @Override
    @Transactional
    public Minutes submitForReview(UUID minutesId) {
        Minutes m = load(minutesId);
        SessionRef s = session(m.sessionId);
        access.requireOfficer(s.committeeId());
        if (m.status != MinutesStatus.DRAFT) {
            throw ApiException.rule("minutes.not_draft", "Only draft minutes can be submitted for review");
        }
        m.status = MinutesStatus.IN_REVIEW;
        m = minutes.saveAndFlush(m);
        audit.record("MINUTES_SUBMITTED_FOR_REVIEW", "minutes", m.id, Map.of("status", "DRAFT"),
                Map.of("status", m.status, "version", m.version));
        for (UUID user : presentUserIds(m.sessionId)) {
            notifications.notify(user, "MINUTES_REVIEW", List.of(s.sessionNumber()), "/n/minutes/" + m.id + "?sessionId=" + m.sessionId,
                    "minutes", m.id, "minutes:" + m.id + ":v" + m.version);
        }
        return m;
    }

    @Override
    @Transactional
    public Map<String, Object> acknowledge(UUID minutesId, String comment) {
        Minutes m = load(minutesId);
        if (m.status != MinutesStatus.IN_REVIEW) {
            throw ApiException.rule("minutes.not_in_review", "These minutes are not open for acknowledgement");
        }
        UUID user = access.userId();
        if (!presentUserIds(m.sessionId).contains(user)) {
            throw ApiException.forbidden("minutes.not_present", "Only members present at the session acknowledge the minutes");
        }
        UUID id = Ids.newId();
        int inserted = jdbc.update("""
                INSERT INTO minutes_acknowledgement (id, institution_id, minutes_id, user_id, minutes_version, comment)
                VALUES (?, app_current_institution(), ?, ?, ?, ?)
                ON CONFLICT (minutes_id, user_id, minutes_version) DO NOTHING
                """, id, m.id, user, m.version, comment);
        if (inserted == 1) {
            audit.record("MINUTES_ACKNOWLEDGED", "minutes", m.id, null,
                    Map.of("version", m.version, "comment", comment == null ? "" : comment));
        }
        return Map.of("minutesId", m.id, "version", m.version);
    }

    @Override
    @Transactional
    public Minutes finalise(UUID minutesId) {
        Minutes m = load(minutesId);
        access.requireOfficer(session(m.sessionId).committeeId());
        if (m.status != MinutesStatus.IN_REVIEW) {
            throw ApiException.rule("minutes.not_in_review", "Submit the minutes for review first");
        }
        Integer pending = jdbc.queryForObject("""
                SELECT count(*) FROM attendance a
                 WHERE a.session_id = ? AND a.status = 'PRESENT'
                   AND NOT EXISTS (SELECT 1 FROM minutes_acknowledgement k
                                    WHERE k.minutes_id = ? AND k.user_id = a.user_id AND k.minutes_version = ?)
                """, Integer.class, m.sessionId, m.id, m.version);
        if (pending != null && pending > 0) {
            throw ApiException.rule("minutes.acknowledgements_pending",
                    "All members present must acknowledge the minutes before they are finalised",
                    Map.of("pending", pending));
        }
        m.status = MinutesStatus.FINAL;
        m.finalisedAt = Instant.now();
        m.finalisedBy = access.userId();
        m = minutes.saveAndFlush(m);
        audit.record("MINUTES_FINALISED", "minutes", m.id, Map.of("status", "IN_REVIEW"),
                Map.of("status", "FINAL", "version", m.version));
        return m;
    }

    @Override
    public Download docx(UUID minutesId) {
        Minutes m = load(minutesId);
        SessionRef s = session(m.sessionId);
        access.requireCanRead(s.committeeId());
        Institution inst = institutions.findById(TenantContext.requireInstitution())
                .orElseThrow(() -> ApiException.notFound("Institution"));
        JsonNode content = m.content;
        if (m.secretaryNotes != null && !m.secretaryNotes.isBlank()) {
            content = content.deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) content).put("secretaryNotes", m.secretaryNotes);
        }
        try {
            byte[] bytes = renderer.render(content, inst.minutesTemplateDocx,
                    new MinutesDocxRenderer.Style(inst.minutesFontFamily, inst.minutesFontSize, inst.minutesLetterhead));
            String name = "Minutes-" + s.sessionNumber().replaceAll("[^A-Za-z0-9._-]+", "-")
                    + (m.status == MinutesStatus.FINAL ? "" : "-DRAFT") + ".docx";
            return new Download(name, bytes);
        } catch (IOException e) {
            throw new IllegalStateException("Could not render minutes", e);
        }
    }

    private List<UUID> presentUserIds(UUID sessionId) {
        return jdbc.queryForList("SELECT user_id FROM attendance WHERE session_id = ? AND status = 'PRESENT'",
                UUID.class, sessionId);
    }

    private Minutes load(UUID id) {
        return minutes.findById(id).orElseThrow(() -> ApiException.notFound("Minutes"));
    }

    private static void requireNotFinal(Minutes m) {
        if (m.status == MinutesStatus.FINAL) {
            throw ApiException.conflict("minutes.final", "Final minutes cannot be changed");
        }
    }

    private static void checkVersion(Minutes m, Long version) {
        if (version != null && !version.equals(m.version)) {
            throw ApiException.conflict("concurrent_modification", "Reload and try again");
        }
    }
}
