package rw.gov.ptc.auth.internal;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.stereotype.Component;
import rw.gov.ptc.auth.api.AuthPrincipal;
import rw.gov.ptc.auth.api.CommitteeRole;
import rw.gov.ptc.auth.api.InstitutionRole;
import rw.gov.ptc.auth.api.InvalidTokenException;
import rw.gov.ptc.auth.api.TokenVerifier;
import rw.gov.ptc.common.Ids;

import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Issues and validates RS256 access tokens (15 min) and short MFA challenge
 * tokens. Challenge tokens carry a different audience and type so they can
 * never be used as access tokens.
 */
@Component
public class TokenService implements TokenVerifier {

    static final JOSEObjectType ACCESS_TYPE = new JOSEObjectType("at+jwt");
    static final JOSEObjectType CHALLENGE_TYPE = new JOSEObjectType("mfa-challenge+jwt");

    private final AuthProperties props;
    private final KeyManager keys;
    private final AuthStore store;
    private final DefaultJWTProcessor<SecurityContext> accessProcessor;
    private final DefaultJWTProcessor<SecurityContext> challengeProcessor;

    public TokenService(AuthProperties props, KeyManager keys, AuthStore store) {
        this.props = props;
        this.keys = keys;
        this.store = store;
        JWKSource<SecurityContext> jwkSource = (selector, ctx) -> selector.select(keys.publicJwkSet());
        this.accessProcessor = processor(jwkSource, ACCESS_TYPE, props.audience(),
                Set.of("sub", "iat", "exp", "nbf", "jti", "token_version", "roles", "committees", "sid"));
        this.challengeProcessor = processor(jwkSource, CHALLENGE_TYPE, props.audience() + ":mfa",
                Set.of("sub", "exp", "jti", "purpose"));
    }

    private DefaultJWTProcessor<SecurityContext> processor(JWKSource<SecurityContext> source, JOSEObjectType type,
                                                           String audience, Set<String> required) {
        DefaultJWTProcessor<SecurityContext> p = new DefaultJWTProcessor<>();
        p.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier<>(type));
        p.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, source));
        DefaultJWTClaimsVerifier<SecurityContext> verifier = new DefaultJWTClaimsVerifier<>(
                audience, new JWTClaimsSet.Builder().issuer(props.issuer()).build(), required);
        verifier.setMaxClockSkew((int) props.clockSkew().toSeconds());
        p.setJWTClaimsSetVerifier(verifier);
        return p;
    }

    // ---------------------------------------------------------------- issue

    public String issueAccessToken(AuthStore.UserRow user, Set<InstitutionRole> roles,
                                   List<AuthPrincipal.CommitteeGrant> committees, UUID sessionId,
                                   boolean mfaVerified) {
        Instant now = Instant.now();
        List<Map<String, String>> committeeClaims = committees.stream()
                .map(g -> Map.of("committee_id", g.committeeId().toString(), "role", g.role().name()))
                .toList();
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(props.issuer())
                .subject(user.id().toString())
                .audience(props.audience())
                .issueTime(Date.from(now))
                .notBeforeTime(Date.from(now))
                .expirationTime(Date.from(now.plus(props.accessTokenTtl())))
                .jwtID(Ids.newId().toString())
                .claim("roles", roles.stream().map(Enum::name).sorted().toList())
                .claim("committees", committeeClaims)
                .claim("token_version", user.tokenVersion())
                .claim("sid", sessionId.toString())
                .claim("amr", mfaVerified ? List.of("pwd", "otp") : List.of("pwd"));
        if (user.institutionId() != null) {
            claims.claim("institution_id", user.institutionId().toString());
        }
        return sign(claims.build(), ACCESS_TYPE);
    }

    public String issueChallenge(UUID userId, String purpose) {
        Instant now = Instant.now();
        return sign(new JWTClaimsSet.Builder()
                .issuer(props.issuer())
                .subject(userId.toString())
                .audience(props.audience() + ":mfa")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(props.mfaChallengeTtl())))
                .jwtID(Ids.newId().toString())
                .claim("purpose", purpose)
                .build(), CHALLENGE_TYPE);
    }

    private String sign(JWTClaimsSet claims, JOSEObjectType type) {
        try {
            var key = keys.signingKey();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(key.getKeyID()).type(type).build(), claims);
            jwt.sign(new RSASSASigner(key));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Token signing failed", e);
        }
    }

    // --------------------------------------------------------------- verify

    @Override
    public AuthPrincipal verify(String token) {
        JWTClaimsSet claims;
        try {
            claims = accessProcessor.process(token, null);
        } catch (ParseException | BadJOSEException | JOSEException e) {
            throw new InvalidTokenException("Invalid access token: " + e.getMessage());
        }
        try {
            UUID userId = uuidClaim(claims.getSubject(), "sub");
            String inst = claims.getStringClaim("institution_id");
            UUID institutionId = inst == null ? null : uuidClaim(inst, "institution_id");
            UUID sessionId = uuidClaim(claims.getStringClaim("sid"), "sid");
            uuidClaim(claims.getJWTID(), "jti");
            Integer version = claims.getIntegerClaim("token_version");

            Set<InstitutionRole> roles = EnumSet.noneOf(InstitutionRole.class);
            for (String r : claims.getStringListClaim("roles")) {
                roles.add(InstitutionRole.valueOf(r));
            }
            List<AuthPrincipal.CommitteeGrant> grants = new ArrayList<>();
            Set<UUID> seen = new HashSet<>();
            for (Object o : claims.getListClaim("committees")) {
                if (!(o instanceof Map<?, ?> m)) {
                    throw new InvalidTokenException("Malformed committees claim");
                }
                UUID committeeId = uuidClaim((String) m.get("committee_id"), "committee_id");
                if (!seen.add(committeeId)) {
                    throw new InvalidTokenException("Duplicate committee claim");
                }
                grants.add(new AuthPrincipal.CommitteeGrant(committeeId,
                        CommitteeRole.valueOf((String) m.get("role"))));
            }
            if (institutionId == null && !roles.contains(InstitutionRole.PLATFORM_ADMIN)) {
                throw new InvalidTokenException("Missing institution_id");
            }

            // Immediate revocation: any change to roles, membership or status
            // increments token_version on the user record.
            var current = store.tokenVersion(userId)
                    .orElseThrow(() -> new InvalidTokenException("Unknown subject"));
            if (version == null || current.tokenVersion() != version || !"ACTIVE".equals(current.status())) {
                throw new InvalidTokenException("Stale token version");
            }
            List<String> amr = claims.getStringListClaim("amr");
            return new AuthPrincipal(userId, institutionId, Set.copyOf(roles), List.copyOf(grants),
                    version, sessionId, amr != null && amr.contains("otp"));
        } catch (ParseException | IllegalArgumentException | ClassCastException e) {
            throw new InvalidTokenException("Malformed claims: " + e.getMessage());
        }
    }

    /** @return the user id of a valid, unexpired challenge for the purpose. */
    public UUID verifyChallenge(String token, String purpose) {
        try {
            JWTClaimsSet claims = challengeProcessor.process(token, null);
            if (!purpose.equals(claims.getStringClaim("purpose"))) {
                throw new InvalidTokenException("Wrong challenge purpose");
            }
            return uuidClaim(claims.getSubject(), "sub");
        } catch (ParseException | BadJOSEException | JOSEException e) {
            throw new InvalidTokenException("Invalid challenge: " + e.getMessage());
        }
    }

    private static UUID uuidClaim(String value, String name) {
        if (!Ids.isCanonical(value)) {
            throw new InvalidTokenException("Claim " + name + " is not a well-formed UUID");
        }
        return UUID.fromString(value);
    }
}
