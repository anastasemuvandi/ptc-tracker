package rw.gov.ptc.institution.dto;

import rw.gov.ptc.auth.api.InstitutionRole;

import java.time.LocalDate;
import java.util.Set;

public record InviteRequest(String email, String fullName, String jobTitle, String officeHolderCode,
                            boolean procurementOfficer, String locale, Set<InstitutionRole> roles,
                            String requesterCategory, LocalDate accessUntil) {
}
