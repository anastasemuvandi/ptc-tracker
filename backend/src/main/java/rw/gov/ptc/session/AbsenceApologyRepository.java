package rw.gov.ptc.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AbsenceApologyRepository extends JpaRepository<AbsenceApology, UUID> {

    List<AbsenceApology> findBySessionId(UUID sessionId);

    Optional<AbsenceApology> findBySessionIdAndUserId(UUID sessionId, UUID userId);
}
