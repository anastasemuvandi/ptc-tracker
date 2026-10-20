package rw.gov.ptc.auth.api;

/** Validates an access token. The only auth capability most of the application uses. */
public interface TokenVerifier {

    /**
     * Verifies signature (against the published JWKS), iss, aud, exp, nbf
     * with 60s skew, claim formats, and token_version against the user
     * record.
     *
     * @throws InvalidTokenException if any check fails
     */
    AuthPrincipal verify(String bearerToken);
}
