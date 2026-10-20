package rw.gov.ptc.common;

import java.security.SecureRandom;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * UUID helpers. New keys are UUIDv7 (RFC 9562): 48-bit Unix millisecond
 * timestamp followed by random bits, so inserts land at the right-hand edge
 * of B-tree indexes instead of fragmenting them.
 */
public final class Ids {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern CANONICAL =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private Ids() {
    }

    public static UUID newId() {
        long millis = System.currentTimeMillis();
        byte[] rand = new byte[10];
        RANDOM.nextBytes(rand);
        long msb = (millis & 0xFFFF_FFFF_FFFFL) << 16;
        msb |= 0x7000L;                                   // version 7
        msb |= ((rand[0] & 0x0FL) << 8) | (rand[1] & 0xFFL); // rand_a (12 bits)
        long lsb = 0;
        for (int i = 2; i < 10; i++) {
            lsb = (lsb << 8) | (rand[i] & 0xFFL);
        }
        lsb = (lsb & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L; // IETF variant
        return new UUID(msb, lsb);
    }

    public static boolean isCanonical(String value) {
        return value != null && CANONICAL.matcher(value).matches();
    }

    /** Strict parse: rejects the abbreviated forms {@link UUID#fromString} tolerates. */
    public static UUID parse(String value) {
        if (!isCanonical(value)) {
            throw ApiException.badRequest("invalid_uuid", "Malformed identifier");
        }
        return UUID.fromString(value);
    }
}
