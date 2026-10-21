package rw.gov.ptc.tenancy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import rw.gov.ptc.common.Ids;
import rw.gov.ptc.config.service.ConfigService;
import rw.gov.ptc.institution.repository.InstitutionRepository;
import rw.gov.ptc.support.Fixtures;
import rw.gov.ptc.support.IntegrationTest;
import rw.gov.ptc.support.Owner;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves tenant isolation at the database layer: the application role sees
 * only the rows of the institution in app.institution_id, cannot write into
 * another tenant, and sees nothing at all without a tenant context.
 */
class TenantIsolationTest extends IntegrationTest {

    static final List<String> TENANT_TABLES = List.of(
            "committee", "committee_membership", "contract", "proc_file", "committee_session", "audit_event");

    static Fixtures.Tenant a;
    static Fixtures.Tenant b;

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    InstitutionRepository institutions;
    @Autowired
    ConfigService config;

    /** After the context (and so the migrations) is up. */
    @BeforeEach
    void seed() {
        if (a != null) {
            return;
        }
        a = Fixtures.tenant("TA" + System.nanoTime() % 100000);
        b = Fixtures.tenant("TB" + System.nanoTime() % 100000);
    }

    private static TenantContext.Scope as(Fixtures.Tenant t) {
        return new TenantContext.Scope(t.institutionId(), t.userId(), false);
    }

    @Test
    void crossTenantReadsReturnNothing() {
        TenantContext.runAs(as(a), () -> {
            for (String table : TENANT_TABLES) {
                List<UUID> institutionsSeen = jdbc.queryForList(
                        "SELECT DISTINCT institution_id FROM " + table, UUID.class);
                assertThat(institutionsSeen).as(table).containsOnly(a.institutionId());
            }
            assertThat(jdbc.queryForList("SELECT id FROM proc_file WHERE id = ?", UUID.class, b.fileId())).isEmpty();
            assertThat(jdbc.queryForList("SELECT id FROM committee WHERE id = ?", UUID.class, b.committeeId())).isEmpty();
            assertThat(jdbc.queryForList("SELECT id FROM contract WHERE contract_ref = ?", UUID.class, "CT-" + abbreviation(b))).isEmpty();
            assertThat(jdbc.queryForList("SELECT id FROM app_user WHERE id = ?", UUID.class, b.userId())).isEmpty();
            assertThat(institutions.findById(b.institutionId())).isEmpty();
            assertThat(institutions.findById(a.institutionId())).isPresent();
        });
    }

    @Test
    void noTenantContextSeesNoTenantRows() {
        for (String table : TENANT_TABLES) {
            Integer n = jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
            assertThat(n).as(table).isZero();
        }
        assertThat(institutions.findAll()).isEmpty();
    }

    @Test
    void cannotWriteIntoAnotherTenant() {
        TenantContext.runAs(as(a), () -> {
            assertThatThrownBy(() -> jdbc.update(
                    "INSERT INTO committee (id, institution_id, name, code, size) VALUES (?, ?, 'X', 'X', 5)",
                    Ids.newId(), b.institutionId()))
                    .isInstanceOf(DataAccessException.class)
                    .rootCause().hasMessageContaining("row-level security");
            int updated = jdbc.update("UPDATE proc_file SET title = 'hijacked' WHERE id = ?", b.fileId());
            assertThat(updated).isZero();
            // Moving one's own row to another tenant is also refused.
            assertThatThrownBy(() -> jdbc.update("UPDATE proc_file SET institution_id = ? WHERE id = ?",
                    b.institutionId(), a.fileId())).isInstanceOf(DataAccessException.class);
        });
        String title = Owner.jdbc().queryForObject("SELECT title FROM proc_file WHERE id = ?", String.class, b.fileId());
        assertThat(title).isEqualTo("Addendum 1");
    }

    @Test
    void tenantVariablesDoNotLeakBetweenCheckouts() {
        TenantContext.runAs(as(a), () ->
                assertThat(jdbc.queryForObject("SELECT count(*) FROM committee", Integer.class)).isEqualTo(1));
        // Same pool, next checkout without context: variables were reset.
        assertThat(jdbc.queryForObject("SELECT current_setting('app.institution_id', true)", String.class)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM committee", Integer.class)).isZero();
    }

    @Test
    void institutionConfigurationIsPrivate() {
        TenantContext.runAs(as(a), () -> {
            List<UUID> owners = jdbc.queryForList(
                    "SELECT DISTINCT institution_id FROM config_rule WHERE institution_id IS NOT NULL", UUID.class);
            assertThat(owners).containsOnly(a.institutionId());
            assertThat(config.snapshot().getInt("amendment.ceilingPct")).isEqualTo(15);
            assertThat(config.snapshot().getInt("committee.minimumSize")).isEqualTo(3);
        });
    }

    @Test
    void auditTrailIsAppendOnly() {
        TenantContext.runAs(as(a), () -> {
            assertThatThrownBy(() -> jdbc.update("UPDATE audit_event SET action = 'X'"))
                    .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("permission denied");
            assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_event"))
                    .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("permission denied");
        });
        // Even the owning role is stopped by the trigger.
        assertThatThrownBy(() -> Owner.jdbc().update("UPDATE audit_event SET action = 'X'"))
                .rootCause().hasMessageContaining("append-only");
        assertThatThrownBy(() -> Owner.jdbc().update("DELETE FROM audit_event"))
                .rootCause().hasMessageContaining("append-only");
        assertThatThrownBy(() -> Owner.jdbc().update("TRUNCATE audit_event"))
                .rootCause().hasMessageContaining("append-only");
    }

    @Test
    void applicationRoleCannotReadCredentials() {
        TenantContext.runAs(as(a), () ->
                assertThatThrownBy(() -> jdbc.queryForList("SELECT * FROM user_credential"))
                        .isInstanceOf(DataAccessException.class).rootCause().hasMessageContaining("permission denied"));
    }

    private static String abbreviation(Fixtures.Tenant t) {
        return Owner.jdbc().queryForObject("SELECT abbreviation FROM institution WHERE id = ?", String.class, t.institutionId());
    }
}
