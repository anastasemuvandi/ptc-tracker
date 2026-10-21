package rw.gov.ptc.file.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.file.entity.TableRequest;
import rw.gov.ptc.file.enums.TableRequestStatus;

import java.util.List;
import java.util.UUID;

public interface TableRequestRepository extends JpaRepository<TableRequest, UUID> {

    List<TableRequest> findByCommitteeIdOrderByRequestedAtDesc(UUID committeeId);

    List<TableRequest> findByFileIdOrderByRequestedAtDesc(UUID fileId);

    List<TableRequest> findByCommitteeIdAndStatusOrderByRequestedAtDesc(UUID committeeId, TableRequestStatus status);
}
