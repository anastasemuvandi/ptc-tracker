package rw.gov.ptc.session.service;

import rw.gov.ptc.session.dto.SessionRequest;
import rw.gov.ptc.session.entity.CommitteeSession;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Session scheduling. Only the Chairperson, Vice-Chairperson or Secretary
 * of the committee may create or modify a session; the CBM cannot.
 */
public interface SessionService {

    List<Map<String, Object>> list(UUID committeeId, LocalDate from, LocalDate to);

    CommitteeSession create(SessionRequest req);

    CommitteeSession update(UUID id, SessionRequest req);

    CommitteeSession changeStatus(UUID id, String status);

    Map<String, Object> view(UUID id);

    /** Members holding a seat on the session date, with attendance and apology. */
    List<Map<String, Object>> members(CommitteeSession s);
}
