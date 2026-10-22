package rw.gov.ptc.auth.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import rw.gov.ptc.auth.api.AuthPrincipal;
import rw.gov.ptc.auth.api.CommitteeRole;
import rw.gov.ptc.auth.api.InstitutionRole;
import rw.gov.ptc.common.Ids;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * All SQL of the auth module, executed as the ptc_auth database role. No
 * other part of the application can read credentials or tokens.
 */
@Component
public class AuthStore {

    record UserRow(UUID id, UUID institutionId, String email, String fullName, String locale,
                   String status, int tokenVersion) {
    }

    record CredentialRow(UUID id, String passwordHash, int failedAttempts, Instant lockedUntil) {
    }

    record TotpRow(UUID id, String secretEncrypted, Instant confirmedAt, Long lastUsedStep) {
    }

    record RefreshRow(UUID id, UUID userId, UUID familyId, Instant issuedAt, Instant expiresAt,
                      Instant consumedAt, Instant revokedAt, boolean mfaVerified) {
    }

    record OneTimeRow(UUID id, UUID userId, String purpose, Instant expiresAt, Instant usedAt) {
    }

    record AdminRow(UUID id, String fullName, String email, String status, boolean mfaEnrolled) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;

    public AuthStore(@Qualifier("authDataSource") DataSource authDataSource, ObjectMapper mapper) {
        this.jdbc = new NamedParameterJdbcTemplate(authDataSource);
        this.tx = new TransactionTemplate(new DataSourceTransactionManager(authDataSource));
        this.mapper = mapper;
    }

    public <T> T inTransaction(Supplier<T> work) {
        return tx.execute(status -> work.get());
    }

    // ------------------------------------------------------------------ users

    private static final String USER_COLUMNS =
            "id, institution_id, email, full_name, locale, status, token_version";

    private UserRow mapUser(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new UserRow(rs.getObject("id", UUID.class), rs.getObject("institution_id", UUID.class),
                rs.getString("email"), rs.getString("full_name"), rs.getString("locale"),
                rs.getString("status"), rs.getInt("token_version"));
    }

    public Optional<UserRow> userByEmail(String email) {
        return jdbc.query("SELECT " + USER_COLUMNS + " FROM app_user WHERE lower(email) = lower(:e)",
                Map.of("e", email.trim()), (rs, i) -> mapUser(rs)).stream().findFirst();
    }

    public Optional<UserRow> user(UUID id) {
        return jdbc.query("SELECT " + USER_COLUMNS + " FROM app_user WHERE id = :id",
                Map.of("id", id), (rs, i) -> mapUser(rs)).stream().findFirst();
    }

    public record VersionRow(int tokenVersion, String status) {
    }

    public Optional<VersionRow> tokenVersion(UUID userId) {
        return jdbc.query("SELECT token_version, status FROM app_user WHERE id = :id", Map.of("id", userId),
                (rs, i) -> new VersionRow(rs.getInt(1), rs.getString(2))).stream().findFirst();
    }

    public boolean platformAdminExists() {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM user_role WHERE role = 'PLATFORM_ADMIN'",
                Map.of(), Integer.class);
        return n != null && n > 0;
    }

    public void bumpTokenVersion(UUID userId) {
        jdbc.update("UPDATE app_user SET token_version = token_version + 1 WHERE id = :id", Map.of("id", userId));
    }

    public void setUserStatus(UUID userId, String status) {
        jdbc.update("UPDATE app_user SET status = :s, token_version = token_version + 1 WHERE id = :id",
                Map.of("id", userId, "s", status));
    }

    public UUID insertUser(UUID institutionId, String email, String fullName, String jobTitle,
                           String officeHolderCode, boolean procurementOfficer, String locale) {
        UUID id = Ids.newId();
        jdbc.update("""
                INSERT INTO app_user (id, institution_id, email, full_name, job_title, office_holder_code,
                                      is_procurement_officer, locale, status)
                VALUES (:id, :inst, :email, :name, :title, :office, :po, :locale, 'INVITED')
                """, new MapSqlParameterSource()
                .addValue("id", id).addValue("inst", institutionId).addValue("email", email.trim())
                .addValue("name", fullName).addValue("title", jobTitle).addValue("office", officeHolderCode)
                .addValue("po", procurementOfficer).addValue("locale", locale == null ? "en" : locale));
        return id;
    }

    public Set<InstitutionRole> roles(UUID userId) {
        Set<InstitutionRole> roles = EnumSet.noneOf(InstitutionRole.class);
        jdbc.query("SELECT role FROM user_role WHERE user_id = :u", Map.of("u", userId),
                rs -> {
                    roles.add(InstitutionRole.valueOf(rs.getString(1)));
                });
        return roles;
    }

    public void replaceRoles(UUID userId, UUID institutionId, Set<InstitutionRole> roles, UUID grantedBy) {
        jdbc.update("DELETE FROM user_role WHERE user_id = :u", Map.of("u", userId));
        for (InstitutionRole r : roles) {
            jdbc.update("""
                    INSERT INTO user_role (id, user_id, institution_id, role, granted_by)
                    VALUES (:id, :u, :inst, :r, :by)
                    """, new MapSqlParameterSource().addValue("id", Ids.newId()).addValue("u", userId)
                    .addValue("inst", r == InstitutionRole.PLATFORM_ADMIN ? null : institutionId)
                    .addValue("r", r.name()).addValue("by", grantedBy));
        }
    }

    /** Institution administrators of one institution, for the platform administrator. */
    public List<AdminRow> institutionAdmins(UUID institutionId) {
        return jdbc.query("""
                SELECT u.id, u.full_name, u.email, u.status,
                       EXISTS (SELECT 1 FROM mfa_totp t WHERE t.user_id = u.id AND t.confirmed_at IS NOT NULL)
                  FROM app_user u
                  JOIN user_role r ON r.user_id = u.id AND r.role = 'INSTITUTION_ADMIN'
                 WHERE u.institution_id = :i
                 ORDER BY u.full_name
                """, Map.of("i", institutionId), (rs, i) -> new AdminRow(rs.getObject(1, UUID.class),
                rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5)));
    }

    /** Active committee seats, for the committees claim. */
    public List<AuthPrincipal.CommitteeGrant> committeeGrants(UUID userId, LocalDate today) {
        return jdbc.query("""
                SELECT m.committee_id, m.role FROM committee_membership m
                  JOIN committee c ON c.id = m.committee_id
                 WHERE m.user_id = :u AND m.status = 'ACTIVE' AND c.status = 'ACTIVE'
                   AND m.term_start <= :d AND m.term_end >= :d
                 ORDER BY c.code
                """, Map.of("u", userId, "d", today),
                (rs, i) -> new AuthPrincipal.CommitteeGrant(rs.getObject(1, UUID.class),
                        CommitteeRole.valueOf(rs.getString(2))));
    }

    /** Resolve a platform/institution configuration value at the current instant. */
    public Optional<JsonNode> config(UUID institutionId, String key) {
        return jdbc.query("""
                SELECT rule_value::text FROM config_rule
                 WHERE rule_key = :k AND (institution_id = :i OR institution_id IS NULL)
                   AND effective_from <= now()
                 ORDER BY (institution_id IS NULL), effective_from DESC
                 LIMIT 1
                """, new MapSqlParameterSource().addValue("k", key).addValue("i", institutionId),
                (rs, i) -> {
                    try {
                        return mapper.readTree(rs.getString(1));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }).stream().findFirst();
    }

    // ------------------------------------------------------------ credentials

    public Optional<CredentialRow> credential(UUID userId) {
        return jdbc.query("""
                SELECT id, password_hash, failed_attempts, locked_until FROM user_credential WHERE user_id = :u
                """, Map.of("u", userId), (rs, i) -> new CredentialRow(rs.getObject(1, UUID.class),
                rs.getString(2), rs.getInt(3), instant(rs.getTimestamp(4)))).stream().findFirst();
    }

    public void upsertPassword(UUID userId, String hash) {
        jdbc.update("""
                INSERT INTO user_credential (id, user_id, password_hash, password_changed_at, failed_attempts)
                VALUES (:id, :u, :h, now(), 0)
                ON CONFLICT (user_id) DO UPDATE
                   SET password_hash = excluded.password_hash, password_changed_at = now(),
                       failed_attempts = 0, locked_until = NULL
                """, Map.of("id", Ids.newId(), "u", userId, "h", hash));
    }

    public void updateHash(UUID userId, String hash) {
        jdbc.update("UPDATE user_credential SET password_hash = :h WHERE user_id = :u", Map.of("u", userId, "h", hash));
    }

    public void recordFailure(UUID userId, int attempts, Instant lockedUntil) {
        jdbc.update("""
                UPDATE user_credential SET failed_attempts = :a, last_failed_at = now(), locked_until = :l
                 WHERE user_id = :u
                """, new MapSqlParameterSource().addValue("u", userId).addValue("a", attempts)
                .addValue("l", lockedUntil == null ? null : Timestamp.from(lockedUntil)));
    }

    public void clearFailures(UUID userId) {
        jdbc.update("UPDATE user_credential SET failed_attempts = 0, locked_until = NULL WHERE user_id = :u",
                Map.of("u", userId));
    }

    // -------------------------------------------------------------------- MFA

    public Optional<TotpRow> totp(UUID userId) {
        return jdbc.query("SELECT id, secret_encrypted, confirmed_at, last_used_step FROM mfa_totp WHERE user_id = :u",
                Map.of("u", userId), (rs, i) -> new TotpRow(rs.getObject(1, UUID.class), rs.getString(2),
                        instant(rs.getTimestamp(3)), (Long) rs.getObject(4))).stream().findFirst();
    }

    public void replaceTotp(UUID userId, String secretEncrypted) {
        jdbc.update("DELETE FROM mfa_totp WHERE user_id = :u", Map.of("u", userId));
        jdbc.update("INSERT INTO mfa_totp (id, user_id, secret_encrypted) VALUES (:id, :u, :s)",
                Map.of("id", Ids.newId(), "u", userId, "s", secretEncrypted));
    }

    public void deleteMfa(UUID userId) {
        jdbc.update("DELETE FROM mfa_totp WHERE user_id = :u", Map.of("u", userId));
        jdbc.update("DELETE FROM mfa_recovery_code WHERE user_id = :u", Map.of("u", userId));
    }

    public void confirmTotp(UUID userId, long step) {
        jdbc.update("UPDATE mfa_totp SET confirmed_at = now(), last_used_step = :s WHERE user_id = :u",
                Map.of("u", userId, "s", step));
    }

    /** Records the used step; false if the step was already used (replay). */
    public boolean useTotpStep(UUID userId, long step) {
        return jdbc.update("""
                UPDATE mfa_totp SET last_used_step = :s
                 WHERE user_id = :u AND (last_used_step IS NULL OR last_used_step < :s)
                """, Map.of("u", userId, "s", step)) == 1;
    }

    public void replaceRecoveryCodes(UUID userId, List<String> hashes) {
        jdbc.update("DELETE FROM mfa_recovery_code WHERE user_id = :u", Map.of("u", userId));
        for (String h : hashes) {
            jdbc.update("INSERT INTO mfa_recovery_code (id, user_id, code_hash) VALUES (:id, :u, :h)",
                    Map.of("id", Ids.newId(), "u", userId, "h", h));
        }
    }

    public boolean useRecoveryCode(UUID userId, String hash) {
        return jdbc.update("""
                UPDATE mfa_recovery_code SET used_at = now()
                 WHERE user_id = :u AND code_hash = :h AND used_at IS NULL
                """, Map.of("u", userId, "h", hash)) == 1;
    }

    public int remainingRecoveryCodes(UUID userId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM mfa_recovery_code WHERE user_id = :u AND used_at IS NULL",
                Map.of("u", userId), Integer.class);
        return n == null ? 0 : n;
    }

    // --------------------------------------------------------- refresh tokens

    public void insertRefresh(UUID id, UUID userId, UUID familyId, String hash, Instant expiresAt,
                              boolean mfaVerified, String userAgent) {
        jdbc.update("""
                INSERT INTO refresh_token (id, user_id, family_id, token_hash, expires_at, mfa_verified, user_agent)
                VALUES (:id, :u, :f, :h, :e, :m, :ua)
                """, new MapSqlParameterSource().addValue("id", id).addValue("u", userId).addValue("f", familyId)
                .addValue("h", hash).addValue("e", Timestamp.from(expiresAt)).addValue("m", mfaVerified)
                .addValue("ua", userAgent));
    }

    /** Locks the row so two concurrent presentations cannot both rotate it. */
    public Optional<RefreshRow> refreshForUpdate(String hash) {
        return jdbc.query("""
                SELECT id, user_id, family_id, issued_at, expires_at, consumed_at, revoked_at, mfa_verified
                  FROM refresh_token WHERE token_hash = :h FOR UPDATE
                """, Map.of("h", hash), (rs, i) -> new RefreshRow(rs.getObject(1, UUID.class),
                rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), instant(rs.getTimestamp(4)),
                instant(rs.getTimestamp(5)), instant(rs.getTimestamp(6)), instant(rs.getTimestamp(7)),
                rs.getBoolean(8))).stream().findFirst();
    }

    public Instant familyStartedAt(UUID familyId) {
        return instant(jdbc.queryForObject("SELECT min(issued_at) FROM refresh_token WHERE family_id = :f",
                Map.of("f", familyId), Timestamp.class));
    }

    public void consumeRefresh(UUID id, UUID replacedBy) {
        jdbc.update("UPDATE refresh_token SET consumed_at = now(), replaced_by = :r WHERE id = :id",
                Map.of("id", id, "r", replacedBy));
    }

    public int revokeFamily(UUID familyId, String reason) {
        return jdbc.update("""
                UPDATE refresh_token SET revoked_at = now(), revoke_reason = :r
                 WHERE family_id = :f AND revoked_at IS NULL
                """, Map.of("f", familyId, "r", reason));
    }

    public int revokeAllForUser(UUID userId, String reason) {
        return jdbc.update("""
                UPDATE refresh_token SET revoked_at = now(), revoke_reason = :r
                 WHERE user_id = :u AND revoked_at IS NULL
                """, Map.of("u", userId, "r", reason));
    }

    public boolean familyActive(UUID familyId) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM refresh_token
                 WHERE family_id = :f AND revoked_at IS NULL AND consumed_at IS NULL AND expires_at > now()
                """, Map.of("f", familyId), Integer.class);
        return n != null && n > 0;
    }

    // -------------------------------------------------------- one-time tokens

    public void insertOneTime(UUID userId, String purpose, String hash, Instant expiresAt, UUID createdBy) {
        jdbc.update("UPDATE auth_one_time_token SET used_at = now() WHERE user_id = :u AND purpose = :p AND used_at IS NULL",
                Map.of("u", userId, "p", purpose));
        jdbc.update("""
                INSERT INTO auth_one_time_token (id, user_id, purpose, token_hash, expires_at, created_by)
                VALUES (:id, :u, :p, :h, :e, :by)
                """, new MapSqlParameterSource().addValue("id", Ids.newId()).addValue("u", userId)
                .addValue("p", purpose).addValue("h", hash).addValue("e", Timestamp.from(expiresAt))
                .addValue("by", createdBy));
    }

    public Optional<OneTimeRow> oneTimeForUpdate(String hash) {
        return jdbc.query("""
                SELECT id, user_id, purpose, expires_at, used_at FROM auth_one_time_token
                 WHERE token_hash = :h FOR UPDATE
                """, Map.of("h", hash), (rs, i) -> new OneTimeRow(rs.getObject(1, UUID.class),
                rs.getObject(2, UUID.class), rs.getString(3), instant(rs.getTimestamp(4)),
                instant(rs.getTimestamp(5)))).stream().findFirst();
    }

    public void useOneTime(UUID id) {
        jdbc.update("UPDATE auth_one_time_token SET used_at = now() WHERE id = :id", Map.of("id", id));
    }

    // ---------------------------------------------------------------- step-up

    public void insertStepUp(UUID userId, UUID sessionId, String method) {
        jdbc.update("INSERT INTO auth_step_up (id, user_id, session_id, method) VALUES (:id, :u, :s, :m)",
                Map.of("id", Ids.newId(), "u", userId, "s", sessionId, "m", method));
    }

    public Optional<Instant> lastStepUp(UUID userId, UUID sessionId) {
        return Optional.ofNullable(instant(jdbc.queryForObject(
                "SELECT max(verified_at) FROM auth_step_up WHERE user_id = :u AND session_id = :s",
                Map.of("u", userId, "s", sessionId), Timestamp.class)));
    }

    // ------------------------------------------------------------------ audit

    /** Auth events go to the same append-only trail as every other action. */
    public void audit(UUID institutionId, UUID actorId, String action, UUID entityId, Map<String, Object> details) {
        String ip = null;
        String ua = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            ip = attrs.getRequest().getRemoteAddr();
            ua = attrs.getRequest().getHeader("User-Agent");
        }
        try {
            jdbc.update("""
                    INSERT INTO audit_event (id, institution_id, actor_id, action, entity_type, entity_id,
                                             new_value, ip_address, user_agent)
                    VALUES (:id, :inst, :actor, :action, 'auth', :entity, CAST(:val AS jsonb), :ip, :ua)
                    """, new MapSqlParameterSource().addValue("id", Ids.newId()).addValue("inst", institutionId)
                    .addValue("actor", actorId).addValue("action", action).addValue("entity", entityId)
                    .addValue("val", details == null ? null : mapper.writeValueAsString(details))
                    .addValue("ip", ip).addValue("ua", ua));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
