package rw.gov.ptc.audit.service;

import java.util.UUID;

/**
 * Writes to the append-only audit trail inside the caller's transaction, so
 * a change and its audit row commit or roll back together. The database
 * rejects any UPDATE/DELETE of audit rows (trigger + revoked grants).
 */
public interface AuditService {

    /**
     * @param previous state before the change (null for creates); serialised as JSON
     * @param current  state after the change (null for deletes)
     */
    void record(String action, String entityType, UUID entityId, Object previous, Object current);

    void created(String entityType, UUID entityId, Object current);

    void updated(String entityType, UUID entityId, Object previous, Object current);

    /** Deep copy for "previous value" capture before mutating an entity. */
    com.fasterxml.jackson.databind.JsonNode snapshot(Object entity);
}
