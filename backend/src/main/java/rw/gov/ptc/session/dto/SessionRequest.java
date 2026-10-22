package rw.gov.ptc.session.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SessionRequest(UUID committeeId, String kind, Instant scheduledAt, String location,
                             String shortNoticeReason, Long version, List<PlannedItem> items,
                             List<PlannedRequest> requests) {
}
