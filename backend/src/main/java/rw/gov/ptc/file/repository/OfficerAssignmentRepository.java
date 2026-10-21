package rw.gov.ptc.file.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.file.entity.OfficerAssignment;

import java.util.List;
import java.util.UUID;

public interface OfficerAssignmentRepository extends JpaRepository<OfficerAssignment, UUID> {

    List<OfficerAssignment> findByFileIdOrderByAssignedAtAsc(UUID fileId);
}
