package rw.gov.ptc.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.util.Base64;
import java.util.Map;

/**
 * VAPID key pair for Web Push. Taken from ptc.push.* when configured,
 * otherwise generated once into the keys volume (vapid.json).
 */
@Component
public class VapidKeys {

    private static final Logger log = LoggerFactory.getLogger(VapidKeys.class);

    private final String publicKey;
    private final String privateKey;
    private final String subject;

    public VapidKeys(@Value("${ptc.push.vapid-public-key:}") String publicKey,
                     @Value("${ptc.push.vapid-private-key:}") String privateKey,
                     @Value("${ptc.push.subject:mailto:ptc-tracker@localhost}") String subject,
                     @Value("${ptc.auth.keys-dir:./var/keys}") String keysDir) throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        this.subject = subject;
        if (!publicKey.isBlank() && !privateKey.isBlank()) {
            this.publicKey = publicKey;
            this.privateKey = privateKey;
            return;
        }
        Path file = Path.of(keysDir, "vapid.json");
        ObjectMapper mapper = new ObjectMapper();
        if (Files.exists(file)) {
            Map<?, ?> stored = mapper.readValue(file.toFile(), Map.class);
            this.publicKey = (String) stored.get("publicKey");
            this.privateKey = (String) stored.get("privateKey");
            return;
        }
        KeyPairGenerator gen = KeyPairGenerator.getInstance("ECDH", BouncyCastleProvider.PROVIDER_NAME);
        gen.initialize(ECNamedCurveTable.getParameterSpec("prime256v1"));
        KeyPair pair = gen.generateKeyPair();
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        this.publicKey = b64.encodeToString(Utils.encode((ECPublicKey) pair.getPublic()));
        this.privateKey = b64.encodeToString(Utils.encode((ECPrivateKey) pair.getPrivate()));
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling("vapid.json.tmp");
        Files.writeString(tmp, mapper.writeValueAsString(Map.of("publicKey", this.publicKey, "privateKey", this.privateKey)));
        Files.move(tmp, file, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        log.info("Generated VAPID key pair in {}; public key {}", file, this.publicKey);
    }

    public String publicKey() {
        return publicKey;
    }

    String privateKey() {
        return privateKey;
    }

    String subject() {
        return subject;
    }
}
