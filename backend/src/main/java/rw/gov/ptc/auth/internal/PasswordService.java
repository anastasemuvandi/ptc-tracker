package rw.gov.ptc.auth.internal;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import rw.gov.ptc.common.ApiException;

import java.util.Map;

/**
 * Argon2id (OWASP parameters: 19 MiB, t=2, p=1) for all new hashes. BCrypt
 * (cost 12) is accepted only for verifying legacy hashes, which are
 * re-hashed with Argon2id on the next successful login.
 */
@Component
public class PasswordService {

    private final PasswordEncoder encoder;
    private final AuthProperties props;
    /** Pre-computed hash so unknown accounts cost the same time to reject. */
    private final String dummyHash;

    public PasswordService(AuthProperties props) {
        this.props = props;
        Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(16, 32, 1, 19_456, 2);
        DelegatingPasswordEncoder delegating = new DelegatingPasswordEncoder("argon2",
                Map.of("argon2", argon2, "bcrypt", new BCryptPasswordEncoder(12)));
        this.encoder = delegating;
        this.dummyHash = encoder.encode("timing-equaliser-not-a-password");
    }

    public String hash(String raw) {
        return encoder.encode(raw);
    }

    public boolean matches(String raw, String hash) {
        return raw != null && hash != null && encoder.matches(raw, hash);
    }

    public boolean needsUpgrade(String hash) {
        return encoder.upgradeEncoding(hash) || !hash.startsWith("{argon2}");
    }

    public void burnTime(String raw) {
        encoder.matches(raw == null ? "" : raw, dummyHash);
    }

    public void checkPolicy(String raw, String email) {
        if (raw == null || raw.length() < props.passwordMinLength()) {
            throw ApiException.badRequest("password.too_short", "Password is too short",
                    Map.of("min", props.passwordMinLength()));
        }
        if (raw.length() > 256) {
            throw ApiException.badRequest("password.too_long", "Password is too long");
        }
        int classes = 0;
        if (raw.chars().anyMatch(Character::isLowerCase)) classes++;
        if (raw.chars().anyMatch(Character::isUpperCase)) classes++;
        if (raw.chars().anyMatch(Character::isDigit)) classes++;
        if (raw.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) classes++;
        if (classes < props.passwordMinCharacterClasses()) {
            throw ApiException.badRequest("password.too_simple", "Use a mix of upper and lower case letters, digits and symbols",
                    Map.of("classes", props.passwordMinCharacterClasses()));
        }
        if (email != null) {
            String local = email.split("@")[0].toLowerCase();
            if (local.length() >= 4 && raw.toLowerCase().contains(local)) {
                throw ApiException.badRequest("password.contains_email", "Password must not contain your email name");
            }
        }
    }
}
