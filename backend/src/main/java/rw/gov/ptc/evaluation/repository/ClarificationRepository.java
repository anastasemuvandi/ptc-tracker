package rw.gov.ptc.evaluation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.evaluation.entity.Clarification;

import java.util.List;
import java.util.UUID;

public interface ClarificationRepository extends JpaRepository<Clarification, UUID> {

    List<Clarification> findByFileIdOrderByRecordedAtAsc(UUID fileId);
}
