package rw.gov.ptc.institution.enums;

/** How long a requester keeps access. Values match the database CHECK constraint. */
public enum RequesterCategory {
    /** Access ends on a set date; the account is then disabled. */
    SHORT_TERM,
    /** No end date; access lasts until the administrator offboards the person. */
    LONG_TERM
}
