package rw.gov.ptc.file.dto;

import java.util.UUID;

public record ReassignRequest(UUID toCommitteeId, String reason) {
}
