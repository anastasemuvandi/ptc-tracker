package rw.gov.ptc.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import rw.gov.ptc.common.TenantEntity;

import java.time.Instant;
import java.util.UUID;

/** In-app notification; email is the guaranteed channel, push an enhancement. */
@Entity
@Table(name = "notification")
public class Notification extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID userId;
    public String kind;
    public String title;
    public String body;
    public String link;
    public String entityType;
    @Column(columnDefinition = "uuid")
    public UUID entityId;
    public String dedupeKey;
    public Instant createdAt = Instant.now();
    public Instant readAt;
    public Instant emailSentAt;
    public String emailError;
    public Instant pushSentAt;
}
