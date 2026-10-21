package rw.gov.ptc.evaluation.dto;

import java.time.LocalDate;

public record CureItem(String code, String label, Boolean supplied, LocalDate suppliedOn, String note) {
}
