package rw.gov.ptc.decision.service;

import rw.gov.ptc.decision.dto.DeclarationRequest;
import rw.gov.ptc.decision.entity.CoiDeclaration;
import rw.gov.ptc.session.entity.AgendaItem;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Conflict-of-interest declarations: before a file is opened, each attending
 * member declares any direct or indirect interest. A member with an interest
 * is excluded from that file's discussion and vote.
 */
public interface DeclarationService {

    /** The agenda item, after checking the caller may read its session. */
    AgendaItem item(UUID agendaItemId);

    Map<String, Object> list(UUID agendaItemId);

    /** Present members who have not yet declared on this item. */
    List<Map<String, Object>> pending(AgendaItem item);

    CoiDeclaration declare(UUID agendaItemId, DeclarationRequest req);

    /** CSV of declarations, by business references only. */
    String exportCsv(UUID sessionId);
}
