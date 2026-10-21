package rw.gov.ptc.committee.enums;

/** Lifecycle of a seat on a committee. Values match the database CHECK constraint. */
public enum MembershipStatus {
    ACTIVE,
    ENDED,
    REPLACED
}
