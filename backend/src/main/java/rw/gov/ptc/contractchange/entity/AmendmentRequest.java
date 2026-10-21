package rw.gov.ptc.contractchange.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import rw.gov.ptc.common.TenantEntity;
import rw.gov.ptc.contractchange.enums.ChangeRequestStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Type C request to amend a signed contract. Values in the contract currency. */
@Entity
@Table(name = "amendment_request")
public class AmendmentRequest extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID fileId;
    @Column(columnDefinition = "uuid")
    public UUID contractId;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(columnDefinition = "char(3)")
    public String currency;
    public BigDecimal requestedValue;
    public String description;
    public Boolean affectsSubstance;
    public String substanceJustification;
    public Boolean deliverablesChange;
    public Boolean perfSecurityExtend;
    public Boolean perfSecurityIncrease;
    public Boolean keyPersonnelChange;
    public Boolean replacementEqualOrBetter;
    @Column(columnDefinition = "uuid")
    public UUID linkedExtensionFileId;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public JsonNode assessmentSnapshot;
    @Enumerated(EnumType.STRING)
    public ChangeRequestStatus status = ChangeRequestStatus.OPEN;
    public BigDecimal approvedValue;
    public Instant approvedAt;
    @Column(columnDefinition = "uuid")
    public UUID createdBy;
    public Instant createdAt = Instant.now();
    @Version
    public Long version;
}
