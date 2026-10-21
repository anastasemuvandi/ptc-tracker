package rw.gov.ptc.session.component;

import org.springframework.stereotype.Component;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.config.component.ConfigKeys;
import rw.gov.ptc.config.service.ConfigService;
import rw.gov.ptc.security.Access;
import rw.gov.ptc.session.dto.Quorum;
import rw.gov.ptc.session.entity.CommitteeSession;
import rw.gov.ptc.session.enums.AttendanceStatus;
import rw.gov.ptc.session.enums.SessionStatus;
import rw.gov.ptc.session.repository.AttendanceRepository;
import rw.gov.ptc.session.repository.CommitteeSessionRepository;

import java.util.UUID;

/** Loading sessions with read checks; shared by the session and decision packages. */
@Component
public class SessionAccess {

    private final CommitteeSessionRepository sessions;
    private final AttendanceRepository attendance;
    private final ConfigService config;
    private final Access access;

    public SessionAccess(CommitteeSessionRepository sessions, AttendanceRepository attendance, ConfigService config,
                         Access access) {
        this.sessions = sessions;
        this.attendance = attendance;
        this.config = config;
        this.access = access;
    }

    public CommitteeSession readable(UUID sessionId) {
        CommitteeSession s = sessions.findById(sessionId).orElseThrow(() -> ApiException.notFound("Session"));
        if (!access.canReadAllCommittees() && access.committeeRole(s.committeeId).isEmpty()) {
            throw ApiException.notFound("Session");
        }
        return s;
    }

    public CommitteeSession forOfficer(UUID sessionId) {
        CommitteeSession s = readable(sessionId);
        access.requireOfficer(s.committeeId);
        return s;
    }

    public Quorum quorum(CommitteeSession s) {
        int present = (int) attendance.findBySessionId(s.id).stream().filter(a -> a.status == AttendanceStatus.PRESENT).count();
        return Quorum.of(config.fromStored(s.configSnapshot).getFraction(ConfigKeys.QUORUM_RULE), s.committeeSize, present);
    }

    public static void requireOpen(CommitteeSession s) {
        if (s.status == SessionStatus.HELD || s.status == SessionStatus.CANCELLED) {
            throw ApiException.rule("session.closed", "This session is " + s.status.name().toLowerCase());
        }
    }
}
