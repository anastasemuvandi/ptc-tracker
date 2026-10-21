package rw.gov.ptc.auth.api;

public enum InstitutionRole {
    PLATFORM_ADMIN,
    INSTITUTION_ADMIN,
    CBM,
    READ_ONLY,
    /** Employee outside the committee who submits requests to it and follows their status. */
    REQUESTER
}
