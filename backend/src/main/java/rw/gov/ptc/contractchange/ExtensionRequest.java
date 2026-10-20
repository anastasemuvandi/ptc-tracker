package rw.gov.ptc.contractchange;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import rw.gov.ptc.common.TenantEntity;

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
    public String status = "OPEN";
    public LocalDate approvedCompletionDate;
    public Instant approvedAt;
    @Column(columnDefinition = "uuid")
    public UUID createdBy;
    public Instant createdAt = Instant.now();
    @Version
    public Long version;
}
