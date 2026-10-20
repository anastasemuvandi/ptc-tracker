package rw.gov.ptc.evaluation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import rw.gov.ptc.auth.api.AuthPrincipal;
import rw.gov.ptc.auth.api.CommitteeRole;
import rw.gov.ptc.common.Ids;
import rw.gov.ptc.support.Fixtures;
import rw.gov.ptc.support.Owner;
import rw.gov.ptc.tenancy.TenantContext;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Runs service calls as the fixture tenant's Chairperson, and seeds extra files/decisions as the owner. */
public final class TestScopes {

    private TestScopes() {
    }

    public static <T> T asOfficer(Fixtures.Tenant t, Supplier<T> work) {
        AuthPrincipal p = new AuthPrincipal(t.userId(), t.institutionId(), Set.of(),
                List.of(new AuthPrincipal.CommitteeGrant(t.committeeId(), CommitteeRole.CHAIRPERSON)),
                1, Ids.newId(), true);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p, null, List.of()));
        try {
            return TenantContext.callAs(new TenantContext.Scope(t.institutionId(), t.userId(), false), work);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    public static void asOfficer(Fixtures.Tenant t, Runnable work) {
        asOfficer(t, () -> {
            work.run();
            return null;
        });
    }

    public static UUID file(Fixtures.Tenant t, String type, UUID contractId, LocalDate bidValidityExpiry) {
        UUID id = Ids.newId();
        Owner.jdbc().update("""
                INSERT INTO proc_file (id, institution_id, committee_id, file_number, item_type, contract_id, title,
                                       date_received, bid_validity_expiry, tender_ref)
                VALUES (?, ?, ?, ?, ?, ?, ?, current_date, ?, 'TND-1')
                """, id, t.institutionId(), t.committeeId(), "T-" + id.toString().substring(24), type, contractId,
                "Test file " + type, bidValidityExpiry);
        return id;
    }

    /** An ISSUED decision on the file, and optionally the CBM's approval of it. */
    public static UUID issueDecision(Fixtures.Tenant t, UUID fileId, String type, String code, boolean cbmApproved) {
        JdbcTemplate owner = Owner.jdbc();
        UUID item = Ids.newId();
        owner.update("INSERT INTO agenda_item (id, institution_id, session_id, file_id, item_type, position) VALUES (?, ?, ?, ?, ?, 1)",
                item, t.institutionId(), t.sessionId(), fileId, type);
        UUID decision = Ids.newId();
        owner.update("""
                INSERT INTO decision (id, institution_id, session_id, agenda_item_id, file_id, item_type, recommendation_code,
                                      recommendation_text, status, config_snapshot, issued_at, issued_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'Recommendation', 'ISSUED', '{}', now(), ?)
                """, decision, t.institutionId(), t.sessionId(), item, fileId, type, code, t.userId());
        if (cbmApproved) {
            owner.update("INSERT INTO cbm_response (id, institution_id, decision_id, file_id, response, responded_by) VALUES (?, ?, ?, ?, 'APPROVED', ?)",
                    Ids.newId(), t.institutionId(), decision, fileId, t.userId());
        }
        return decision;
    }
}
