package rw.gov.ptc.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import rw.gov.ptc.common.TenantEntity;

import java.time.Instant;
import java.util.UUID;

/** Attendance of one member at one session. */
@Entity
@Table(name = "attendance")
public class Attendance extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID sessionId;
    @Column(columnDefinition = "uuid")
    public UUID membershipId;
    @Column(columnDefinition = "uuid")
    public UUID userId;
    public String status;
    @Column(columnDefinition = "uuid")
    public UUID recordedBy;
    public Instant recordedAt = Instant.now();
    public Instant clientRecordedAt;
    @Version
    public Long version;
}
