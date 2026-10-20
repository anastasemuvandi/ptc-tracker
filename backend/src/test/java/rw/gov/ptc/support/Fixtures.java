package rw.gov.ptc.support;

import org.springframework.jdbc.core.JdbcTemplate;
import rw.gov.ptc.common.Ids;

import java.util.UUID;

/** Minimal tenant data written as the owner (RLS bypassed) for isolation tests. */
public final class Fixtures {

    public record Tenant(UUID institutionId, UUID userId, UUID committeeId, UUID contractId, UUID fileId,
                         UUID sessionId) {
    }

    private Fixtures() {
    }

    public static Tenant tenant(String abbreviation) {
        JdbcTemplate owner = Owner.jdbc();
        UUID inst = Ids.newId();
        owner.update("INSERT INTO institution (id, name, abbreviation, institution_type) VALUES (?, ?, ?, 'MINISTRY')",
                inst, "Institution " + abbreviation, abbreviation);
        UUID user = Ids.newId();
        owner.update("INSERT INTO app_user (id, institution_id, email, full_name, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                user, inst, abbreviation.toLowerCase() + "-" + user + "@example.rw", "User " + abbreviation);
        UUID committee = Ids.newId();
        owner.update("INSERT INTO committee (id, institution_id, name, code, size) VALUES (?, ?, 'PTC A', 'PTC-A', 5)",
                committee, inst);
        owner.update("""
                INSERT INTO committee_membership (id, institution_id, committee_id, user_id, role, term_start, term_end)
                VALUES (?, ?, ?, ?, 'CHAIRPERSON', current_date - 10, current_date + 700)
                """, Ids.newId(), inst, committee, user);
        UUID contract = Ids.newId();
        owner.update("""
                INSERT INTO contract (id, institution_id, contract_ref, title, procurement_type, currency, original_value,
                                      original_completion_date, current_completion_date)
                VALUES (?, ?, ?, 'Works', 'WORKS', 'RWF', 100000000.00, current_date + 100, current_date + 100)
                """, contract, inst, "CT-" + abbreviation);
        UUID file = Ids.newId();
        owner.update("""
                INSERT INTO proc_file (id, institution_id, committee_id, file_number, item_type, contract_id, title, date_received)
                VALUES (?, ?, ?, ?, 'C', ?, 'Addendum 1', current_date)
                """, file, inst, committee, "F-" + abbreviation + "/0001", contract);
        UUID session = Ids.newId();
        owner.update("""
                INSERT INTO committee_session (id, institution_id, committee_id, session_number, kind, scheduled_at,
                                               committee_size, config_snapshot)
                VALUES (?, ?, ?, ?, 'ORDINARY', now() + interval '3 days', 5, '{}')
                """, session, inst, committee, "S-" + abbreviation + "/001");
        owner.update("INSERT INTO audit_event (id, institution_id, actor_id, action, entity_type, entity_id) VALUES (?, ?, ?, 'CREATE', 'proc_file', ?)",
                Ids.newId(), inst, user, file);
        owner.update("INSERT INTO config_rule (id, institution_id, rule_key, rule_value, effective_from, reason) VALUES (?, ?, 'amendment.ceilingPct', '15', now() - interval '1 day', 'test')",
                Ids.newId(), inst);
        return new Tenant(inst, user, committee, contract, file, session);
    }
}
