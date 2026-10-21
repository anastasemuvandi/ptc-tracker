package rw.gov.ptc.minutes.dto;

import java.util.UUID;

public record SessionRef(UUID committeeId, String sessionNumber, String status) {
}
