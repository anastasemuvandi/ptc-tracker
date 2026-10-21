package rw.gov.ptc.session.dto;

import java.util.List;
import java.util.UUID;

public record CarryOver(List<UUID> activityIds) {
}
