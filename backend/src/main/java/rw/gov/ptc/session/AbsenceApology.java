package rw.gov.ptc.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import rw.gov.ptc.common.TenantEntity;

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
    public String kind = "ABSENT_WITH_PERMISSION";
    public String reason;
    public Instant submittedAt = Instant.now();
    public Instant clientRecordedAt;
    public boolean timely;
}
