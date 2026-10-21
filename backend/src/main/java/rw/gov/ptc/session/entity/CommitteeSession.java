package rw.gov.ptc.session.entity;

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
import rw.gov.ptc.session.enums.SessionKind;
import rw.gov.ptc.session.enums.SessionStatus;

import java.time.Instant;
import java.util.UUID;

/** A committee session. session_number is the human-facing reference. */
@Entity
@Table(name = "committee_session")
public class CommitteeSession extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID committeeId;
    public String sessionNumber;
    @Enumerated(EnumType.STRING)
    public SessionKind kind;
    public Instant scheduledAt;
    public String location;
    @Enumerated(EnumType.STRING)
    public SessionStatus status = SessionStatus.PLANNED;
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
