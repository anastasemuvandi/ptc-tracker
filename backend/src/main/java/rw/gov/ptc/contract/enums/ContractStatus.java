package rw.gov.ptc.contract.enums;

/** Lifecycle of a contract reference. Values match the database CHECK constraint. */
public enum ContractStatus {
    ACTIVE,
    COMPLETED,
    TERMINATED,
    CANCELLED
}
