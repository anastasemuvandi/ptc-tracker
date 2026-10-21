package rw.gov.ptc.session.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import rw.gov.ptc.session.entity.AgendaItem;

import java.util.List;
import java.util.UUID;

public interface AgendaItemRepository extends JpaRepository<AgendaItem, UUID> {

    List<AgendaItem> findBySessionIdOrderByPositionAsc(UUID sessionId);

    List<AgendaItem> findByFileId(UUID fileId);
}
