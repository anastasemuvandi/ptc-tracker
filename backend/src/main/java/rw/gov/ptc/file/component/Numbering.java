package rw.gov.ptc.file.component;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import rw.gov.ptc.common.Ids;
import rw.gov.ptc.tenancy.TenantContext;

/**
 * Per-institution sequential counters for human-facing numbers (file and
 * session numbers). Never derived from UUIDs. The upsert is atomic, so
 * concurrent requests cannot obtain the same number.
 */
@Component
public class Numbering {

    private final JdbcTemplate jdbc;

    public Numbering(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int next(String counterKey) {
        Integer value = jdbc.queryForObject("""
                INSERT INTO institution_counter (id, institution_id, counter_key, last_value)
                VALUES (?, ?, ?, 1)
                ON CONFLICT (institution_id, counter_key)
                DO UPDATE SET last_value = institution_counter.last_value + 1
                RETURNING last_value
                """, Integer.class, Ids.newId(), TenantContext.requireInstitution(), counterKey);
        return value == null ? 1 : value;
    }
}
