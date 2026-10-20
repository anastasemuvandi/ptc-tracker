package rw.gov.ptc.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rw.gov.ptc.auth.api.InstitutionRole;
import rw.gov.ptc.common.Rows;
import rw.gov.ptc.security.Access;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Audit log browsing for institution administrators, the CBM and internal audit. */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final JdbcTemplate jdbc;
    private final Access access;

    public AuditController(JdbcTemplate jdbc, Access access) {
        this.jdbc = jdbc;
        this.access = access;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) UUID entityId,
                                          @RequestParam(required = false) UUID actorId,
                                          @RequestParam(required = false) String action,
                                          @RequestParam(required = false) Instant before,
                                          @RequestParam(defaultValue = "100") int limit) {
        access.requireAny(InstitutionRole.READ_ONLY, InstitutionRole.INSTITUTION_ADMIN, InstitutionRole.CBM);
        return Rows.camel(jdbc.queryForList("""
                SELECT a.id, a.occurred_at, a.actor_id, u.full_name AS actor_name, a.action, a.entity_type,
                       a.entity_id, a.previous_value::text AS previous_value, a.new_value::text AS new_value,
                       a.ip_address
                  FROM audit_event a LEFT JOIN app_user u ON u.id = a.actor_id
                 WHERE (CAST(? AS uuid) IS NULL OR a.entity_id = ?)
                   AND (CAST(? AS uuid) IS NULL OR a.actor_id = ?)
                   AND (CAST(? AS text) IS NULL OR a.action = ?)
                   AND (CAST(? AS timestamptz) IS NULL OR a.occurred_at < ?)
                 ORDER BY a.occurred_at DESC
                 LIMIT ?
                """, entityId, entityId, actorId, actorId, action, action,
                before == null ? null : java.sql.Timestamp.from(before),
                before == null ? null : java.sql.Timestamp.from(before), Math.min(Math.max(limit, 1), 500)));
    }
}
