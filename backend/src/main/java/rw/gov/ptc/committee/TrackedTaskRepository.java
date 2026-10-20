package rw.gov.ptc.committee;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrackedTaskRepository extends JpaRepository<TrackedTask, UUID> {

    List<TrackedTask> findByStatusOrderByDueDateAsc(String status);

    List<TrackedTask> findAllByOrderByCreatedAtDesc();
}
