package rw.gov.ptc.minutes.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.minutes.entity.Minutes;

import java.util.Optional;
import java.util.UUID;

public interface MinutesRepository extends JpaRepository<Minutes, UUID> {

    Optional<Minutes> findBySessionId(UUID sessionId);
}
