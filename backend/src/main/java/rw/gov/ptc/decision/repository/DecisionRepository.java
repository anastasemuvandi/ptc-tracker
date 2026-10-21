package rw.gov.ptc.decision.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.decision.entity.Decision;
import rw.gov.ptc.decision.enums.DecisionStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DecisionRepository extends JpaRepository<Decision, UUID> {

    Optional<Decision> findByAgendaItemId(UUID agendaItemId);

    List<Decision> findByFileIdOrderByCreatedAtAsc(UUID fileId);

    List<Decision> findByStatusOrderByIssuedAtAsc(DecisionStatus status);

    List<Decision> findBySessionId(UUID sessionId);
}
