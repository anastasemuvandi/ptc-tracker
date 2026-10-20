package rw.gov.ptc.decision;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import rw.gov.ptc.common.TenantEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** The committee's recommendation on one agenda item, with the configuration it was judged under. */
@Entity
@Table(name = "decision")
public class Decision extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID sessionId;
    @Column(columnDefinition = "uuid")
    public UUID agendaItemId;
    @Column(columnDefinition = "uuid")
    public UUID fileId;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(columnDefinition = "char(1)")
    public String itemType;
    public String recommendationCode;
    public String recommendationText;
    public String recommendedBidderRef;
    public BigDecimal contractValue;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(columnDefinition = "char(3)")
    public String currency;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public JsonNode structuredOutcome;
    public Integer presentCount;
    public Integer eligibleVoters;
    public Integer requiredVotes;
    public int votesFor;
    public int votesAgainst;
    public int votesAbstained;
    public String status = "DRAFT";
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public JsonNode configSnapshot;
    public Instant votingOpenedAt;
    public Instant votingClosedAt;
    public Instant issuedAt;
    @Column(columnDefinition = "uuid")
    public UUID issuedBy;
    public Instant issuedStepUpAt;
    @Column(columnDefinition = "uuid")
    public UUID createdBy;
    public Instant createdAt = Instant.now();
    @Version
    public Long version;
}
