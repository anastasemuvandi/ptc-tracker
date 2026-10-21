package rw.gov.ptc.file.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.file.entity.FileReassignment;

import java.util.List;
import java.util.UUID;

public interface FileReassignmentRepository extends JpaRepository<FileReassignment, UUID> {

    List<FileReassignment> findByFileIdOrderByPerformedAtAsc(UUID fileId);
}
