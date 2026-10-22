package rw.gov.ptc.request.dto;

import java.util.List;
import java.util.UUID;

/** Accept a submitted request straight onto a session agenda, with the activities for that sitting. */
public record ScheduleRequest(UUID sessionId, UUID contractId, UUID procurementOfficerId, List<String> activities) {
}
