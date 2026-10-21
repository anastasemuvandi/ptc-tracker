package rw.gov.ptc.request.dto;

import java.time.LocalDate;
import java.util.UUID;

/** What the officer adds when registering the request as a file. */
public record AcceptRequest(UUID committeeId, UUID contractId, UUID procurementOfficerId, LocalDate targetDate,
                            String title) {
}
