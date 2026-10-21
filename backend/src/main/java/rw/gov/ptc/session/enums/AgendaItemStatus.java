package rw.gov.ptc.session.enums;

/** Progress of an agenda item within a session. Values match the database CHECK constraint. */
public enum AgendaItemStatus {
    PENDING,
    DISCUSSED,
    DEFERRED
}
