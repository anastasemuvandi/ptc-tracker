package rw.gov.ptc.decision.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.decision.entity.CbmResponse;

import java.util.List;
import java.util.UUID;

public interface CbmResponseRepository extends JpaRepository<CbmResponse, UUID> {

    List<CbmResponse> findByDecisionIdOrderByRespondedAtAsc(UUID decisionId);

    List<CbmResponse> findByFileIdOrderByRespondedAtAsc(UUID fileId);
}
