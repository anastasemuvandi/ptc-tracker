package rw.gov.ptc.committee;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    List<Membership> findByCommitteeIdAndStatusOrderByRoleAsc(UUID committeeId, String status);

    List<Membership> findByCommitteeIdOrderByAppointedAtDesc(UUID committeeId);

    List<Membership> findByCommitteeIdAndUserIdOrderByTermEndDesc(UUID committeeId, UUID userId);

    List<Membership> findByUserIdAndStatus(UUID userId, String status);
}
