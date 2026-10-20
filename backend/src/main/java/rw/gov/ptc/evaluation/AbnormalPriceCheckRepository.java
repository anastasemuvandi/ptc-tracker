package rw.gov.ptc.evaluation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AbnormalPriceCheckRepository extends JpaRepository<AbnormalPriceCheck, UUID> {

    List<AbnormalPriceCheck> findByFileIdOrderByRecordedAtAsc(UUID fileId);
}
