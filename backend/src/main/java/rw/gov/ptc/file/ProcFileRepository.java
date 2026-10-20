package rw.gov.ptc.file;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcFileRepository extends JpaRepository<ProcFile, UUID> {

    List<ProcFile> findByContractIdOrderByCreatedAtAsc(UUID contractId);
}
