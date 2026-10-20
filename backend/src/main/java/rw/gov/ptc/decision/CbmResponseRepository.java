package rw.gov.ptc.decision;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CbmResponseRepository extends JpaRepository<CbmResponse, UUID> {

    List<CbmResponse> findByDecisionIdOrderByRespondedAtAsc(UUID decisionId);

    List<CbmResponse> findByFileIdOrderByRespondedAtAsc(UUID fileId);
}
