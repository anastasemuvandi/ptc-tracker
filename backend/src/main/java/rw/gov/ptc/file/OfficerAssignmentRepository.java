package rw.gov.ptc.file;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfficerAssignmentRepository extends JpaRepository<OfficerAssignment, UUID> {

    List<OfficerAssignment> findByFileIdOrderByAssignedAtAsc(UUID fileId);
}
