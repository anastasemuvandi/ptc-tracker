package rw.gov.ptc.evaluation.entity;

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
import rw.gov.ptc.evaluation.enums.EvaluationStageStatus;

import java.time.Instant;
import java.util.UUID;

/** One evaluation stage (A1..A6 by default). No scores are stored. */
@Entity
@Table(name = "evaluation_stage")
public class EvaluationStage extends TenantEntity {

    @Column(columnDefinition = "uuid")
    public UUID evaluationId;
    @Column(columnDefinition = "uuid")
    public UUID fileId;
    public String stageCode;
    public int sequence;
    @Enumerated(EnumType.STRING)
    public EvaluationStageStatus status = EvaluationStageStatus.NOT_STARTED;
    public String outcome;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    public JsonNode data;
    public Instant startedAt;
    public Instant completedAt;
    @Column(columnDefinition = "uuid")
    public UUID completedBy;
    @Version
    public Long version;
}
