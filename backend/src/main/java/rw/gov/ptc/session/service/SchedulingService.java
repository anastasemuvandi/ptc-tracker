package rw.gov.ptc.session.service;

import rw.gov.ptc.session.dto.SessionRequest;
import rw.gov.ptc.session.entity.CommitteeSession;

/**
 * Schedules a session together with its first agenda: normal activities
 * (files of the committee) and submitted requests, which are accepted as
 * files of the committee. All or nothing.
 */
public interface SchedulingService {

    CommitteeSession schedule(SessionRequest req);
}
