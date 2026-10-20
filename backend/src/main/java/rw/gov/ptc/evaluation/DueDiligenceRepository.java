package rw.gov.ptc.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DueDiligenceRepository extends JpaRepository<DueDiligence, UUID> {

    List<DueDiligence> findByFileIdOrderByRecordedAtAsc(UUID fileId);
}
