package rw.gov.ptc.auth.api;

import java.util.Set;
import java.util.UUID;

/**
 * Identity lifecycle operations exposed to the rest of the application.
 * Implementations bump token_version whenever roles, membership or status
 * change so outstanding access tokens are rejected immediately.
 */
public interface IdentityAdmin {

    record Invitation(UUID institutionId, String email, String fullName, String jobTitle,
                      String officeHolderCode, boolean procurementOfficer, String locale,
                      Set<InstitutionRole> roles) {
    }

    /** Creates an INVITED user and emails a single-use invitation link. */
    UUID invite(Invitation invitation, UUID invitedBy);

    void resendInvitation(UUID userId, UUID requestedBy);

    void setInstitutionRoles(UUID userId, UUID institutionId, Set<InstitutionRole> roles, UUID changedBy);

    void setStatus(UUID userId, String status, UUID changedBy);

    /** Call after committee membership changes for the user. */
    void membershipChanged(UUID userId);

    /** Clears a lost authenticator; the user re-enrols at next sign-in. */
    void resetMfa(UUID userId, UUID actor);

    /** Revokes all refresh tokens and outstanding access tokens of the user. */
    void revokeAllSessions(UUID userId, String reason, UUID actor);
}
