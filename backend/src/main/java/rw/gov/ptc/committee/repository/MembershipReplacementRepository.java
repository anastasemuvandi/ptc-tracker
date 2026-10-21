package rw.gov.ptc.committee.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.committee.entity.MembershipReplacement;

import java.util.List;
import java.util.UUID;

public interface MembershipReplacementRepository extends JpaRepository<MembershipReplacement, UUID> {

    List<MembershipReplacement> findByCommitteeIdOrderByPerformedAtDesc(UUID committeeId);
}
