package rw.gov.ptc.file;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FileStageHistoryRepository extends JpaRepository<FileStageHistory, UUID> {

    List<FileStageHistory> findByFileIdOrderByChangedAtAsc(UUID fileId);
}
