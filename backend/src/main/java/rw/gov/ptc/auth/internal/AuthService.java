package rw.gov.ptc.auth.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import rw.gov.ptc.auth.api.AuthMailer;
import rw.gov.ptc.auth.api.AuthPrincipal;
import rw.gov.ptc.auth.api.CommitteeRole;
import rw.gov.ptc.auth.api.InstitutionRole;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.common.Ids;
import rw.gov.ptc.common.Time;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Credential and session flows: login, MFA, refresh rotation, logout,
 * password reset/change, invitation acceptance, step-up.
 *
 * <p>Failure paths record their audit event in its own committed statement
 * before the exception is thrown, so a rejected attempt is never lost to a
 * rollback.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    public enum LoginStatus { OK, MFA_REQUIRED, MFA_ENROLMENT_REQUIRED }

    public record SessionTokens(String accessToken, long expiresIn, String refreshToken, Duration refreshMaxAge) {
    }

    public record LoginResult(LoginStatus status, SessionTokens tokens, String challengeToken) {
    }

    public record Enrolment(String secret, String otpauthUri) {
    }

    public record EnrolmentConfirmed(List<String> recoveryCodes, SessionTokens tokens) {
    }

    private final AuthProperties props;
    private final AuthStore store;
    private final PasswordService passwords;
    private final TokenService tokens;
    private final SecretBox box;
    private final LoginThrottle throttle;
    private final AuthMailer mailer;

    public AuthService(AuthProperties props, AuthStore store, PasswordService passwords, TokenService tokens,
                       SecretBox box, LoginThrottle throttle, AuthMailer mailer) {
        this.props = props;
        this.store = store;
        this.passwords = passwords;
        this.tokens = tokens;
        this.box = box;
        this.throttle = throttle;
        this.mailer = mailer;
    }

    // ----------------------------------------------------------------- login

    public LoginResult login(String email, String password, String clientIp, String userAgent) {
        if (!throttle.tryAcquire(clientIp)) {
            throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "auth.throttled",
                    "Too many attempts. Wait a minute and try again.", null);
        }
        var user = email == null ? null : store.userByEmail(email).orElse(null);
        if (user == null) {
            passwords.burnTime(password);
            store.audit(null, null, "LOGIN_FAILURE", null, Map.of("reason", "unknown_account"));
            throw invalidCredentials();
        }
        var cred = store.credential(user.id()).orElse(null);
        if (cred == null || !"ACTIVE".equals(user.status())) {
            passwords.burnTime(password);
            store.audit(user.institutionId(), user.id(), "LOGIN_FAILURE", user.id(), Map.of("reason", "account_" + user.status().toLowerCase()));
            throw invalidCredentials();
        }
        Instant now = Instant.now();
        if (cred.lockedUntil() != null && cred.lockedUntil().isAfter(now)) {
            passwords.burnTime(password);
            store.audit(user.institutionId(), user.id(), "LOGIN_FAILURE", user.id(), Map.of("reason", "locked"));
            throw locked(cred.lockedUntil());
        }
        if (!passwords.matches(password, cred.passwordHash())) {
            registerFailure(user, cred.failedAttempts(), "bad_password");
            throw invalidCredentials();
        }
        store.clearFailures(user.id());
        if (passwords.needsUpgrade(cred.passwordHash())) {
            store.updateHash(user.id(), passwords.hash(password));
        }

        var totp = store.totp(user.id()).filter(t -> t.confirmedAt() != null);
        if (totp.isPresent()) {
            store.audit(user.institutionId(), user.id(), "LOGIN_PASSWORD_OK", user.id(), Map.of("next", "mfa"));
            return new LoginResult(LoginStatus.MFA_REQUIRED, null, tokens.issueChallenge(user.id(), "mfa"));
        }
        if (mfaRequired(user)) {
            store.audit(user.institutionId(), user.id(), "LOGIN_PASSWORD_OK", user.id(), Map.of("next", "mfa_enrolment"));
            return new LoginResult(LoginStatus.MFA_ENROLMENT_REQUIRED, null, tokens.issueChallenge(user.id(), "enrol"));
        }
        SessionTokens session = issueSession(user, false, userAgent);
        store.audit(user.institutionId(), user.id(), "LOGIN_SUCCESS", user.id(), Map.of("mfa", false));
        return new LoginResult(LoginStatus.OK, session, null);
    }

    public SessionTokens verifyMfa(String challenge, String code, String recoveryCode, String userAgent) {
        UUID userId = tokens.verifyChallenge(challenge, "mfa");
        var user = activeUser(userId);
        if (!checkSecondFactor(user, code, recoveryCode)) {
            throw ApiException.unauthorized("auth.invalid_code", "The code is not valid");
        }
        SessionTokens session = issueSession(user, true, userAgent);
        store.audit(user.institutionId(), user.id(), "LOGIN_SUCCESS", user.id(),
                Map.of("mfa", true, "method", recoveryCode != null ? "recovery_code" : "totp"));
        return session;
    }

    // ------------------------------------------------------------ enrolment

    /** Start TOTP enrolment, either from a login challenge or for a signed-in user. */
    public Enrolment startEnrolment(UUID userId) {
        var user = activeUser(userId);
        if (store.totp(userId).map(t -> t.confirmedAt() != null).orElse(false)) {
            throw ApiException.conflict("auth.mfa_already_enrolled", "Two-factor authentication is already set up");
        }
        String secret = Totp.newSecret();
        store.replaceTotp(userId, box.encrypt(secret));
        return new Enrolment(secret, Totp.uri("PTC Tracker", user.email(), secret));
    }

    public UUID challengeUser(String challenge, String purpose) {
        return tokens.verifyChallenge(challenge, purpose);
    }

    public EnrolmentConfirmed confirmEnrolment(UUID userId, String code, boolean issueSession, String userAgent) {
        var user = activeUser(userId);
        var totp = store.totp(userId).orElseThrow(() ->
                ApiException.badRequest("auth.mfa_not_started", "Start enrolment first"));
        long step = Totp.verify(box.decrypt(totp.secretEncrypted()), code, Instant.now());
        if (step < 0) {
            store.audit(user.institutionId(), userId, "MFA_ENROLMENT_FAILURE", userId, null);
            throw ApiException.unauthorized("auth.invalid_code", "The code is not valid");
        }
        List<String> codes = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            String c = SecretBox.randomToken().replaceAll("[^A-Za-z0-9]", "").substring(0, 10).toUpperCase();
            codes.add(c.substring(0, 5) + "-" + c.substring(5));
            hashes.add(box.mac(c));
        }
        store.inTransaction(() -> {
            store.confirmTotp(userId, step);
            store.replaceRecoveryCodes(userId, hashes);
            store.bumpTokenVersion(userId);
            return null;
        });
        store.audit(user.institutionId(), userId, "MFA_ENROLLED", userId, Map.of("recovery_codes", codes.size()));
        SessionTokens session = issueSession ? issueSession(store.user(userId).orElseThrow(), true, userAgent) : null;
        return new EnrolmentConfirmed(codes, session);
    }

    // -------------------------------------------------------------- refresh

    private enum RefreshOutcome { ROTATED, UNKNOWN, REVOKED, EXPIRED, REUSED, INACTIVE, MFA_NEEDED }

    public SessionTokens refresh(String rawRefreshToken, String userAgent) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw ApiException.unauthorized("auth.no_session", "Not signed in");
        }
        String hash = SecretBox.sha256(rawRefreshToken);
        String newRaw = SecretBox.randomToken();
        UUID newId = Ids.newId();
        Object[] holder = new Object[2];
        RefreshOutcome outcome = store.inTransaction(() -> {
            var row = store.refreshForUpdate(hash).orElse(null);
            if (row == null) {
                return RefreshOutcome.UNKNOWN;
            }
            holder[0] = row;
            if (row.revokedAt() != null) {
                return RefreshOutcome.REVOKED;
            }
            if (row.consumedAt() != null) {
                // Presentation of a consumed token: treat as theft.
                store.revokeFamily(row.familyId(), "reuse_detected");
                store.bumpTokenVersion(row.userId());
                return RefreshOutcome.REUSED;
            }
            Instant now = Instant.now();
            if (row.expiresAt().isBefore(now)
                    || store.familyStartedAt(row.familyId()).plus(props.refreshAbsoluteTtl()).isBefore(now)) {
                return RefreshOutcome.EXPIRED;
            }
            var user = store.user(row.userId()).orElse(null);
            if (user == null || !"ACTIVE".equals(user.status())) {
                store.revokeFamily(row.familyId(), "account_inactive");
                return RefreshOutcome.INACTIVE;
            }
            if (!row.mfaVerified() && (mfaRequired(user) || store.totp(user.id()).map(t -> t.confirmedAt() != null).orElse(false))) {
                store.revokeFamily(row.familyId(), "mfa_now_required");
                return RefreshOutcome.MFA_NEEDED;
            }
            store.insertRefresh(newId, row.userId(), row.familyId(), SecretBox.sha256(newRaw),
                    now.plus(props.refreshTokenTtl()), row.mfaVerified(), userAgent);
            store.consumeRefresh(row.id(), newId);
            holder[1] = user;
            return RefreshOutcome.ROTATED;
        });

        AuthStore.RefreshRow row = (AuthStore.RefreshRow) holder[0];
        switch (outcome) {
            case ROTATED -> {
                AuthStore.UserRow user = (AuthStore.UserRow) holder[1];
                var roles = store.roles(user.id());
                var grants = store.committeeGrants(user.id(), Time.today());
                String access = tokens.issueAccessToken(user, roles, grants, row.familyId(), row.mfaVerified());
                return new SessionTokens(access, props.accessTokenTtl().toSeconds(), newRaw, props.refreshTokenTtl());
            }
            case REUSED -> {
                var user = store.user(row.userId()).orElseThrow();
                store.audit(user.institutionId(), user.id(), "TOKEN_FAMILY_REVOKED", row.familyId(),
                        Map.of("reason", "refresh_token_reuse"));
                log.warn("Refresh token reuse detected for user {}; family {} revoked", user.id(), row.familyId());
                throw ApiException.unauthorized("auth.session_revoked",
                        "For your security this session was ended. Please sign in again.");
            }
            case MFA_NEEDED -> throw ApiException.unauthorized("auth.mfa_required",
                    "Two-factor authentication is now required. Please sign in again.");
            default -> throw ApiException.unauthorized("auth.no_session", "Not signed in");
        }
    }

    public void logout(String rawRefreshToken, UUID actor) {
        if (rawRefreshToken == null) {
            return;
        }
        store.inTransaction(() -> {
            store.refreshForUpdate(SecretBox.sha256(rawRefreshToken)).ifPresent(row -> {
                store.revokeFamily(row.familyId(), "logout");
                var user = store.user(row.userId()).orElseThrow();
                store.audit(user.institutionId(), user.id(), "LOGOUT", row.familyId(), null);
            });
            return null;
        });
    }

    // -------------------------------------------------------------- step-up

    public Instant stepUp(AuthPrincipal principal, String code, String password) {
        var user = activeUser(principal.userId());
        boolean hasTotp = store.totp(user.id()).map(t -> t.confirmedAt() != null).orElse(false);
        boolean ok;
        String method;
        if (hasTotp) {
            method = "TOTP";
            ok = checkSecondFactor(user, code, null);
        } else {
            method = "PASSWORD";
            var cred = store.credential(user.id()).orElseThrow();
            if (cred.lockedUntil() != null && cred.lockedUntil().isAfter(Instant.now())) {
                throw locked(cred.lockedUntil());
            }
            ok = passwords.matches(password, cred.passwordHash());
            if (!ok) {
                registerFailure(user, cred.failedAttempts(), "step_up_bad_password");
            } else {
                store.clearFailures(user.id());
            }
        }
        if (!ok) {
            store.audit(user.institutionId(), user.id(), "STEP_UP_FAILURE", principal.sessionId(), Map.of("method", method));
            throw ApiException.unauthorized("auth.invalid_code", hasTotp ? "The code is not valid" : "The password is not correct");
        }
        store.insertStepUp(user.id(), principal.sessionId(), method);
        store.audit(user.institutionId(), user.id(), "STEP_UP_SUCCESS", principal.sessionId(), Map.of("method", method));
        return Instant.now();
    }

    // ------------------------------------------------- invitation & password

    public void acceptInvitation(String rawToken, String newPassword) {
        String hash = SecretBox.sha256(rawToken == null ? "" : rawToken);
        AuthStore.UserRow user = store.inTransaction(() -> {
            var t = store.oneTimeForUpdate(hash)
                    .filter(r -> "INVITE".equals(r.purpose()) && r.usedAt() == null && r.expiresAt().isAfter(Instant.now()))
                    .orElseThrow(() -> ApiException.badRequest("auth.invalid_link", "This link is invalid or has expired"));
            var u = store.user(t.userId()).orElseThrow();
            if (!"INVITED".equals(u.status())) {
                throw ApiException.badRequest("auth.invalid_link", "This link is invalid or has expired");
            }
            passwords.checkPolicy(newPassword, u.email());
            store.upsertPassword(u.id(), passwords.hash(newPassword));
            store.useOneTime(t.id());
            store.setUserStatus(u.id(), "ACTIVE");
            return u;
        });
        store.audit(user.institutionId(), user.id(), "INVITATION_ACCEPTED", user.id(), null);
    }

    public void forgotPassword(String email) {
        store.userByEmail(email == null ? "" : email).filter(u -> "ACTIVE".equals(u.status())).ifPresentOrElse(u -> {
            String raw = SecretBox.randomToken();
            store.insertOneTime(u.id(), "PASSWORD_RESET", SecretBox.sha256(raw),
                    Instant.now().plus(props.passwordResetTtl()), null);
            store.audit(u.institutionId(), u.id(), "PASSWORD_RESET_REQUESTED", u.id(), null);
            mailer.sendPasswordReset(u.id(), u.email(), u.fullName(), u.locale(),
                    props.publicBaseUrl() + "/reset-password?token=" + raw);
        }, () -> passwords.burnTime(email));
    }

    public void resetPassword(String rawToken, String newPassword) {
        String hash = SecretBox.sha256(rawToken == null ? "" : rawToken);
        AuthStore.UserRow user = store.inTransaction(() -> {
            var t = store.oneTimeForUpdate(hash)
                    .filter(r -> "PASSWORD_RESET".equals(r.purpose()) && r.usedAt() == null
                            && r.expiresAt().isAfter(Instant.now()))
                    .orElseThrow(() -> ApiException.badRequest("auth.invalid_link", "This link is invalid or has expired"));
            var u = store.user(t.userId()).orElseThrow();
            passwords.checkPolicy(newPassword, u.email());
            store.upsertPassword(u.id(), passwords.hash(newPassword));
            store.useOneTime(t.id());
            store.revokeAllForUser(u.id(), "password_reset");
            store.bumpTokenVersion(u.id());
            return u;
        });
        store.audit(user.institutionId(), user.id(), "PASSWORD_CHANGED", user.id(), Map.of("via", "reset"));
        mailer.sendSecurityNotice(user.id(), user.email(), user.fullName(), user.locale(),
                "Your PTC Tracker password was reset",
                "Your password was reset and all sessions were signed out. If this was not you, contact your administrator.");
    }

    public void changePassword(AuthPrincipal principal, String current, String next) {
        var user = activeUser(principal.userId());
        var cred = store.credential(user.id()).orElseThrow();
        if (!passwords.matches(current, cred.passwordHash())) {
            registerFailure(user, cred.failedAttempts(), "change_bad_password");
            throw ApiException.unauthorized("auth.invalid_credentials", "Current password is not correct");
        }
        passwords.checkPolicy(next, user.email());
        store.upsertPassword(user.id(), passwords.hash(next));
        store.audit(user.institutionId(), user.id(), "PASSWORD_CHANGED", user.id(), Map.of("via", "change"));
    }

    public Map<String, Object> mfaStatus(UUID userId) {
        var user = activeUser(userId);
        boolean enrolled = store.totp(userId).map(t -> t.confirmedAt() != null).orElse(false);
        return Map.of("enrolled", enrolled, "required", mfaRequired(user),
                "recoveryCodesRemaining", enrolled ? store.remainingRecoveryCodes(userId) : 0);
    }

    // -------------------------------------------------------------- helpers

    SessionTokens issueSession(AuthStore.UserRow user, boolean mfaVerified, String userAgent) {
        UUID familyId = Ids.newId();
        String raw = SecretBox.randomToken();
        store.insertRefresh(Ids.newId(), user.id(), familyId, SecretBox.sha256(raw),
                Instant.now().plus(props.refreshTokenTtl()), mfaVerified, userAgent);
        Set<InstitutionRole> roles = store.roles(user.id());
        var grants = store.committeeGrants(user.id(), Time.today());
        String access = tokens.issueAccessToken(user, roles, grants, familyId, mfaVerified);
        return new SessionTokens(access, props.accessTokenTtl().toSeconds(), raw, props.refreshTokenTtl());
    }

    /** MFA is mandatory for administrators, the CBM and committee officers; configurable for members. */
    boolean mfaRequired(AuthStore.UserRow user) {
        Set<String> mandatory = Set.copyOf(props.mfaMandatoryRoles());
        for (InstitutionRole r : store.roles(user.id())) {
            if (mandatory.contains(r.name())) {
                return true;
            }
        }
        var grants = store.committeeGrants(user.id(), Time.today());
        for (var g : grants) {
            if (mandatory.contains(g.role().name())) {
                return true;
            }
        }
        if (!grants.isEmpty() && grants.stream().anyMatch(g -> g.role() == CommitteeRole.MEMBER)) {
            return store.config(user.institutionId(), "mfa.memberRequired")
                    .map(n -> n.asBoolean(false)).orElse(false);
        }
        return false;
    }

    private boolean checkSecondFactor(AuthStore.UserRow user, String code, String recoveryCode) {
        var cred = store.credential(user.id()).orElseThrow();
        if (cred.lockedUntil() != null && cred.lockedUntil().isAfter(Instant.now())) {
            throw locked(cred.lockedUntil());
        }
        boolean ok = false;
        if (recoveryCode != null && !recoveryCode.isBlank()) {
            ok = store.useRecoveryCode(user.id(), box.mac(recoveryCode.replace("-", "").trim().toUpperCase()));
            if (ok) {
                store.audit(user.institutionId(), user.id(), "MFA_RECOVERY_CODE_USED", user.id(),
                        Map.of("remaining", store.remainingRecoveryCodes(user.id())));
            }
        } else {
            var totp = store.totp(user.id()).filter(t -> t.confirmedAt() != null).orElse(null);
            if (totp != null) {
                long step = Totp.verify(box.decrypt(totp.secretEncrypted()), code, Instant.now());
                ok = step >= 0 && store.useTotpStep(user.id(), step);
            }
        }
        if (ok) {
            store.clearFailures(user.id());
        } else {
            registerFailure(user, cred.failedAttempts(), "bad_second_factor");
        }
        return ok;
    }

    /** Exponential lockout once the threshold is crossed: base, 2×base, 4×base … capped. */
    private void registerFailure(AuthStore.UserRow user, int previousAttempts, String reason) {
        int attempts = previousAttempts + 1;
        Instant lockedUntil = null;
        if (attempts >= props.lockoutMaxAttempts()) {
            int exponent = Math.min(attempts - props.lockoutMaxAttempts(), 10);
            Duration lock = props.lockoutBaseDuration().multipliedBy(1L << exponent);
            if (lock.compareTo(props.lockoutMaxDuration()) > 0) {
                lock = props.lockoutMaxDuration();
            }
            lockedUntil = Instant.now().plus(lock);
        }
        store.recordFailure(user.id(), attempts, lockedUntil);
        store.audit(user.institutionId(), user.id(), "LOGIN_FAILURE", user.id(), Map.of("reason", reason, "attempts", attempts));
        if (lockedUntil != null) {
            store.audit(user.institutionId(), user.id(), "ACCOUNT_LOCKED", user.id(),
                    Map.of("until", lockedUntil.toString(), "attempts", attempts));
        }
    }

    private AuthStore.UserRow activeUser(UUID userId) {
        return store.user(userId).filter(u -> "ACTIVE".equals(u.status()))
                .orElseThrow(() -> ApiException.unauthorized("auth.no_session", "Not signed in"));
    }

    private static ApiException invalidCredentials() {
        return ApiException.unauthorized("auth.invalid_credentials", "Email or password is incorrect");
    }

    private static ApiException locked(Instant until) {
        return new ApiException(org.springframework.http.HttpStatus.LOCKED, "auth.locked",
                "Too many failed attempts. The account is temporarily locked.", Map.of("until", until.toString()));
    }
}
