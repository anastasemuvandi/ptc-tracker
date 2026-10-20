package rw.gov.ptc.auth.api;

public enum CommitteeRole {
    CHAIRPERSON,
    VICE_CHAIRPERSON,
    SECRETARY,
    MEMBER;

    /** Officers convene and manage sessions. */
    public boolean isOfficer() {
        return this != MEMBER;
    }
}
