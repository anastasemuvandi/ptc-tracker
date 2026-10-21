package rw.gov.ptc.evaluation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.evaluation.entity.AbnormalPriceCheck;

import java.util.List;
import java.util.UUID;

public interface AbnormalPriceCheckRepository extends JpaRepository<AbnormalPriceCheck, UUID> {

    List<AbnormalPriceCheck> findByFileIdOrderByRecordedAtAsc(UUID fileId);
}
