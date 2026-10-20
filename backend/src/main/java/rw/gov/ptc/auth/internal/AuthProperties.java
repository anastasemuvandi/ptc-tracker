package rw.gov.ptc.auth.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("ptc.auth")
public record AuthProperties(
        @DefaultValue("ptc-tracker") String issuer,
        @DefaultValue("ptc-tracker-api") String audience,
        @DefaultValue("15m") Duration accessTokenTtl,
        @DefaultValue("14d") Duration refreshTokenTtl,
        @DefaultValue("30d") Duration refreshAbsoluteTtl,
        @DefaultValue("60s") Duration clockSkew,
        /* Directory holding signing keys — a mounted volume, never the image. */
        String keysDir,
        @DefaultValue("false") boolean generateKeysIfMissing,
        @DefaultValue("90d") Duration keyRotationInterval,
        /* Old public keys stay in the JWKS for this long after rotation. */
        @DefaultValue("24h") Duration keyOverlap,
        /* Base64 AES-256 key encrypting TOTP secrets; generated into keysDir if blank. */
        String dataKey,
        @DefaultValue("ptc_rt") String refreshCookieName,
        @DefaultValue("true") boolean refreshCookieSecure,
        @DefaultValue("/api/auth") String refreshCookiePath,
        @DefaultValue("12") int passwordMinLength,
        @DefaultValue("3") int passwordMinCharacterClasses,
        @DefaultValue("5") int lockoutMaxAttempts,
        @DefaultValue("15m") Duration lockoutBaseDuration,
        @DefaultValue("24h") Duration lockoutMaxDuration,
        @DefaultValue("20") int loginAttemptsPerMinutePerIp,
        @DefaultValue("72h") Duration invitationTtl,
        @DefaultValue("30m") Duration passwordResetTtl,
        @DefaultValue("5m") Duration mfaChallengeTtl,
        @DefaultValue({"CBM", "CHAIRPERSON", "VICE_CHAIRPERSON", "SECRETARY"}) List<String> mfaMandatoryRoles,
        @DefaultValue("http://localhost:5173") String publicBaseUrl) {
}
