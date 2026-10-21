package rw.gov.ptc.minutes.entity;

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
import rw.gov.ptc.minutes.enums.MinutesStatus;

import java.time.Instant;
import java.util.UUID;

/** Draft or final minutes of a session; content is the generated structure rendered to .docx. */
@Entity
@Table(name = "minutes")
public class Minutes extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID sessionId;
    @Enumerated(EnumType.STRING)
    public MinutesStatus status = MinutesStatus.DRAFT;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public JsonNode content;
    public String secretaryNotes;
    public Instant generatedAt = Instant.now();
    @Column(columnDefinition = "uuid")
    public UUID generatedBy;
    public Instant finalisedAt;
    @Column(columnDefinition = "uuid")
    public UUID finalisedBy;
    @Version
    public Long version;
}
