package rw.gov.ptc.session.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.gov.ptc.audit.service.AuditService;
import rw.gov.ptc.committee.entity.Membership;
import rw.gov.ptc.committee.enums.MembershipStatus;
import rw.gov.ptc.committee.repository.MembershipRepository;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.common.Enums;
import rw.gov.ptc.security.Access;
import rw.gov.ptc.session.component.SessionAccess;
import rw.gov.ptc.session.dto.ApologyRequest;
import rw.gov.ptc.session.dto.Mark;
import rw.gov.ptc.session.entity.AbsenceApology;
import rw.gov.ptc.session.entity.Attendance;
import rw.gov.ptc.session.entity.CommitteeSession;
import rw.gov.ptc.session.enums.ApologyKind;
import rw.gov.ptc.session.enums.AttendanceStatus;
import rw.gov.ptc.session.enums.SessionStatus;
import rw.gov.ptc.session.repository.AbsenceApologyRepository;
import rw.gov.ptc.session.repository.AttendanceRepository;
import rw.gov.ptc.session.service.AttendanceService;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AttendanceServiceImpl implements AttendanceService {


    private final AttendanceRepository attendance;
    private final AbsenceApologyRepository apologies;
    private final MembershipRepository memberships;
    private final SessionAccess sessions;
    private final AuditService audit;
    private final Access access;

    public AttendanceServiceImpl(AttendanceRepository attendance, AbsenceApologyRepository apologies,
                             MembershipRepository memberships, SessionAccess sessions, AuditService audit,
                             Access access) {
        this.attendance = attendance;
        this.apologies = apologies;
        this.memberships = memberships;
        this.sessions = sessions;
        this.audit = audit;
        this.access = access;
    }

    @Override
    @Transactional
    public Map<String, Object> mark(UUID sessionId, UUID membershipId, Mark req) {
        CommitteeSession s = sessions.forOfficer(sessionId);
        if (s.status == SessionStatus.CANCELLED) {
            throw ApiException.rule("session.closed", "This session was cancelled");
        }
        AttendanceStatus status = Enums.parse(AttendanceStatus.class, req.status());
        if (status == null) {
            throw ApiException.badRequest("attendance.invalid_status", "Unknown attendance status");
        }
        Membership m = memberships.findById(membershipId).filter(x -> x.committeeId.equals(s.committeeId))
                .orElseThrow(() -> ApiException.notFound("Membership"));
        Instant now = Instant.now();
        Instant clientAt = plausible(req.clientRecordedAt(), now);
        Attendance a = attendance.findBySessionIdAndMembershipId(s.id, m.id).orElse(null);
        Object before = null;
        if (a != null) {
            boolean changedSinceBase = req.baseVersion() == null || !req.baseVersion().equals(a.version);
            Instant serverAt = a.clientRecordedAt != null ? a.clientRecordedAt : a.recordedAt;
            if (changedSinceBase && serverAt.isAfter(clientAt)) {
                Map<String, Object> server = new LinkedHashMap<>();
                server.put("status", a.status);
                server.put("version", a.version);
                server.put("recordedAt", serverAt.toString());
                throw new ApiException(org.springframework.http.HttpStatus.CONFLICT, "attendance.conflict",
                        "Attendance for this member was changed more recently by someone else",
                        Map.of("server", server));
            }
            before = audit.snapshot(a);
        } else {
            a = new Attendance();
            a.sessionId = s.id;
            a.membershipId = m.id;
            a.userId = m.userId;
        }
        a.status = status;
        a.recordedBy = access.userId();
        a.recordedAt = now;
        a.clientRecordedAt = clientAt;
        attendance.saveAndFlush(a);
        audit.record(before == null ? "CREATE" : "UPDATE", "attendance", a.id, before, a);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("attendance", a);
        out.put("quorum", sessions.quorum(s));
        return out;
    }

    @Override
    @Transactional
    public AbsenceApology apologise(UUID sessionId, ApologyRequest req) {
        CommitteeSession s = sessions.readable(sessionId);
        SessionAccess.requireOpen(s);
        access.requireMember(s.committeeId);
        ApologyKind kind = req.kind() == null ? ApologyKind.ABSENT_WITH_PERMISSION : Enums.parse(ApologyKind.class, req.kind());
        if (kind == null) {
            throw ApiException.badRequest("apology.invalid_kind", "Choose absence with permission or leave");
        }
        if (req.reason() == null || req.reason().isBlank()) {
            throw ApiException.badRequest("apology.reason_required", "Give the reason for your absence");
        }
        UUID me = access.userId();
        Membership seat = memberships.findByUserIdAndStatus(me, MembershipStatus.ACTIVE).stream()
                .filter(x -> x.committeeId.equals(s.committeeId)).findFirst()
                .orElseThrow(() -> ApiException.forbidden("committee.member_required", "Only members may send apologies"));
        Instant now = Instant.now();
        Instant capturedAt = plausible(req.clientRecordedAt(), now);
        AbsenceApology ap = apologies.findBySessionIdAndUserId(s.id, me).orElse(null);
        Object before = ap == null ? null : audit.snapshot(ap);
        if (ap == null) {
            ap = new AbsenceApology();
            ap.sessionId = s.id;
            ap.userId = me;
        }
        ap.kind = kind;
        ap.reason = req.reason().trim();
        ap.submittedAt = now;
        ap.clientRecordedAt = capturedAt;
        ap.timely = capturedAt.isBefore(s.scheduledAt);
        apologies.saveAndFlush(ap);
        audit.record(before == null ? "CREATE" : "UPDATE", "absence_apology", ap.id, before, ap);

        // Pre-fill attendance so quorum risk is visible before the session.
        if (attendance.findBySessionIdAndMembershipId(s.id, seat.id).isEmpty()) {
            Attendance a = new Attendance();
            a.sessionId = s.id;
            a.membershipId = seat.id;
            a.userId = me;
            a.status = AttendanceStatus.valueOf(ap.kind.name());
            a.recordedBy = me;
            a.clientRecordedAt = capturedAt;
            attendance.saveAndFlush(a);
            audit.created("attendance", a.id, a);
        }
        return ap;
    }

    /**
     * Client capture times are accepted for ordering offline writes but
     * never in the future, and never more than a week old.
     */
    static Instant plausible(Instant clientAt, Instant now) {
        if (clientAt == null || clientAt.isAfter(now) || clientAt.isBefore(now.minus(Duration.ofDays(7)))) {
            return now;
        }
        return clientAt;
    }
}
