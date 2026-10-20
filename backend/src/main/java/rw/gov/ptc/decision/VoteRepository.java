package rw.gov.ptc.decision;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VoteRepository extends JpaRepository<Vote, UUID> {

    List<Vote> findByDecisionId(UUID decisionId);

    Optional<Vote> findByDecisionIdAndUserId(UUID decisionId, UUID userId);
}
