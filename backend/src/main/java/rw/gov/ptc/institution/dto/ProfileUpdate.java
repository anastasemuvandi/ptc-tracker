package rw.gov.ptc.institution.dto;

public record ProfileUpdate(String fullName, String jobTitle, String phone, String officeHolderCode,
                            Boolean procurementOfficer, String locale) {
}
