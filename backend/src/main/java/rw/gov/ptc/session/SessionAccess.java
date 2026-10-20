package rw.gov.ptc.session;

import org.springframework.stereotype.Component;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.config.ConfigKeys;
import rw.gov.ptc.config.ConfigService;
import rw.gov.ptc.security.Access;

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
        int present = (int) attendance.findBySessionId(s.id).stream().filter(a -> "PRESENT".equals(a.status)).count();
        return Quorum.of(config.fromStored(s.configSnapshot).getFraction(ConfigKeys.QUORUM_RULE), s.committeeSize, present);
    }

    public static void requireOpen(CommitteeSession s) {
        if ("HELD".equals(s.status) || "CANCELLED".equals(s.status)) {
            throw ApiException.rule("session.closed", "This session is " + s.status.toLowerCase());
        }
    }
}
