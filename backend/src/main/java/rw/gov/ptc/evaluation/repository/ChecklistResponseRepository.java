package rw.gov.ptc.evaluation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.evaluation.entity.ChecklistResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChecklistResponseRepository extends JpaRepository<ChecklistResponse, UUID> {

    List<ChecklistResponse> findByFileId(UUID fileId);

    Optional<ChecklistResponse> findByFileIdAndItemCode(UUID fileId, String itemCode);
}
