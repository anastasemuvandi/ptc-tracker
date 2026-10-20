package rw.gov.ptc.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import rw.gov.ptc.common.TenantEntity;

import java.time.Instant;
import java.util.UUID;

/** A Web Push subscription of a user's device. */
@Entity
@Table(name = "push_subscription")
public class PushSubscription extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID userId;
    public String endpoint;
    public String p256dh;
    public String authSecret;
    public String userAgent;
    public Instant createdAt = Instant.now();
}
