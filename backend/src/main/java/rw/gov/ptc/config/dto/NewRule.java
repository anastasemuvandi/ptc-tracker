package rw.gov.ptc.config.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record NewRule(String key, JsonNode value, Instant effectiveFrom, String reason, String legalReference,
                      boolean platform) {
}
