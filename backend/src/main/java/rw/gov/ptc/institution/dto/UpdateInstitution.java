package rw.gov.ptc.institution.dto;

public record UpdateInstitution(String name, String institutionType, String supervisingAuthority,
                                String minutesLetterhead, String minutesFontFamily, Integer minutesFontSize,
                                Boolean holidayOverride, String defaultCurrency, Long version) {
}
