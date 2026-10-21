package rw.gov.ptc.notification.dto;

import java.util.UUID;

public record Recipient(UUID userId, String email, String fullName, String locale) {
}
