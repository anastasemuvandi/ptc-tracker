package rw.gov.ptc.session.enums;

/** A member's attendance mark for a session. Values match the database CHECK constraint. */
public enum AttendanceStatus {
    PRESENT,
    ABSENT_WITH_PERMISSION,
    ON_LEAVE,
    ABSENT_UNJUSTIFIED
}
