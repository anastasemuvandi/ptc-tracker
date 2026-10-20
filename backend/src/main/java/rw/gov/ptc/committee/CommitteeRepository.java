package rw.gov.ptc.committee;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommitteeRepository extends JpaRepository<Committee, UUID> {

    List<Committee> findAllByOrderByCodeAsc();

    long countByStatus(String status);
}
