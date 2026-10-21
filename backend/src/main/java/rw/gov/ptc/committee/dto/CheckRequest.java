package rw.gov.ptc.committee.dto;

import java.util.List;
import java.util.UUID;

public record CheckRequest(List<UUID> outgoingMembershipIds, List<Appointment> appointments,
                           UUID userId, String role) {
}
