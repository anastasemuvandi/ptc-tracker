package rw.gov.ptc.session.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.session.entity.AbsenceApology;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AbsenceApologyRepository extends JpaRepository<AbsenceApology, UUID> {

    List<AbsenceApology> findBySessionId(UUID sessionId);

    Optional<AbsenceApology> findBySessionIdAndUserId(UUID sessionId, UUID userId);
}
