package rw.gov.ptc.file.enums;

/** Outcome of a request to table a file at a session. Values match the database CHECK constraint. */
public enum TableRequestStatus {
    PENDING,
    ACCEPTED,
    DECLINED
}
