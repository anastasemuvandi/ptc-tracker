package rw.gov.ptc.contractchange.dto;

import java.time.LocalDate;

public record ExtensionInput(LocalDate requestedCompletionDate, String groundCode, String groundDetails,
                             LocalDate requestReceivedOn, Boolean penaltiesConsidered, String bidderFaultReasoning,
                             LocalDate forceMajeureStart, LocalDate forceMajeureEnd, Long version) {
}
