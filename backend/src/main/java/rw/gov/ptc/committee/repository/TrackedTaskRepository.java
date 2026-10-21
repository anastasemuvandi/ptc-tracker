package rw.gov.ptc.committee.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.committee.entity.TrackedTask;
import rw.gov.ptc.committee.enums.TaskStatus;

import java.util.List;
import java.util.UUID;

public interface TrackedTaskRepository extends JpaRepository<TrackedTask, UUID> {

    List<TrackedTask> findByStatusOrderByDueDateAsc(TaskStatus status);

    List<TrackedTask> findAllByOrderByCreatedAtDesc();
}
