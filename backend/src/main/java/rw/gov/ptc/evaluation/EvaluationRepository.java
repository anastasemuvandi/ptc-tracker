package rw.gov.ptc.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EvaluationRepository extends JpaRepository<Evaluation, UUID> {

    Optional<Evaluation> findByFileId(UUID fileId);
}
