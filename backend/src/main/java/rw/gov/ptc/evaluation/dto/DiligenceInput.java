package rw.gov.ptc.evaluation.dto;

import java.time.LocalDate;

public record DiligenceInput(String bidderRef, String performedByName, LocalDate performedOn, String itemsVerified,
                             String result, Boolean disqualified, String disqualificationGround, Long version) {
}
