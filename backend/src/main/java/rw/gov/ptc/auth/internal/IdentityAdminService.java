package rw.gov.ptc.auth.internal;

import org.springframework.stereotype.Service;
import rw.gov.ptc.auth.api.AuthMailer;
import rw.gov.ptc.auth.api.IdentityAdmin;
import rw.gov.ptc.auth.api.InstitutionRole;
import rw.gov.ptc.common.ApiException;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Invite-only onboarding and identity changes, each bumping token_version. */
@Service
public class IdentityAdminService implements IdentityAdmin {

    private static final Set<String> STATUSES = Set.of("ACTIVE", "LOCKED", "DISABLED");

    private final AuthStore store;
    private final AuthProperties props;
    private final AuthMailer mailer;

    public IdentityAdminService(AuthStore store, AuthProperties props, AuthMailer mailer) {
        this.store = store;
        this.props = props;
        this.mailer = mailer;
    }

    @Override
    public UUID invite(Invitation inv, UUID invitedBy) {
        if (inv.email() == null || !inv.email().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw ApiException.badRequest("user.invalid_email", "Enter a valid email address");
        }
        if (inv.roles().contains(InstitutionRole.PLATFORM_ADMIN) != (inv.institutionId() == null)) {
            throw ApiException.badRequest("user.invalid_roles", "Platform administrators have no institution");
        }
        if (store.userByEmail(inv.email()).isPresent()) {
            throw ApiException.conflict("user.email_taken", "A user with this email already exists");
        }
        UUID userId = store.inTransaction(() -> {
            UUID id = store.insertUser(inv.institutionId(), inv.email(), inv.fullName(), inv.jobTitle(),
                    inv.officeHolderCode(), inv.procurementOfficer(), inv.locale());
            store.replaceRoles(id, inv.institutionId(), inv.roles(), invitedBy);
            return id;
        });
        store.audit(inv.institutionId(), invitedBy, "USER_INVITED", userId,
                Map.of("email", inv.email(), "roles", inv.roles().stream().map(Enum::name).sorted().toList()));
        sendInvitation(userId, invitedBy);
        return userId;
    }

    @Override
    public void resendInvitation(UUID userId, UUID requestedBy) {
        var user = store.user(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (!"INVITED".equals(user.status())) {
            throw ApiException.conflict("user.not_invited", "This user has already accepted the invitation");
        }
        sendInvitation(userId, requestedBy);
    }

    private void sendInvitation(UUID userId, UUID by) {
        var user = store.user(userId).orElseThrow();
        String raw = SecretBox.randomToken();
        store.insertOneTime(userId, "INVITE", SecretBox.sha256(raw), Instant.now().plus(props.invitationTtl()), by);
        mailer.sendInvitation(userId, user.email(), user.fullName(), user.locale(),
                props.publicBaseUrl() + "/accept-invite?token=" + raw);
    }

    @Override
    public void setInstitutionRoles(UUID userId, UUID institutionId, Set<InstitutionRole> roles, UUID changedBy) {
        var user = store.user(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (institutionId == null || !institutionId.equals(user.institutionId())) {
            throw ApiException.forbidden("tenant_violation", "User belongs to another institution");
        }
        if (roles.contains(InstitutionRole.PLATFORM_ADMIN)) {
            throw ApiException.badRequest("user.invalid_roles", "Platform administrators have no institution");
        }
        var before = store.roles(userId);
        store.inTransaction(() -> {
            store.replaceRoles(userId, institutionId, roles, changedBy);
            store.bumpTokenVersion(userId);
            return null;
        });
        store.audit(institutionId, changedBy, "USER_ROLES_CHANGED", userId,
                Map.of("before", before.stream().map(Enum::name).sorted().toList(),
                        "after", roles.stream().map(Enum::name).sorted().toList()));
    }

    @Override
    public void setStatus(UUID userId, String status, UUID changedBy) {
        if (!STATUSES.contains(status)) {
            throw ApiException.badRequest("user.invalid_status", "Unknown status");
        }
        var user = store.user(userId).orElseThrow(() -> ApiException.notFound("User"));
        store.inTransaction(() -> {
            store.setUserStatus(userId, status);
            if (!"ACTIVE".equals(status)) {
                store.revokeAllForUser(userId, "status_" + status.toLowerCase());
            } else {
                store.clearFailures(userId);
            }
            return null;
        });
        store.audit(user.institutionId(), changedBy, "USER_STATUS_CHANGED", userId,
                Map.of("before", user.status(), "after", status));
    }

    @Override
    public void membershipChanged(UUID userId) {
        store.bumpTokenVersion(userId);
    }

    @Override
    public void revokeAllSessions(UUID userId, String reason, UUID actor) {
        var user = store.user(userId).orElseThrow(() -> ApiException.notFound("User"));
        store.inTransaction(() -> {
            store.revokeAllForUser(userId, reason);
            store.bumpTokenVersion(userId);
            return null;
        });
        store.audit(user.institutionId(), actor, "TOKEN_FAMILY_REVOKED", userId, Map.of("reason", reason, "scope", "all"));
    }

    @Override
    public void resetMfa(UUID userId, UUID actor) {
        var user = store.user(userId).orElseThrow(() -> ApiException.notFound("User"));
        clearMfa(userId);
        store.audit(user.institutionId(), actor, "MFA_RESET", userId, null);
    }

    /**
     * Recovery when an institution has no administrator left who can sign in:
     * the platform administrator may reset an institution administrator's MFA,
     * and no one else's.
     */
    void resetInstitutionAdminMfa(UUID institutionId, UUID userId, UUID actor) {
        var user = store.user(userId).filter(u -> institutionId.equals(u.institutionId()))
                .orElseThrow(() -> ApiException.notFound("User"));
        if (!store.roles(userId).contains(InstitutionRole.INSTITUTION_ADMIN)) {
            throw ApiException.rule("platform.not_institution_admin",
                    "Only an institution administrator's two-step verification can be reset here");
        }
        clearMfa(userId);
        store.audit(user.institutionId(), actor, "MFA_RESET", userId, Map.of("by", "PLATFORM_ADMIN"));
    }

    private void clearMfa(UUID userId) {
        store.inTransaction(() -> {
            store.deleteMfa(userId);
            store.revokeAllForUser(userId, "mfa_reset");
            store.bumpTokenVersion(userId);
            return null;
        });
    }
}
