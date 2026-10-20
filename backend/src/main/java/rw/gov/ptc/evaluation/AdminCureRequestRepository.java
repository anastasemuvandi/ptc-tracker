package rw.gov.ptc.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AdminCureRequestRepository extends JpaRepository<AdminCureRequest, UUID> {

    List<AdminCureRequest> findByFileIdOrderByRecordedAtAsc(UUID fileId);
}
