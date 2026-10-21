package rw.gov.ptc.file.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.file.entity.ProcFile;

import java.util.List;
import java.util.UUID;

public interface ProcFileRepository extends JpaRepository<ProcFile, UUID> {

    List<ProcFile> findByContractIdOrderByCreatedAtAsc(UUID contractId);
}
