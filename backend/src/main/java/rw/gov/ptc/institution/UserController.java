package rw.gov.ptc.institution;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import rw.gov.ptc.audit.AuditService;
import rw.gov.ptc.auth.api.AuthPrincipal;
import rw.gov.ptc.auth.api.IdentityAdmin;
import rw.gov.ptc.auth.api.InstitutionRole;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.common.Rows;
import rw.gov.ptc.security.Access;
import rw.gov.ptc.tenancy.TenantContext;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class UserController {

    public record InviteRequest(String email, String fullName, String jobTitle, String officeHolderCode,
                                boolean procurementOfficer, String locale, Set<InstitutionRole> roles) {
    }

    public record ProfileUpdate(String fullName, String jobTitle, String phone, String officeHolderCode,
                                Boolean procurementOfficer, String locale) {
    }

    private final AppUserRepository users;
    private final IdentityAdmin identity;
    private final Access access;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public UserController(AppUserRepository users, IdentityAdmin identity, Access access, AuditService audit,
                          JdbcTemplate jdbc) {
        this.users = users;
        this.identity = identity;
        this.access = access;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    /** The signed-in user: profile, institution roles and committee seats. */
    @GetMapping("/me")
    public Map<String, Object> me() {
        AuthPrincipal p = access.principal();
        AppUser u = users.findById(p.userId()).orElseThrow(() -> ApiException.notFound("User"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", u.id);
        out.put("email", u.email);
        out.put("fullName", u.fullName);
        out.put("locale", u.locale);
        out.put("institutionId", p.institutionId());
        out.put("roles", p.roles());
        out.put("mfaVerified", p.mfaVerified());
        out.put("isProcurementOfficer", u.isProcurementOfficer);
        out.put("committees", p.institutionId() == null ? List.of() : Rows.camel(jdbc.queryForList("""
                SELECT c.id AS committee_id, c.name, c.code, m.role, m.id AS membership_id, m.term_end
                  FROM committee_membership m JOIN committee c ON c.id = m.committee_id
                 WHERE m.user_id = ? AND m.status = 'ACTIVE' ORDER BY c.code
                """, p.userId())));
        if (p.institutionId() != null) {
            out.put("institution", Rows.camel(jdbc.queryForMap(
                    "SELECT name, abbreviation, default_currency FROM institution WHERE id = ?", p.institutionId())));
        }
        return out;
    }

    @PutMapping("/me")
    @Transactional
    public AppUser updateMe(@RequestBody ProfileUpdate req) {
        AppUser u = users.findById(access.userId()).orElseThrow(() -> ApiException.notFound("User"));
        var before = audit.snapshot(u);
        if (req.fullName() != null && !req.fullName().isBlank()) u.fullName = req.fullName().trim();
        if (req.phone() != null) u.phone = req.phone();
        if (req.locale() != null) {
            if (!Set.of("en", "rw", "fr").contains(req.locale())) {
                throw ApiException.badRequest("user.invalid_locale", "Unsupported language");
            }
            u.locale = req.locale();
        }
        users.save(u);
        audit.updated("app_user", u.id, before, u);
        return u;
    }

    @GetMapping("/users")
    public List<Map<String, Object>> list() {
        TenantContext.requireInstitution();
        return Rows.camel(jdbc.queryForList("""
                SELECT u.id, u.email, u.full_name, u.job_title, u.office_holder_code, u.is_procurement_officer,
                       u.status, u.locale,
                       coalesce((SELECT array_agg(r.role ORDER BY r.role) FROM user_role r WHERE r.user_id = u.id), '{}') AS roles
                  FROM app_user u ORDER BY u.full_name
                """));
    }

    @PostMapping("/users")
    public Map<String, Object> invite(@RequestBody InviteRequest req) {
        access.requireInstitutionAdmin();
        Set<InstitutionRole> roles = req.roles() == null ? EnumSet.noneOf(InstitutionRole.class) : req.roles();
        if (roles.contains(InstitutionRole.PLATFORM_ADMIN)) {
            throw ApiException.forbidden("forbidden", "Cannot grant platform administration");
        }
        UUID id = identity.invite(new IdentityAdmin.Invitation(TenantContext.requireInstitution(), req.email(),
                req.fullName(), req.jobTitle(), req.officeHolderCode(), req.procurementOfficer(), req.locale(), roles),
                access.userId());
        return Map.of("id", id);
    }

    @PutMapping("/users/{id}")
    @Transactional
    public AppUser updateProfile(@PathVariable UUID id, @RequestBody ProfileUpdate req) {
        access.requireInstitutionAdmin();
        AppUser u = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        var before = audit.snapshot(u);
        if (req.fullName() != null && !req.fullName().isBlank()) u.fullName = req.fullName().trim();
        if (req.jobTitle() != null) u.jobTitle = req.jobTitle();
        if (req.phone() != null) u.phone = req.phone();
        if (req.officeHolderCode() != null) u.officeHolderCode = req.officeHolderCode().isBlank() ? null : req.officeHolderCode();
        if (req.procurementOfficer() != null) u.isProcurementOfficer = req.procurementOfficer();
        users.save(u);
        audit.updated("app_user", u.id, before, u);
        return u;
    }

    @PutMapping("/users/{id}/roles")
    public ResponseEntity<Void> setRoles(@PathVariable UUID id, @RequestBody Set<InstitutionRole> roles) {
        access.requireInstitutionAdmin();
        if (roles.contains(InstitutionRole.CBM)) {
            Integer seats = jdbc.queryForObject(
                    "SELECT count(*) FROM committee_membership WHERE user_id = ? AND status = 'ACTIVE'", Integer.class, id);
            if (seats != null && seats > 0) {
                throw ApiException.rule("composition.cbm_is_member",
                        "The Chief Budget Manager cannot hold a committee seat. End the membership first.");
            }
        }
        identity.setInstitutionRoles(id, TenantContext.requireInstitution(), roles, access.userId());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/users/{id}/status")
    public ResponseEntity<Void> setStatus(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        access.requireInstitutionAdmin();
        requireSameInstitution(id);
        identity.setStatus(id, body.get("status"), access.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{id}/resend-invitation")
    public ResponseEntity<Void> resend(@PathVariable UUID id) {
        access.requireInstitutionAdmin();
        requireSameInstitution(id);
        identity.resendInvitation(id, access.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{id}/reset-mfa")
    public ResponseEntity<Void> resetMfa(@PathVariable UUID id) {
        access.requireInstitutionAdmin();
        requireSameInstitution(id);
        identity.resetMfa(id, access.userId());
        return ResponseEntity.noContent().build();
    }

    /** RLS hides other institutions' users, so absence means "not yours". */
    private void requireSameInstitution(UUID userId) {
        users.findById(userId).filter(u -> TenantContext.requireInstitution().equals(u.institutionId))
                .orElseThrow(() -> ApiException.notFound("User"));
    }
}
