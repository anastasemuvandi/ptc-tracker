package rw.gov.ptc.evaluation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.evaluation.entity.EvaluationStage;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EvaluationStageRepository extends JpaRepository<EvaluationStage, UUID> {

    List<EvaluationStage> findByEvaluationIdOrderBySequenceAsc(UUID evaluationId);

    Optional<EvaluationStage> findByEvaluationIdAndStageCode(UUID evaluationId, String stageCode);
}
