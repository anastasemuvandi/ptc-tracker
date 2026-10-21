package rw.gov.ptc.session.service;

import rw.gov.ptc.session.dto.ApologyRequest;
import rw.gov.ptc.session.dto.Mark;
import rw.gov.ptc.session.entity.AbsenceApology;

import java.util.Map;
import java.util.UUID;

/**
 * Attendance marking and absence apologies — the only writes the client may
 * queue offline. Queued writes carry the time they were captured
 * (clientRecordedAt) and the version they were based on, so a stale queued
 * mark never silently overwrites a later one.
 */
public interface AttendanceService {

    Map<String, Object> mark(UUID sessionId, UUID membershipId, Mark req);

    /** A member's apology to the Chairperson and Secretary; timely if captured before the start. */
    AbsenceApology apologise(UUID sessionId, ApologyRequest req);
}
