package rw.gov.ptc.evaluation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.evaluation.entity.AdminCureRequest;

import java.util.List;
import java.util.UUID;

public interface AdminCureRequestRepository extends JpaRepository<AdminCureRequest, UUID> {

    List<AdminCureRequest> findByFileIdOrderByRecordedAtAsc(UUID fileId);
}
