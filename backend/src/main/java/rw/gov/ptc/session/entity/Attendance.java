package rw.gov.ptc.session.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import rw.gov.ptc.common.TenantEntity;
import rw.gov.ptc.session.enums.AttendanceStatus;

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
    @Enumerated(EnumType.STRING)
    public AttendanceStatus status;
    @Column(columnDefinition = "uuid")
    public UUID recordedBy;
    public Instant recordedAt = Instant.now();
    public Instant clientRecordedAt;
    @Version
    public Long version;
}
