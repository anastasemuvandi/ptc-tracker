package rw.gov.ptc.contractchange.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import rw.gov.ptc.common.TenantEntity;
import rw.gov.ptc.contractchange.enums.ChangeRequestStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Type D request to extend a contract completion date. */
@Entity
@Table(name = "extension_request")
public class ExtensionRequest extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID fileId;
    @Column(columnDefinition = "uuid")
    public UUID contractId;
    public LocalDate completionDateBefore;
    public LocalDate requestedCompletionDate;
    public String groundCode;
    public String groundDetails;
    public LocalDate requestReceivedOn;
    @Column(insertable = false, updatable = false)
    public Boolean receivedBeforeCompletion;
    public Boolean penaltiesConsidered;
    public String bidderFaultReasoning;
    public boolean hasCostClaim;
    @Column(columnDefinition = "uuid")
    public UUID linkedAmendmentFileId;
    public LocalDate forceMajeureStart;
    public LocalDate forceMajeureEnd;
    @Enumerated(EnumType.STRING)
    public ChangeRequestStatus status = ChangeRequestStatus.OPEN;
    public LocalDate approvedCompletionDate;
    public Instant approvedAt;
    @Column(columnDefinition = "uuid")
    public UUID createdBy;
    public Instant createdAt = Instant.now();
    @Version
    public Long version;
}
