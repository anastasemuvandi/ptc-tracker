package rw.gov.ptc.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import rw.gov.ptc.auth.api.AuthPrincipal;
import rw.gov.ptc.auth.api.CommitteeRole;
import rw.gov.ptc.auth.api.InstitutionRole;
import rw.gov.ptc.common.ApiException;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

/**
 * Authorisation helpers over the verified token claims. Tenant isolation is
 * handled by RLS; these checks decide what a user may do inside their
 * institution.
 */
@Component
public class Access {

    public AuthPrincipal principal() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthPrincipal p)) {
            throw ApiException.unauthorized("auth.no_session", "Not signed in");
        }
        return p;
    }

    public UUID userId() {
        return principal().userId();
    }

    public boolean has(InstitutionRole role) {
        return principal().has(role);
    }

    public void requireAny(InstitutionRole... roles) {
        AuthPrincipal p = principal();
        if (Arrays.stream(roles).noneMatch(p::has)) {
            throw ApiException.forbidden("forbidden", "You are not allowed to do this");
        }
    }

    public Optional<CommitteeRole> committeeRole(UUID committeeId) {
        return principal().committeeRole(committeeId);
    }

    /** Chairperson, Vice-Chairperson or Secretary of this committee. */
    public CommitteeRole requireOfficer(UUID committeeId) {
        return committeeRole(committeeId).filter(CommitteeRole::isOfficer)
                .orElseThrow(() -> ApiException.forbidden("committee.officer_required",
                        "Only the Chairperson, Vice-Chairperson or Secretary may do this"));
    }

    public void requireChair(UUID committeeId) {
        committeeRole(committeeId)
                .filter(r -> r == CommitteeRole.CHAIRPERSON || r == CommitteeRole.VICE_CHAIRPERSON)
                .orElseThrow(() -> ApiException.forbidden("committee.chair_required",
                        "Only the Chairperson (or Vice-Chairperson) may do this"));
    }

    public CommitteeRole requireMember(UUID committeeId) {
        return committeeRole(committeeId).orElseThrow(() -> ApiException.forbidden("committee.member_required",
                "Only members of this committee may do this"));
    }

    /** Members of the committee, the CBM, institution admins and read-only auditors. */
    public void requireCanRead(UUID committeeId) {
        AuthPrincipal p = principal();
        if (p.committeeRole(committeeId).isPresent() || p.has(InstitutionRole.CBM)
                || p.has(InstitutionRole.INSTITUTION_ADMIN) || p.has(InstitutionRole.READ_ONLY)) {
            return;
        }
        throw ApiException.forbidden("committee.no_access", "You do not have access to this committee");
    }

    public boolean canReadAllCommittees() {
        AuthPrincipal p = principal();
        return p.has(InstitutionRole.CBM) || p.has(InstitutionRole.INSTITUTION_ADMIN) || p.has(InstitutionRole.READ_ONLY);
    }

    /** Chairperson, Vice-Chairperson or Secretary of at least one committee. */
    public boolean isOfficerAnywhere() {
        return principal().committees().stream().anyMatch(g -> g.role().isOfficer());
    }

    public void requireCbm() {
        requireAny(InstitutionRole.CBM);
    }

    public void requireInstitutionAdmin() {
        requireAny(InstitutionRole.INSTITUTION_ADMIN);
    }
}
