package rw.gov.ptc.committee.dto;

import java.time.LocalDate;
import java.util.UUID;

public record Appointment(UUID userId, String role, LocalDate termStart, LocalDate termEnd,
                          String appointmentRef, UUID renewMembershipId) {
}
