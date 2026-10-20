package rw.gov.ptc.auth.internal;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Instant;

/** RFC 6238 TOTP (HMAC-SHA1, 30 s step, 6 digits) with RFC 4648 base32 secrets. */
final class Totp {

    static final int STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final char[] B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    static String newSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32(bytes);
    }

    static String uri(String issuer, String account, String secret) {
        String label = URLEncoder.encode(issuer + ":" + account, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + label + "?secret=" + secret
                + "&issuer=" + URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20")
                + "&algorithm=SHA1&digits=6&period=30";
    }

    /**
     * @return the matched time step (for replay prevention), or -1. Accepts
     *         one step of drift either side.
     */
    static long verify(String secret, String code, Instant now) {
        if (code == null || !code.matches("\\d{6}")) {
            return -1;
        }
        long step = now.getEpochSecond() / STEP_SECONDS;
        byte[] key = unbase32(secret);
        for (long s = step - 1; s <= step + 1; s++) {
            if (constantTimeEquals(generate(key, s), code)) {
                return s;
            }
        }
        return -1;
    }

    static String generate(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] h = mac.doFinal(java.nio.ByteBuffer.allocate(8).putLong(step).array());
            int o = h[h.length - 1] & 0x0F;
            int bin = ((h[o] & 0x7F) << 24) | ((h[o + 1] & 0xFF) << 16) | ((h[o + 2] & 0xFF) << 8) | (h[o + 3] & 0xFF);
            return String.format("%0" + DIGITS + "d", bin % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String base32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(B32[(buffer >> (bits - 5)) & 31]);
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(B32[(buffer << (5 - bits)) & 31]);
        }
        return sb.toString();
    }

    static byte[] unbase32(String s) {
        String clean = s.replace("=", "").toUpperCase();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            int v = c >= 'A' && c <= 'Z' ? c - 'A' : c - '2' + 26;
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }
}
