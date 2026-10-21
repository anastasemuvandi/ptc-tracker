package rw.gov.ptc.session.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.session.entity.CommitteeSession;

import java.util.List;
import java.util.UUID;

public interface CommitteeSessionRepository extends JpaRepository<CommitteeSession, UUID> {

    List<CommitteeSession> findByCommitteeIdOrderByScheduledAtDesc(UUID committeeId);
}
