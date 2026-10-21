package rw.gov.ptc.file.dto;

import java.time.LocalDate;
import java.util.UUID;

public record FileRequest(UUID committeeId, String itemType, String tenderRef, UUID contractId, String title,
                          String procurementType, String procurementMethod, UUID procurementOfficerId,
                          LocalDate dateReceived, LocalDate targetDate, String referralRef, String referralReason,
                          LocalDate bidValidityExpiry, Long version) {
}
