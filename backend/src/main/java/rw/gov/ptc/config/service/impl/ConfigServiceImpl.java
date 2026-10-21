package rw.gov.ptc.config.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rw.gov.ptc.audit.service.AuditService;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.common.Ids;
import rw.gov.ptc.common.Rows;
import rw.gov.ptc.config.dto.ConfigSnapshot;
import rw.gov.ptc.config.dto.NewRule;
import rw.gov.ptc.config.service.ConfigService;
import rw.gov.ptc.tenancy.TenantContext;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ConfigServiceImpl implements ConfigService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final AuditService audit;

    public ConfigServiceImpl(JdbcTemplate jdbc, ObjectMapper mapper, AuditService audit) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.audit = audit;
    }

    @Override
    public ConfigSnapshot snapshot() {
        return snapshot(Instant.now());
    }

    @Override
    public ConfigSnapshot snapshot(Instant at) {
        ObjectNode root = mapper.createObjectNode();
        root.put("resolvedAt", at.toString());
        UUID institution = TenantContext.institutionOrNull();
        root.put("institutionId", institution == null ? null : institution.toString());
        ObjectNode values = root.putObject("values");
        ObjectNode sources = root.putObject("sources");
        jdbc.query("""
                SELECT DISTINCT ON (rule_key) rule_key, rule_value::text, institution_id, effective_from, id
                  FROM config_rule
                 WHERE effective_from <= ?
                   AND (institution_id IS NULL OR institution_id = app_current_institution())
                 ORDER BY rule_key, (institution_id IS NULL), effective_from DESC, created_at DESC
                """, rs -> {
            String key = rs.getString(1);
            values.set(key, read(rs.getString(2)));
            ObjectNode src = sources.putObject(key);
            src.put("scope", rs.getObject(3) == null ? "PLATFORM" : "INSTITUTION");
            src.put("effectiveFrom", rs.getTimestamp(4).toInstant().toString());
            src.put("ruleId", rs.getObject(5, UUID.class).toString());
        }, Timestamp.from(at));
        return new ConfigSnapshot(root);
    }

    @Override
    public ConfigSnapshot fromStored(JsonNode stored) {
        return new ConfigSnapshot((ObjectNode) stored);
    }

    @Override
    public List<Map<String, Object>> history(String key) {
        return Rows.camel(jdbc.queryForList("""
                SELECT r.id, r.rule_key, r.rule_value::text AS rule_value, r.effective_from, r.reason,
                       r.legal_reference, r.created_at, u.full_name AS created_by_name,
                       CASE WHEN r.institution_id IS NULL THEN 'PLATFORM' ELSE 'INSTITUTION' END AS scope
                  FROM config_rule r LEFT JOIN app_user u ON u.id = r.created_by
                 WHERE r.rule_key = ?
                 ORDER BY r.effective_from DESC, r.created_at DESC
                """, key));
    }

    @Override
    @Transactional
    public UUID addRule(NewRule rule) {
        ConfigSnapshot current = snapshot();
        if (!current.has(rule.key())) {
            throw ApiException.badRequest("config.unknown_key", "Unknown configuration key", Map.of("key", rule.key()));
        }
        JsonNode existing = current.get(rule.key());
        if (rule.value() == null || existing.getNodeType() != rule.value().getNodeType()) {
            throw ApiException.badRequest("config.wrong_type", "Value has the wrong type",
                    Map.of("key", rule.key(), "expected", existing.getNodeType().name()));
        }
        if (rule.reason() == null || rule.reason().isBlank()) {
            throw ApiException.badRequest("config.reason_required", "Give the reason for the change");
        }
        Instant effective = rule.effectiveFrom() == null ? Instant.now() : rule.effectiveFrom();
        if (effective.isBefore(Instant.now().minus(Duration.ofMinutes(5)))) {
            throw ApiException.rule("config.no_retroactive", "A rule cannot take effect in the past");
        }
        UUID id = Ids.newId();
        jdbc.update("""
                INSERT INTO config_rule (id, institution_id, rule_key, rule_value, effective_from, reason,
                                         legal_reference, created_by)
                VALUES (?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?)
                """, id, rule.platform() ? null : TenantContext.requireInstitution(), rule.key(),
                rule.value().toString(), Timestamp.from(effective), rule.reason(), rule.legalReference(),
                TenantContext.userOrNull());
        audit.record("CONFIG_RULE_ADDED", "config_rule", id, Map.of("key", rule.key(), "value", existing),
                Map.of("key", rule.key(), "value", rule.value(), "effectiveFrom", effective.toString(),
                        "scope", rule.platform() ? "PLATFORM" : "INSTITUTION"));
        return id;
    }

    private JsonNode read(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
