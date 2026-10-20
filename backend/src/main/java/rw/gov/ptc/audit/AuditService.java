package rw.gov.ptc.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import rw.gov.ptc.common.Ids;
import rw.gov.ptc.tenancy.TenantContext;

import java.util.UUID;

/**
 * Writes to the append-only audit trail inside the caller's transaction, so
 * a change and its audit row commit or roll back together. The database
 * rejects any UPDATE/DELETE of audit rows (trigger + revoked grants).
 */
@Service
public class AuditService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public AuditService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    /**
     * @param previous state before the change (null for creates); serialised as JSON
     * @param current  state after the change (null for deletes)
     */
    public void record(String action, String entityType, UUID entityId, Object previous, Object current) {
        String ip = null;
        String ua = null;
        String requestId = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            ip = attrs.getRequest().getRemoteAddr();
            ua = attrs.getRequest().getHeader("User-Agent");
            requestId = attrs.getRequest().getHeader("Idempotency-Key");
        }
        jdbc.update("""
                INSERT INTO audit_event (id, institution_id, actor_id, action, entity_type, entity_id,
                                         previous_value, new_value, ip_address, user_agent, request_id)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?)
                """, Ids.newId(), TenantContext.institutionOrNull(), TenantContext.userOrNull(), action,
                entityType, entityId, json(previous), json(current), ip, ua, requestId);
    }

    public void created(String entityType, UUID entityId, Object current) {
        record("CREATE", entityType, entityId, null, current);
    }

    public void updated(String entityType, UUID entityId, Object previous, Object current) {
        record("UPDATE", entityType, entityId, previous, current);
    }

    /** Deep copy for "previous value" capture before mutating an entity. */
    public com.fasterxml.jackson.databind.JsonNode snapshot(Object entity) {
        return entity == null ? null : mapper.valueToTree(entity);
    }

    private String json(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
