package rw.gov.ptc.evaluation.dto;

import java.time.LocalDate;

public record ClarificationInput(String bidderRef, LocalDate issuedOn, String subject, LocalDate responseReceivedOn,
                                 String treatment, String effect, Long version) {
}
