package rw.gov.ptc.evaluation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import rw.gov.ptc.common.TenantEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Abnormal price exchange: written request, explanation, analysis, decision. */
@Entity
@Table(name = "abnormal_price_check")
public class AbnormalPriceCheck extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID fileId;
    public String bidderRef;
    public String direction;
    public String requestRef;
    public LocalDate requestDate;
    public String explanationSummary;
    public LocalDate explanationDate;
    public String analysis;
    public Boolean doubtLifted;
    public String decision = "PENDING";
    public String decisionReasons;
    @Column(columnDefinition = "uuid")
    public UUID recordedBy;
    public Instant recordedAt = Instant.now();
    @Version
    public Long version;
}
