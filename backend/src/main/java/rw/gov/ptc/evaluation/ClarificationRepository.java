package rw.gov.ptc.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ClarificationRepository extends JpaRepository<Clarification, UUID> {

    List<Clarification> findByFileIdOrderByRecordedAtAsc(UUID fileId);
}
