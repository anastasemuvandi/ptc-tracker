package rw.gov.ptc.minutes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MinutesAcknowledgementRepository extends JpaRepository<MinutesAcknowledgement, UUID> {
}
