package rw.gov.ptc.committee.dto;

import java.util.List;
import java.util.UUID;

public record Replacement(String scope, String reason, List<UUID> outgoingMembershipIds,
                          List<Appointment> appointments) {
}
