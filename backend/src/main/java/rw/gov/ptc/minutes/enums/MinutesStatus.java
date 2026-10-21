package rw.gov.ptc.minutes.enums;

/** Lifecycle of session minutes. Values match the database CHECK constraint. */
public enum MinutesStatus {
    DRAFT,
    IN_REVIEW,
    FINAL
}
