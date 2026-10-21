package rw.gov.ptc.request.enums;

/** Lifecycle of a request to the committee. Values match the database CHECK constraint. */
public enum RequestStatus {
    DRAFT,
    SUBMITTED,
    ACCEPTED,
    RETURNED,
    WITHDRAWN
}
