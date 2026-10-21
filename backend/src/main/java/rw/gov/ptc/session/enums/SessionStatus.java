package rw.gov.ptc.session.enums;

/** Lifecycle of a committee session. Values match the database CHECK constraint. */
public enum SessionStatus {
    PLANNED,
    IN_PROGRESS,
    HELD,
    CANCELLED
}
