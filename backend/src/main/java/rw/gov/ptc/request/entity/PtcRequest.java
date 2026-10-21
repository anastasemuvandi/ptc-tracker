package rw.gov.ptc.request.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import rw.gov.ptc.common.TenantEntity;
import rw.gov.ptc.request.enums.RequestStatus;

import java.time.Instant;
import java.util.UUID;

/** A request from an employee outside the committee; once accepted it becomes a file. */
@Entity
@Table(name = "ptc_request")
public class PtcRequest extends TenantEntity {

    public String requestNumber;
    @Column(columnDefinition = "uuid")
    public UUID requesterId;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(columnDefinition = "char(1)")
    public String itemType;
    public String title;
    public String description;
    public String contractRef;
    public String tenderRef;
    @Enumerated(EnumType.STRING)
    public RequestStatus status = RequestStatus.DRAFT;
    public Instant declaredAt;
    public Instant submittedAt;
    @Column(columnDefinition = "uuid")
    public UUID decidedBy;
    public Instant decidedAt;
    public String decisionNote;
    @Column(columnDefinition = "uuid")
    public UUID committeeId;
    @Column(columnDefinition = "uuid")
    public UUID fileId;
    public Instant createdAt = Instant.now();
    @Version
    public Long version;
}
