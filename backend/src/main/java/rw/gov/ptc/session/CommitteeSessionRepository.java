package rw.gov.ptc.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommitteeSessionRepository extends JpaRepository<CommitteeSession, UUID> {

    List<CommitteeSession> findByCommitteeIdOrderByScheduledAtDesc(UUID committeeId);
}
