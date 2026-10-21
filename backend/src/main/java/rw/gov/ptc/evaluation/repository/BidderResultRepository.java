package rw.gov.ptc.evaluation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.evaluation.entity.BidderResult;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BidderResultRepository extends JpaRepository<BidderResult, UUID> {

    List<BidderResult> findByFileIdOrderByRecordedAtAsc(UUID fileId);

    List<BidderResult> findByStageId(UUID stageId);

    Optional<BidderResult> findByStageIdAndBidderRef(UUID stageId, String bidderRef);
}
