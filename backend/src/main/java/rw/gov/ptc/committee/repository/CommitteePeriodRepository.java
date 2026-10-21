package rw.gov.ptc.committee.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.committee.entity.CommitteePeriod;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommitteePeriodRepository extends JpaRepository<CommitteePeriod, UUID> {

    List<CommitteePeriod> findByCommitteeIdOrderByStartsOnDesc(UUID committeeId);

    Optional<CommitteePeriod> findFirstByCommitteeIdOrderByStartsOnDesc(UUID committeeId);
}
