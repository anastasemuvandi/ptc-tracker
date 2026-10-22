package rw.gov.ptc.session.dto;

import java.util.List;
import java.util.UUID;

/** A submitted request accepted onto the agenda when the session is scheduled. */
public record PlannedRequest(UUID requestId, UUID contractId, List<String> activities) {
}
