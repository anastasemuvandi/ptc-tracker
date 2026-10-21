package rw.gov.ptc.session.dto;

import java.time.Instant;

public record ApologyRequest(String kind, String reason, Instant clientRecordedAt) {
}
