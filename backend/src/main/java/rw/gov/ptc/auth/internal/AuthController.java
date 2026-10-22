package rw.gov.ptc.auth.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import rw.gov.ptc.auth.api.AuthPrincipal;
import rw.gov.ptc.auth.api.InstitutionRole;
import rw.gov.ptc.common.ApiException;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP surface of the auth module. The access token is returned in the body
 * (the client holds it in memory only); the refresh token only ever travels
 * in an HttpOnly, Secure, SameSite=Strict cookie scoped to /api/auth.
 * Cookie-authenticated endpoints additionally require the X-PTC-Client
 * header, which a cross-site form cannot send.
 */
@RestController
public class AuthController {

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record MfaVerifyRequest(@NotBlank String challenge, String code, String recoveryCode) {
    }

    public record EnrolRequest(String challenge, String code) {
    }

    public record StepUpRequest(String code, String password) {
    }

    public record TokenPasswordRequest(@NotBlank String token, @NotBlank String password) {
    }

    public record EmailRequest(String email) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    private final AuthService auth;
    private final AuthProperties props;
    private final KeyManager keys;
    private final AuthStore store;
    private final IdentityAdminService identity;
    private final StepUpService stepUp;

    public AuthController(AuthService auth, AuthProperties props, KeyManager keys, AuthStore store,
                          IdentityAdminService identity, StepUpService stepUp) {
        this.auth = auth;
        this.props = props;
        this.keys = keys;
        this.store = store;
        this.identity = identity;
        this.stepUp = stepUp;
    }

    @GetMapping(value = {"/.well-known/jwks.json", "/api/auth/jwks"}, produces = "application/json")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                .body(keys.publicJwkSet().toJSONObject(true));
    }

    @PostMapping("/api/auth/login")
    public Map<String, Object> login(@RequestBody @jakarta.validation.Valid LoginRequest req,
                                     HttpServletRequest http, HttpServletResponse res) {
        var result = auth.login(req.email(), req.password(), http.getRemoteAddr(), http.getHeader("User-Agent"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", result.status().name());
        if (result.tokens() != null) {
            body.putAll(session(result.tokens(), res));
        } else {
            body.put("challenge", result.challengeToken());
        }
        return body;
    }

    @PostMapping("/api/auth/mfa/verify")
    public Map<String, Object> verifyMfa(@RequestBody @jakarta.validation.Valid MfaVerifyRequest req,
                                         HttpServletRequest http, HttpServletResponse res) {
        return session(auth.verifyMfa(req.challenge(), req.code(), req.recoveryCode(), http.getHeader("User-Agent")), res);
    }

    @PostMapping("/api/auth/mfa/enrol/start")
    public AuthService.Enrolment startEnrolment(@RequestBody EnrolRequest req,
                                                @AuthenticationPrincipal AuthPrincipal principal) {
        return auth.startEnrolment(enrolmentUser(req, principal));
    }

    @PostMapping("/api/auth/mfa/enrol/confirm")
    public Map<String, Object> confirmEnrolment(@RequestBody EnrolRequest req,
                                                @AuthenticationPrincipal AuthPrincipal principal,
                                                HttpServletRequest http, HttpServletResponse res) {
        boolean viaChallenge = principal == null;
        var confirmed = auth.confirmEnrolment(enrolmentUser(req, principal), req.code(), viaChallenge,
                http.getHeader("User-Agent"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("recoveryCodes", confirmed.recoveryCodes());
        if (confirmed.tokens() != null) {
            body.putAll(session(confirmed.tokens(), res));
        }
        return body;
    }

    @GetMapping("/api/auth/mfa/status")
    public Map<String, Object> mfaStatus(@AuthenticationPrincipal AuthPrincipal principal) {
        return auth.mfaStatus(require(principal).userId());
    }

    @PostMapping("/api/auth/refresh")
    public Map<String, Object> refresh(@RequestHeader("X-PTC-Client") String client,
                                       @CookieValue(name = "${ptc.auth.refresh-cookie-name:ptc_rt}", required = false) String refreshToken,
                                       HttpServletRequest http, HttpServletResponse res) {
        try {
            return session(auth.refresh(refreshToken, http.getHeader("User-Agent")), res);
        } catch (ApiException e) {
            clearCookie(res);
            throw e;
        }
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<Void> logout(@RequestHeader("X-PTC-Client") String client,
                                       @CookieValue(name = "${ptc.auth.refresh-cookie-name:ptc_rt}", required = false) String refreshToken,
                                       @AuthenticationPrincipal AuthPrincipal principal, HttpServletResponse res) {
        auth.logout(refreshToken, principal == null ? null : principal.userId());
        clearCookie(res);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/auth/step-up")
    public Map<String, Object> stepUp(@RequestBody StepUpRequest req, @AuthenticationPrincipal AuthPrincipal principal) {
        Instant at = auth.stepUp(require(principal), req.code(), req.password());
        return Map.of("verifiedAt", at.toString());
    }

    @PostMapping("/api/auth/invitations/accept")
    public ResponseEntity<Void> acceptInvitation(@RequestBody @jakarta.validation.Valid TokenPasswordRequest req) {
        auth.acceptInvitation(req.token(), req.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/auth/password/forgot")
    public ResponseEntity<Void> forgot(@RequestBody EmailRequest req, HttpServletRequest http) {
        auth.forgotPassword(req.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/api/auth/password/reset")
    public ResponseEntity<Void> reset(@RequestBody @jakarta.validation.Valid TokenPasswordRequest req) {
        auth.resetPassword(req.token(), req.password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/auth/password/change")
    public ResponseEntity<Void> change(@RequestBody @jakarta.validation.Valid ChangePasswordRequest req,
                                       @AuthenticationPrincipal AuthPrincipal principal) {
        auth.changePassword(require(principal), req.currentPassword(), req.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/platform/keys/rotate")
    public Map<String, Object> rotate(@AuthenticationPrincipal AuthPrincipal principal) {
        requirePlatformAdmin(principal);
        keys.rotate();
        return Map.of("kid", keys.signingKey().getKeyID());
    }

    @GetMapping("/api/platform/institutions/{id}/admins")
    public List<AuthStore.AdminRow> institutionAdmins(@PathVariable UUID id,
                                                      @AuthenticationPrincipal AuthPrincipal principal) {
        requirePlatformAdmin(principal);
        return store.institutionAdmins(id);
    }

    @PostMapping("/api/platform/institutions/{id}/admins/{userId}/reset-mfa")
    public ResponseEntity<Void> resetInstitutionAdminMfa(@PathVariable UUID id, @PathVariable UUID userId,
                                                         @AuthenticationPrincipal AuthPrincipal principal) {
        requirePlatformAdmin(principal);
        stepUp.requireRecent(principal, "MFA_RESET");
        identity.resetInstitutionAdminMfa(id, userId, principal.userId());
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------

    private UUID enrolmentUser(EnrolRequest req, AuthPrincipal principal) {
        if (principal != null) {
            return principal.userId();
        }
        if (req.challenge() == null) {
            throw ApiException.unauthorized("auth.no_session", "Not signed in");
        }
        return auth.challengeUser(req.challenge(), "enrol");
    }

    private Map<String, Object> session(AuthService.SessionTokens tokens, HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, cookie(tokens.refreshToken(), tokens.refreshMaxAge()).toString());
        res.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        return Map.of("status", "OK", "accessToken", tokens.accessToken(), "expiresIn", tokens.expiresIn(),
                "tokenType", "Bearer");
    }

    /** Persistent (Max-Age) so it survives relaunch of the installed PWA. */
    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(props.refreshCookieName(), value)
                .httpOnly(true)
                .secure(props.refreshCookieSecure())
                .sameSite("Strict")
                .path(props.refreshCookiePath())
                .maxAge(maxAge)
                .build();
    }

    private void clearCookie(HttpServletResponse res) {
        res.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    private static void requirePlatformAdmin(AuthPrincipal principal) {
        if (!require(principal).has(InstitutionRole.PLATFORM_ADMIN)) {
            throw ApiException.forbidden("forbidden", "Platform administrators only");
        }
    }

    private static AuthPrincipal require(AuthPrincipal principal) {
        if (principal == null) {
            throw ApiException.unauthorized("auth.no_session", "Not signed in");
        }
        return principal;
    }
}
