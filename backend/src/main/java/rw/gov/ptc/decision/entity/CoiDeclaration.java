package rw.gov.ptc.decision.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import rw.gov.ptc.common.TenantEntity;

import java.time.Instant;
import java.util.UUID;

/** Conflict-of-interest declaration by an attending member before a file is opened. */
@Entity
@Table(name = "coi_declaration")
public class CoiDeclaration extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID sessionId;
    @Column(columnDefinition = "uuid")
    public UUID agendaItemId;
    @Column(columnDefinition = "uuid")
    public UUID fileId;
    @Column(columnDefinition = "uuid")
    public UUID userId;
    public boolean hasInterest;
    public String interestKind;
    public String details;
    public Instant declaredAt = Instant.now();
    public Instant stepUpVerifiedAt;
}
