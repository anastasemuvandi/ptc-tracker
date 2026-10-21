package rw.gov.ptc.evaluation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.evaluation.entity.DueDiligence;

import java.util.List;
import java.util.UUID;

public interface DueDiligenceRepository extends JpaRepository<DueDiligence, UUID> {

    List<DueDiligence> findByFileIdOrderByRecordedAtAsc(UUID fileId);
}
