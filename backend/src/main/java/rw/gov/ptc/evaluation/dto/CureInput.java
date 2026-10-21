package rw.gov.ptc.evaluation.dto;

import java.time.LocalDate;
import java.util.List;

public record CureInput(String bidderRef, List<CureItem> outstandingItems, LocalDate issuedOn,
                        String suppliedSummary, LocalDate suppliedOn, String outcome, String outcomeNote,
                        Long version) {
}
