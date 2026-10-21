package rw.gov.ptc.session.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import rw.gov.ptc.common.TenantEntity;
import rw.gov.ptc.session.enums.ApologyKind;

import java.time.Instant;
import java.util.UUID;

/** A member's apology for absence, addressed to the Chairperson and Secretary. */
@Entity
@Table(name = "absence_apology")
public class AbsenceApology extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID sessionId;
    @Column(columnDefinition = "uuid")
    public UUID userId;
    @Enumerated(EnumType.STRING)
    public ApologyKind kind = ApologyKind.ABSENT_WITH_PERMISSION;
    public String reason;
    public Instant submittedAt = Instant.now();
    public Instant clientRecordedAt;
    public boolean timely;
}
