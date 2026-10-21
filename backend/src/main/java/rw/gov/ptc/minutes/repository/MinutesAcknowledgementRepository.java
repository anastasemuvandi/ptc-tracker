package rw.gov.ptc.minutes.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.minutes.entity.MinutesAcknowledgement;

import java.util.UUID;

public interface MinutesAcknowledgementRepository extends JpaRepository<MinutesAcknowledgement, UUID> {
}
