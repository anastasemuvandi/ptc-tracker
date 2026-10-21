package rw.gov.ptc.config.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record RuleRequest(String key, JsonNode value, Instant effectiveFrom, String reason,
                          String legalReference, String scope) {
}
