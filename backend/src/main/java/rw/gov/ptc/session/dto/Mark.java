package rw.gov.ptc.session.dto;

import java.time.Instant;

public record Mark(String status, Instant clientRecordedAt, Long baseVersion) {
}
