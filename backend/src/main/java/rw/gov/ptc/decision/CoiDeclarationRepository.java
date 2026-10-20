package rw.gov.ptc.decision;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CoiDeclarationRepository extends JpaRepository<CoiDeclaration, UUID> {

    List<CoiDeclaration> findByAgendaItemId(UUID agendaItemId);

    List<CoiDeclaration> findBySessionId(UUID sessionId);

    Optional<CoiDeclaration> findByAgendaItemIdAndUserId(UUID agendaItemId, UUID userId);
}
