package rw.gov.ptc.notification.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates in-app notifications (deduplicated per user and occurrence) and
 * delivers them through every channel. Email is guaranteed: failures are
 * recorded and retried; push is best effort.
 */
public interface NotificationService {

    /**
     * Notify a user in their language. No-op when the same dedupe key was
     * already used for this user, or the user is not active.
     *
     * @param link in-app landing route (one screen, one question, one action)
     * @return id of the new notification, empty when deduplicated
     */
    Optional<UUID> notify(UUID userId, String kind, List<String> args, String link,
                                 String entityType, UUID entityId, String dedupeKey);

    /** Deliver through all channels; records email outcome. Runs inside a tenant scope. */
    void deliver(UUID notificationId);
}
