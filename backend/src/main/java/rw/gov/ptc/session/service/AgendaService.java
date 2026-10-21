package rw.gov.ptc.session.service;

import rw.gov.ptc.session.dto.AddItem;
import rw.gov.ptc.session.entity.AgendaActivity;
import rw.gov.ptc.session.entity.AgendaItem;

import java.util.List;
import java.util.UUID;

/**
 * Agenda scope is enforced, not advisory: every item is a procurement file
 * of a permitted type (A–H, with H only for files referred by the CBM) or a
 * type I administrative item about the committee's own business.
 */
public interface AgendaService {

    AgendaItem add(UUID sessionId, AddItem req);

    void remove(UUID sessionId, UUID itemId);

    /** Puts activities not completed in earlier sessions on this session's agenda. */
    List<AgendaActivity> carryOver(UUID sessionId, List<UUID> activityIds);

    List<AgendaItem> reorder(UUID sessionId, List<UUID> order);
}
