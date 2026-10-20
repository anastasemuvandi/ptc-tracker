package rw.gov.ptc.minutes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import rw.gov.ptc.common.TenantEntity;

import java.time.Instant;
import java.util.UUID;

/** A member's electronic acknowledgement of a version of the minutes. */
@Entity
@Table(name = "minutes_acknowledgement")
public class MinutesAcknowledgement extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID minutesId;
    @Column(columnDefinition = "uuid")
    public UUID userId;
    public long minutesVersion;
    public Instant acknowledgedAt = Instant.now();
    public String comment;
}
