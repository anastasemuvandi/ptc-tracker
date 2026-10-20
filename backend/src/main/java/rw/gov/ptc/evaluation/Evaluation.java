package rw.gov.ptc.evaluation;

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

/** Type A evaluation header: criteria lock and the stage definitions it was opened with. */
@Entity
@Table(name = "evaluation")
public class Evaluation extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID fileId;
    public Instant criteriaLockedAt;
    @Column(columnDefinition = "uuid")
    public UUID criteriaLockedBy;
    public String criteriaDefectFindings;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public JsonNode configSnapshot;
    public Instant createdAt = Instant.now();
    @Version
    public Long version;
}
