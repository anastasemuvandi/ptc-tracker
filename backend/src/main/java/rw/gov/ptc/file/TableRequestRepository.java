package rw.gov.ptc.file;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TableRequestRepository extends JpaRepository<TableRequest, UUID> {

    List<TableRequest> findByCommitteeIdOrderByRequestedAtDesc(UUID committeeId);

    List<TableRequest> findByFileIdOrderByRequestedAtDesc(UUID fileId);

    List<TableRequest> findByCommitteeIdAndStatusOrderByRequestedAtDesc(UUID committeeId, String status);
}
