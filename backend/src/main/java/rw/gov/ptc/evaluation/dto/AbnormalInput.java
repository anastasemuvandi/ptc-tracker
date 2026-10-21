package rw.gov.ptc.evaluation.dto;

import java.time.LocalDate;

public record AbnormalInput(String bidderRef, String direction, String requestRef, LocalDate requestDate,
                            String explanationSummary, LocalDate explanationDate, String analysis,
                            Boolean doubtLifted, String decision, String decisionReasons, Long version) {
}
