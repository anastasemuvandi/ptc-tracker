package rw.gov.ptc.contractchange.enums;

/** Lifecycle of an amendment or extension request. Values match the database CHECK constraint. */
public enum ChangeRequestStatus {
    OPEN,
    RECOMMENDED,
    APPROVED,
    REJECTED,
    WITHDRAWN
}
