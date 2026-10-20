package rw.gov.ptc.session;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import rw.gov.ptc.common.TenantEntity;

import java.time.Instant;
import java.util.UUID;

/** A committee session. session_number is the human-facing reference. */
@Entity
@Table(name = "committee_session")
public class CommitteeSession extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID committeeId;
    public String sessionNumber;
    public String kind;
    public Instant scheduledAt;
    public String location;
    public String status = "PLANNED";
    public Instant convenedAt = Instant.now();
    public boolean shortNotice;
    public String shortNoticeReason;
    public Instant openedAt;
    public Instant closedAt;
    public int committeeSize;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public JsonNode configSnapshot;
    @Column(columnDefinition = "uuid")
    public UUID createdBy;
    public Instant createdAt = Instant.now();
    @Version
    public Long version;
}
