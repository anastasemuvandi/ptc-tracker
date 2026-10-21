package rw.gov.ptc.institution.dto;

public record CreateInstitution(String name, String abbreviation, String institutionType,
                                String supervisingAuthority, String defaultCurrency,
                                String adminEmail, String adminFullName) {
}
