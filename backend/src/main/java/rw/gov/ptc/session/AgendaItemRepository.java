package rw.gov.ptc.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgendaItemRepository extends JpaRepository<AgendaItem, UUID> {

    List<AgendaItem> findBySessionIdOrderByPositionAsc(UUID sessionId);

    List<AgendaItem> findByFileId(UUID fileId);
}
