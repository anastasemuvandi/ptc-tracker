package rw.gov.ptc.evaluation.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record StageUpdate(JsonNode data, String outcome, Long version) {
}
