package rw.gov.ptc.auth.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rw.gov.ptc.common.Ids;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.ParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * RS256 signing keys held as JWK files in a mounted directory
 * (ptc.auth.keys-dir). Private keys never live in the repository or image.
 *
 * <p>Rotation: a new key becomes the signing key; the previous one is marked
 * retired but stays in the published JWKS for the overlap period so tokens
 * signed just before rotation keep validating. Keys are re-read every minute
 * so all instances sharing the volume converge.
 */
@Component
public class KeyManager {

    private static final Logger log = LoggerFactory.getLogger(KeyManager.class);

    record StoredKey(RSAKey jwk, Instant createdAt, Instant retiredAt) {
    }

    private final AuthProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile List<StoredKey> keys = List.of();

    public KeyManager(AuthProperties props) {
        this.props = props;
        reload();
        if (activeKey() == null) {
            if (!props.generateKeysIfMissing()) {
                throw new IllegalStateException("No signing key in " + props.keysDir()
                        + " and ptc.auth.generate-keys-if-missing is false");
            }
            rotate();
        }
    }

    public synchronized void rotate() {
        try {
            Path dir = dir();
            Instant now = Instant.now();
            RSAKey jwk = new RSAKeyGenerator(3072)
                    .keyID(Ids.newId().toString())
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .generate();
            for (StoredKey k : keys) {
                if (k.retiredAt() == null) {
                    writeMeta(dir, k.jwk().getKeyID(), k.createdAt(), now);
                }
            }
            Path keyFile = dir.resolve(jwk.getKeyID() + ".jwk.json");
            writeSecret(keyFile, jwk.toJSONString());
            writeMeta(dir, jwk.getKeyID(), now, null);
            log.info("Rotated signing key; new kid {}", jwk.getKeyID());
            reload();
        } catch (JOSEException e) {
            throw new IllegalStateException("Key generation failed", e);
        }
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void reload() {
        Path dir = dir();
        List<StoredKey> loaded = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".jwk.json")).toList()) {
                RSAKey jwk = RSAKey.parse(Files.readString(f));
                Path metaFile = dir.resolve(jwk.getKeyID() + ".meta.json");
                Instant created = Instant.EPOCH;
                Instant retired = null;
                if (Files.exists(metaFile)) {
                    Map<?, ?> meta = mapper.readValue(metaFile.toFile(), Map.class);
                    created = Instant.parse((String) meta.get("createdAt"));
                    Object r = meta.get("retiredAt");
                    retired = r == null ? null : Instant.parse((String) r);
                }
                loaded.add(new StoredKey(jwk, created, retired));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (ParseException e) {
            throw new IllegalStateException("Unreadable key file in " + dir, e);
        }
        loaded.sort(Comparator.comparing(StoredKey::createdAt).reversed());
        keys = List.copyOf(loaded);
    }

    /** Daily check: rotate when the signing key is older than the configured interval. */
    @Scheduled(cron = "0 15 2 * * *", zone = "Africa/Kigali")
    public void rotateIfDue() {
        StoredKey active = activeKey();
        if (active == null || active.createdAt().plus(props.keyRotationInterval()).isBefore(Instant.now())) {
            rotate();
        }
    }

    public RSAKey signingKey() {
        StoredKey active = activeKey();
        if (active == null) {
            throw new IllegalStateException("No active signing key");
        }
        return active.jwk();
    }

    /** Public keys currently valid for verification (active + within overlap). */
    public JWKSet publicJwkSet() {
        Instant cutoff = Instant.now().minus(props.keyOverlap());
        return new JWKSet(keys.stream()
                .filter(k -> k.retiredAt() == null || k.retiredAt().isAfter(cutoff))
                .map(k -> (com.nimbusds.jose.jwk.JWK) k.jwk().toPublicJWK())
                .toList());
    }

    private StoredKey activeKey() {
        return keys.stream().filter(k -> k.retiredAt() == null).findFirst().orElse(null);
    }

    private Path dir() {
        if (props.keysDir() == null || props.keysDir().isBlank()) {
            throw new IllegalStateException("ptc.auth.keys-dir is not configured");
        }
        Path dir = Path.of(props.keysDir());
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return dir;
    }

    private void writeMeta(Path dir, String kid, Instant created, Instant retired) {
        var meta = new java.util.LinkedHashMap<String, Object>();
        meta.put("kid", kid);
        meta.put("createdAt", created.toString());
        meta.put("retiredAt", retired == null ? null : retired.toString());
        try {
            writeSecret(dir.resolve(kid + ".meta.json"), mapper.writeValueAsString(meta));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void writeSecret(Path file, String content) {
        try {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, content);
            try {
                Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // non-POSIX file system
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
