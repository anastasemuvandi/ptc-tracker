package rw.gov.ptc.committee.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import rw.gov.ptc.committee.enums.MembershipStatus;
import rw.gov.ptc.common.TenantEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A seat on a committee for a term. Roles: CHAIRPERSON, VICE_CHAIRPERSON, SECRETARY, MEMBER. */
@Entity
@Table(name = "committee_membership")
public class Membership extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID committeeId;
    @Column(columnDefinition = "uuid")
    public UUID userId;
    public String role;
    public LocalDate termStart;
    public LocalDate termEnd;
    public int consecutiveTerm = 1;
    @Enumerated(EnumType.STRING)
    public MembershipStatus status = MembershipStatus.ACTIVE;
    public String appointmentRef;
    @Column(columnDefinition = "uuid")
    public UUID appointedBy;
    public Instant appointedAt = Instant.now();
    public Instant endedAt;
    public String endReason;
    @Column(columnDefinition = "uuid")
    public UUID replacementId;
    @Version
    public Long version;
}
