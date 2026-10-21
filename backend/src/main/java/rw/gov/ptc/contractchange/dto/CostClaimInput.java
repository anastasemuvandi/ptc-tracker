package rw.gov.ptc.contractchange.dto;

import java.math.BigDecimal;

public record CostClaimInput(BigDecimal requestedValue, String description) {
}
