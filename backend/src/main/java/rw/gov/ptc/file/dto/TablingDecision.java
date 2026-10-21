package rw.gov.ptc.file.dto;

import java.util.UUID;

public record TablingDecision(boolean accept, String note, UUID sessionId) {
}
