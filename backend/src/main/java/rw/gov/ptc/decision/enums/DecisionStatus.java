package rw.gov.ptc.decision.enums;

/** Lifecycle of a committee decision. Values match the database CHECK constraint. */
public enum DecisionStatus {
    DRAFT,
    VOTING,
    ADOPTED,
    NOT_ADOPTED,
    ISSUED
}
