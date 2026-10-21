package rw.gov.ptc.session.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.session.entity.AgendaActivity;
import rw.gov.ptc.session.enums.ActivityStatus;

import java.util.List;
import java.util.UUID;

public interface AgendaActivityRepository extends JpaRepository<AgendaActivity, UUID> {

    List<AgendaActivity> findByAgendaItemIdOrderByPositionAsc(UUID agendaItemId);

    List<AgendaActivity> findBySessionIdOrderByPositionAsc(UUID sessionId);

    List<AgendaActivity> findByFileIdAndActivityCodeAndStatus(UUID fileId, String activityCode, ActivityStatus status);

    long countBySessionIdAndStatus(UUID sessionId, ActivityStatus status);
}
