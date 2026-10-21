package rw.gov.ptc.institution.enums;

/** Account state of a user. Values match the database CHECK constraint. */
public enum UserStatus {
    INVITED,
    ACTIVE,
    LOCKED,
    DISABLED
}
