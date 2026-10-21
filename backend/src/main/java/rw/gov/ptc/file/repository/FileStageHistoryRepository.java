package rw.gov.ptc.file.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.file.entity.FileStageHistory;

import java.util.List;
import java.util.UUID;

public interface FileStageHistoryRepository extends JpaRepository<FileStageHistory, UUID> {

    List<FileStageHistory> findByFileIdOrderByChangedAtAsc(UUID fileId);
}
