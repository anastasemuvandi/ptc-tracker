package rw.gov.ptc.decision.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

public record DraftRequest(String recommendationCode, String recommendationText, String recommendedBidderRef,
                           BigDecimal contractValue, String currency, JsonNode structuredOutcome, Long version) {
}
