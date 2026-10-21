package rw.gov.ptc.contractchange.dto;

import java.math.BigDecimal;

public record AmendmentInput(BigDecimal requestedValue, String currency, String description,
                             Boolean affectsSubstance, String substanceJustification, Boolean deliverablesChange,
                             Boolean perfSecurityExtend, Boolean perfSecurityIncrease, Boolean keyPersonnelChange,
                             Boolean replacementEqualOrBetter, Long version) {
}
