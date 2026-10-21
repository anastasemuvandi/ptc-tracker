package rw.gov.ptc.institution.dto;

import java.time.LocalDate;

public record RequesterAccess(String category, LocalDate accessUntil) {
}
