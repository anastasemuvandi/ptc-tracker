package rw.gov.ptc.institution.service;

import rw.gov.ptc.institution.entity.AppUser;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Onboarding and offboarding of requesters, done by the institution
 * administrator: short-term access ends on a date, long-term access lasts
 * until the administrator disables the account.
 */
public interface RequesterAccessService {

    /** Checks a category and end date before an invitation is sent. */
    void validate(String category, LocalDate accessUntil);

    AppUser apply(UUID userId, String category, LocalDate accessUntil);

    /** Ends requester access past its date in every institution: the role goes, or the account if it was the only role. */
    void offboardExpiredEverywhere();

    int offboardExpired(LocalDate today);
}
