package rw.gov.ptc.file.dto;

import java.util.UUID;

public record Filter(UUID committeeId, String type, UUID officerId, String stage, Boolean overdue,
                     String procurementType, UUID contractId) {
}
