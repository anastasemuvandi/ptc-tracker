package rw.gov.ptc.committee.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.committee.entity.Committee;
import rw.gov.ptc.committee.enums.CommitteeStatus;

import java.util.List;
import java.util.UUID;

public interface CommitteeRepository extends JpaRepository<Committee, UUID> {

    List<Committee> findAllByOrderByCodeAsc();

    long countByStatus(CommitteeStatus status);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
