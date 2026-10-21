package rw.gov.ptc.contract.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ContractRequest(String contractRef, String tenderRef, String title, String supplierRef,
                              String procurementType, String currency, BigDecimal originalValue,
                              LocalDate signedDate, LocalDate originalCompletionDate, String perfSecurityRef,
                              BigDecimal perfSecurityAmount, String perfSecurityCurrency,
                              LocalDate perfSecurityExpiry, Boolean isFramework, LocalDate frameworkExpiry,
                              String status, Long version) {
}
