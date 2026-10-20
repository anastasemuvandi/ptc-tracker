package rw.gov.ptc.decision;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DecisionRepository extends JpaRepository<Decision, UUID> {

    Optional<Decision> findByAgendaItemId(UUID agendaItemId);

    List<Decision> findByFileIdOrderByCreatedAtAsc(UUID fileId);

    List<Decision> findByStatusOrderByIssuedAtAsc(String status);

    List<Decision> findBySessionId(UUID sessionId);
}
