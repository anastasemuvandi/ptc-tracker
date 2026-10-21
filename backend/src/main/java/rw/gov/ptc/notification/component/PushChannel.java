package rw.gov.ptc.notification.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import nl.martijndwars.webpush.AbstractPushService;
import nl.martijndwars.webpush.Encoding;
import nl.martijndwars.webpush.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import rw.gov.ptc.notification.dto.Recipient;
import rw.gov.ptc.notification.entity.Notification;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Web Push (VAPID, aes128gcm). An enhancement only: failures are logged,
 * never retried, and email always goes out regardless.
 */
@Component
public class PushChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(PushChannel.class);

    /** Exposes the library's request preparation; we send with java.net.http. */
    static final class Signer extends AbstractPushService<Signer> {
        Signer(String publicKey, String privateKey, String subject) throws java.security.GeneralSecurityException {
            super(publicKey, privateKey, subject);
        }

        HttpRequest prepare(nl.martijndwars.webpush.Notification n) throws Exception {
            return prepareRequest(n, Encoding.AES128GCM);
        }
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Signer signer;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public PushChannel(JdbcTemplate jdbc, ObjectMapper mapper, VapidKeys keys) throws Exception {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.signer = new Signer(keys.publicKey(), keys.privateKey(), keys.subject());
    }

    @Override
    public String name() {
        return "push";
    }

    @Override
    public boolean guaranteed() {
        return false;
    }

    @Override
    public boolean deliver(Notification n, Recipient r) throws Exception {
        var subs = jdbc.queryForList(
                "SELECT id, endpoint, p256dh, auth_secret FROM push_subscription WHERE user_id = ?", r.userId());
        if (subs.isEmpty()) {
            return false;
        }
        byte[] payload = mapper.writeValueAsString(Map.of("title", n.title, "body", n.body, "link", n.link,
                "tag", n.kind)).getBytes(StandardCharsets.UTF_8);
        boolean any = false;
        for (Map<String, Object> s : subs) {
            try {
                var notification = new nl.martijndwars.webpush.Notification((String) s.get("endpoint"),
                        (String) s.get("p256dh"), (String) s.get("auth_secret"), payload);
                HttpRequest req = signer.prepare(notification);
                var builder = java.net.http.HttpRequest.newBuilder(URI.create(req.getUrl()))
                        .timeout(Duration.ofSeconds(15))
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(req.getBody()));
                req.getHeaders().forEach((k, v) -> {
                    if (!"content-length".equalsIgnoreCase(k) && !"host".equalsIgnoreCase(k)) {
                        builder.header(k, v);
                    }
                });
                int status = http.send(builder.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
                if (status == 404 || status == 410) {
                    jdbc.update("DELETE FROM push_subscription WHERE id = ?", (UUID) s.get("id"));
                } else if (status >= 200 && status < 300) {
                    any = true;
                } else {
                    log.info("Push service answered {} for subscription {}", status, s.get("id"));
                }
            } catch (Exception e) {
                log.info("Push delivery failed for subscription {}: {}", s.get("id"), e.toString());
            }
        }
        return any;
    }
}
