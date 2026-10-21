package rw.gov.ptc.evaluation.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import rw.gov.ptc.common.TenantEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Administrative compliance cure request to the first-ranked bidder (A4). */
@Entity
@Table(name = "admin_cure_request")
public class AdminCureRequest extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID fileId;
    public String bidderRef;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public JsonNode outstandingItems;
    public LocalDate issuedOn;
    public int workingDays;
    public LocalDate deadline;
    public String suppliedSummary;
    public LocalDate suppliedOn;
    public String outcome = "PENDING";
    public String outcomeNote;
    @Column(columnDefinition = "uuid")
    public UUID recordedBy;
    public Instant recordedAt = Instant.now();
    @Version
    public Long version;
}
