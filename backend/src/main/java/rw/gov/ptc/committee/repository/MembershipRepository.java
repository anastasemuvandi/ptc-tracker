package rw.gov.ptc.committee.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.committee.entity.Membership;
import rw.gov.ptc.committee.enums.MembershipStatus;

import java.util.List;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    List<Membership> findByCommitteeIdAndStatusOrderByRoleAsc(UUID committeeId, MembershipStatus status);

    List<Membership> findByCommitteeIdOrderByAppointedAtDesc(UUID committeeId);

    List<Membership> findByCommitteeIdAndUserIdOrderByTermEndDesc(UUID committeeId, UUID userId);

    List<Membership> findByUserIdAndStatus(UUID userId, MembershipStatus status);
}
