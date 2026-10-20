package rw.gov.ptc.minutes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MinutesRepository extends JpaRepository<Minutes, UUID> {

    Optional<Minutes> findBySessionId(UUID sessionId);
}
