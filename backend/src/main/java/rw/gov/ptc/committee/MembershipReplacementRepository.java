package rw.gov.ptc.committee;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipReplacementRepository extends JpaRepository<MembershipReplacement, UUID> {

    List<MembershipReplacement> findByCommitteeIdOrderByPerformedAtDesc(UUID committeeId);
}
