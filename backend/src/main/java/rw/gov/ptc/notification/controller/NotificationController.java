package rw.gov.ptc.notification.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rw.gov.ptc.audit.service.AuditService;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.common.Ids;
import rw.gov.ptc.common.Rows;
import rw.gov.ptc.notification.component.VapidKeys;
import rw.gov.ptc.notification.dto.SubscriptionRequest;
import rw.gov.ptc.security.Access;
import rw.gov.ptc.tenancy.TenantContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class NotificationController {

    private final JdbcTemplate jdbc;
    private final Access access;
    private final AuditService audit;
    private final VapidKeys vapid;

    public NotificationController(JdbcTemplate jdbc, Access access, AuditService audit, VapidKeys vapid) {
        this.jdbc = jdbc;
        this.access = access;
        this.audit = audit;
        this.vapid = vapid;
    }

    @GetMapping("/api/notifications")
    public List<Map<String, Object>> list(@RequestParam(defaultValue = "false") boolean unread,
                                          @RequestParam(defaultValue = "50") int limit) {
        return Rows.camel(jdbc.queryForList("""
                SELECT id, kind, title, body, link, entity_type, entity_id, created_at, read_at
                  FROM notification
                 WHERE user_id = ? AND (NOT ? OR read_at IS NULL)
                 ORDER BY created_at DESC LIMIT ?
                """, access.userId(), unread, Math.min(Math.max(limit, 1), 200)));
    }

    @GetMapping("/api/notifications/unread-count")
    public Map<String, Object> unreadCount() {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM notification WHERE user_id = ? AND read_at IS NULL",
                Integer.class, access.userId());
        return Map.of("unread", n == null ? 0 : n);
    }

    @PostMapping("/api/notifications/{id}/read")
    public ResponseEntity<Void> read(@PathVariable UUID id) {
        int n = jdbc.update("UPDATE notification SET read_at = coalesce(read_at, now()) WHERE id = ? AND user_id = ?",
                id, access.userId());
        if (n == 0) {
            throw ApiException.notFound("Notification");
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/notifications/read-all")
    public ResponseEntity<Void> readAll() {
        jdbc.update("UPDATE notification SET read_at = now() WHERE user_id = ? AND read_at IS NULL", access.userId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/push/vapid-public-key")
    public Map<String, String> vapidPublicKey() {
        return Map.of("publicKey", vapid.publicKey());
    }

    @PostMapping("/api/push/subscriptions")
    @Transactional
    public ResponseEntity<Void> subscribe(@RequestBody SubscriptionRequest req,
                                          @RequestHeader(value = "User-Agent", required = false) String userAgent) {
        if (req.endpoint() == null || !req.endpoint().startsWith("https://") || req.keys() == null
                || req.keys().get("p256dh") == null || req.keys().get("auth") == null) {
            throw ApiException.badRequest("push.invalid_subscription", "Invalid push subscription");
        }
        UUID user = access.userId();
        TenantContext.requireInstitution();
        jdbc.update("DELETE FROM push_subscription WHERE endpoint = ?", req.endpoint());
        UUID id = Ids.newId();
        jdbc.update("""
                INSERT INTO push_subscription (id, institution_id, user_id, endpoint, p256dh, auth_secret, user_agent)
                VALUES (?, app_current_institution(), ?, ?, ?, ?, ?)
                ON CONFLICT (endpoint) DO NOTHING
                """, id, user, req.endpoint(), req.keys().get("p256dh"), req.keys().get("auth"), userAgent);
        audit.record("PUSH_SUBSCRIBED", "push_subscription", id, null, Map.of("userAgent", String.valueOf(userAgent)));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/push/subscriptions")
    @Transactional
    public ResponseEntity<Void> unsubscribe(@RequestParam String endpoint) {
        List<UUID> ids = jdbc.queryForList("SELECT id FROM push_subscription WHERE endpoint = ? AND user_id = ?",
                UUID.class, endpoint, access.userId());
        for (UUID id : ids) {
            jdbc.update("DELETE FROM push_subscription WHERE id = ?", id);
            audit.record("PUSH_UNSUBSCRIBED", "push_subscription", id, null, null);
        }
        return ResponseEntity.noContent().build();
    }
}
