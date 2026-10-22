package rw.gov.ptc.session.dto;

import java.util.List;
import java.util.UUID;

/** A file of the committee put on the agenda when the session is scheduled. */
public record PlannedItem(UUID fileId, List<String> activities) {
}
