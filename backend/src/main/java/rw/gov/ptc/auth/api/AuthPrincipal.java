package rw.gov.ptc.auth.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The verified identity behind a request — everything the rest of the
 * application may know about authentication. Built only from a validated
 * access token.
 *
 * @param sessionId the login session (refresh-token family); scopes step-up
 */
public record AuthPrincipal(
        UUID userId,
        UUID institutionId,
        Set<InstitutionRole> roles,
        List<CommitteeGrant> committees,
        int tokenVersion,
        UUID sessionId,
        boolean mfaVerified) {

    public record CommitteeGrant(UUID committeeId, CommitteeRole role) {
    }

    public boolean has(InstitutionRole role) {
        return roles.contains(role);
    }

    public boolean isPlatformAdmin() {
        return roles.contains(InstitutionRole.PLATFORM_ADMIN);
    }

    public Optional<CommitteeRole> committeeRole(UUID committeeId) {
        return committees.stream()
                .filter(g -> g.committeeId().equals(committeeId))
                .map(CommitteeGrant::role)
                .findFirst();
    }

    /** Read-only (internal audit) with no other capacity: may not write anything. */
    public boolean isReadOnlyOnly() {
        return roles.equals(Set.of(InstitutionRole.READ_ONLY)) && committees.isEmpty();
    }
}
