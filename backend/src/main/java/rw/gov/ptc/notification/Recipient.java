package rw.gov.ptc.notification;

import java.util.UUID;

public record Recipient(UUID userId, String email, String fullName, String locale) {
}
