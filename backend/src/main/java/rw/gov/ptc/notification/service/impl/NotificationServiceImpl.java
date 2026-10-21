package rw.gov.ptc.notification.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import rw.gov.ptc.common.Ids;
import rw.gov.ptc.notification.component.NotificationChannel;
import rw.gov.ptc.notification.component.NotificationTemplates;
import rw.gov.ptc.notification.dto.Recipient;
import rw.gov.ptc.notification.entity.Notification;
import rw.gov.ptc.notification.service.NotificationService;
import rw.gov.ptc.tenancy.TenantContext;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private final JdbcTemplate jdbc;
    private final NotificationTemplates templates;
    private final List<NotificationChannel> channels;
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "ptc-notify");
        t.setDaemon(true);
        return t;
    });

    public NotificationServiceImpl(JdbcTemplate jdbc, NotificationTemplates templates, List<NotificationChannel> channels) {
        this.jdbc = jdbc;
        this.templates = templates;
        this.channels = channels;
    }

    @Override
    public Optional<UUID> notify(UUID userId, String kind, List<String> args, String link,
                                 String entityType, UUID entityId, String dedupeKey) {
        Optional<Recipient> recipient = recipient(userId);
        if (recipient.isEmpty()) {
            return Optional.empty();
        }
        NotificationTemplates.Rendered text = templates.render(kind, recipient.get().locale(), args);
        List<UUID> inserted = jdbc.queryForList("""
                INSERT INTO notification (id, institution_id, user_id, kind, title, body, link, entity_type, entity_id, dedupe_key)
                VALUES (?, app_current_institution(), ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id, dedupe_key) DO NOTHING
                RETURNING id
                """, UUID.class, Ids.newId(), userId, kind, text.title(), text.body(), link, entityType, entityId, dedupeKey);
        if (inserted.isEmpty()) {
            return Optional.empty();
        }
        UUID id = inserted.get(0);
        TenantContext.Scope scope = TenantContext.current();
        Runnable delivery = () -> executor.execute(() -> TenantContext.runAs(scope, () -> deliver(id)));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delivery.run();
                }
            });
        } else {
            delivery.run();
        }
        return Optional.of(id);
    }

    @Override
    public void deliver(UUID notificationId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, user_id, kind, title, body, link, email_sent_at FROM notification WHERE id = ?", notificationId);
        if (rows.isEmpty()) {
            return;
        }
        Map<String, Object> row = rows.get(0);
        Notification n = new Notification();
        n.id = notificationId;
        n.userId = (UUID) row.get("user_id");
        n.kind = (String) row.get("kind");
        n.title = (String) row.get("title");
        n.body = (String) row.get("body");
        n.link = (String) row.get("link");
        Recipient r = recipient(n.userId).orElse(null);
        if (r == null) {
            return;
        }
        boolean emailAlreadySent = row.get("email_sent_at") != null;
        for (NotificationChannel channel : channels) {
            if (channel.guaranteed() && emailAlreadySent) {
                continue;
            }
            try {
                boolean sent = channel.deliver(n, r);
                if (channel.guaranteed()) {
                    jdbc.update("UPDATE notification SET email_sent_at = now(), email_error = NULL WHERE id = ?", n.id);
                } else if (sent) {
                    jdbc.update("UPDATE notification SET push_sent_at = now() WHERE id = ?", n.id);
                }
            } catch (Exception e) {
                if (channel.guaranteed()) {
                    log.warn("{} delivery failed for notification {}: {}", channel.name(), n.id, e.toString());
                    jdbc.update("UPDATE notification SET email_error = ? WHERE id = ?",
                            truncate(e.toString(), 500), n.id);
                }
            }
        }
    }

    /** Retry guaranteed-channel (email) deliveries that failed, for up to three days. */
    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT2M")
    public void retryFailedEmails() {
        List<UUID> institutions = jdbc.queryForList("SELECT * FROM app_active_institution_ids()", UUID.class);
        for (UUID institution : institutions) {
            TenantContext.runAs(new TenantContext.Scope(institution, null, false), () -> {
                List<UUID> pending = jdbc.queryForList("""
                        SELECT id FROM notification
                         WHERE email_sent_at IS NULL AND created_at > ? AND created_at < ?
                         ORDER BY created_at LIMIT 200
                        """, UUID.class, Timestamp.from(Instant.now().minus(3, ChronoUnit.DAYS)),
                        Timestamp.from(Instant.now().minus(2, ChronoUnit.MINUTES)));
                pending.forEach(this::deliver);
            });
        }
    }

    private Optional<Recipient> recipient(UUID userId) {
        return jdbc.query("SELECT id, email, full_name, locale FROM app_user WHERE id = ? AND status = 'ACTIVE'",
                (rs, i) -> new Recipient(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4)),
                userId).stream().findFirst();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
