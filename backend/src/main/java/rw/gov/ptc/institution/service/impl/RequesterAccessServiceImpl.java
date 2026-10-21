package rw.gov.ptc.institution.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.gov.ptc.audit.service.AuditService;
import rw.gov.ptc.auth.api.IdentityAdmin;
import rw.gov.ptc.auth.api.InstitutionRole;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.common.Enums;
import rw.gov.ptc.common.Rows;
import rw.gov.ptc.common.Time;
import rw.gov.ptc.institution.entity.AppUser;
import rw.gov.ptc.institution.enums.RequesterCategory;
import rw.gov.ptc.institution.repository.AppUserRepository;
import rw.gov.ptc.institution.service.RequesterAccessService;
import rw.gov.ptc.tenancy.TenantContext;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class RequesterAccessServiceImpl implements RequesterAccessService {

    private static final Logger log = LoggerFactory.getLogger(RequesterAccessServiceImpl.class);

    private final AppUserRepository users;
    private final IdentityAdmin identity;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public RequesterAccessServiceImpl(AppUserRepository users, IdentityAdmin identity, AuditService audit, JdbcTemplate jdbc) {
        this.users = users;
        this.identity = identity;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Override
    public void validate(String category, LocalDate accessUntil) {
        RequesterCategory c = Enums.parse(RequesterCategory.class, category);
        if (c == null) {
            throw ApiException.badRequest("user.requester_category_required", "Choose short-term or long-term access");
        }
        if (c == RequesterCategory.SHORT_TERM) {
            if (accessUntil == null) {
                throw ApiException.badRequest("user.access_until_required", "Short-term access needs an end date");
            }
            if (accessUntil.isBefore(Time.today())) {
                throw ApiException.rule("user.access_until_past", "The end of access cannot be in the past");
            }
        }
    }

    @Override
    @Transactional
    public AppUser apply(UUID userId, String category, LocalDate accessUntil) {
        validate(category, accessUntil);
        AppUser u = users.findById(userId).filter(x -> TenantContext.requireInstitution().equals(x.institutionId))
                .orElseThrow(() -> ApiException.notFound("User"));
        var before = audit.snapshot(u);
        u.requesterCategory = Enums.parse(RequesterCategory.class, category);
        u.accessUntil = u.requesterCategory == RequesterCategory.SHORT_TERM ? accessUntil : null;
        users.saveAndFlush(u);
        audit.updated("app_user", u.id, before, u);
        return u;
    }

    @Override
    public void offboardExpiredEverywhere() {
        for (UUID institution : jdbc.queryForList("SELECT * FROM app_active_institution_ids()", UUID.class)) {
            try {
                TenantContext.runAs(new TenantContext.Scope(institution, null, false), () -> offboardExpired(Time.today()));
            } catch (RuntimeException e) {
                log.error("Requester offboarding failed for institution {}", institution, e);
            }
        }
    }

    @Override
    public int offboardExpired(LocalDate today) {
        UUID institution = TenantContext.requireInstitution();
        int n = 0;
        for (Map<String, Object> row : Rows.camel(jdbc.queryForList("""
                SELECT u.id, coalesce((SELECT array_agg(r.role) FROM user_role r WHERE r.user_id = u.id), '{}') AS roles
                  FROM app_user u
                 WHERE u.access_until < ? AND u.status IN ('ACTIVE', 'INVITED', 'LOCKED')
                   AND EXISTS (SELECT 1 FROM user_role r WHERE r.user_id = u.id AND r.role = 'REQUESTER')
                """, today))) {
            UUID id = (UUID) row.get("id");
            Set<InstitutionRole> roles = EnumSet.noneOf(InstitutionRole.class);
            for (Object r : (List<?>) row.get("roles")) {
                roles.add(InstitutionRole.valueOf(r.toString()));
            }
            roles.remove(InstitutionRole.REQUESTER);
            boolean seated = Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM committee_membership WHERE user_id = ? AND status = 'ACTIVE')", Boolean.class, id));
            if (roles.isEmpty() && !seated) {
                identity.setStatus(id, "DISABLED", null);
            } else {
                identity.setInstitutionRoles(id, institution, roles, null);
            }
            n++;
        }
        return n;
    }
}
