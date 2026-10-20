package rw.gov.ptc.auth.internal;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** AES-256-GCM for secrets at rest (TOTP seeds) and keyed hashing for codes. */
@Component
public class SecretBox {

    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    public SecretBox(AuthProperties props) {
        byte[] raw;
        if (props.dataKey() != null && !props.dataKey().isBlank()) {
            raw = Base64.getDecoder().decode(props.dataKey().trim());
        } else {
            raw = loadOrCreate(Path.of(props.keysDir(), "data.key"));
        }
        if (raw.length != 32) {
            throw new IllegalStateException("ptc.auth.data-key must be 32 bytes (base64)");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(12 + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
            return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Keyed hash for high-entropy single-use codes (recovery codes). */
    public String mac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getEncoded(), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Unkeyed SHA-256 for opaque 256-bit tokens (refresh, invitation, reset). */
    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] loadOrCreate(Path file) {
        try {
            if (Files.exists(file)) {
                return Base64.getDecoder().decode(Files.readString(file).trim());
            }
            byte[] raw = new byte[32];
            RANDOM.nextBytes(raw);
            Files.createDirectories(file.getParent());
            KeyManager.writeSecret(file, Base64.getEncoder().encodeToString(raw));
            return raw;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
