package rw.gov.ptc.session.service;

import rw.gov.ptc.file.entity.ProcFile;
import rw.gov.ptc.session.dto.RecordActivity;
import rw.gov.ptc.session.entity.AgendaActivity;
import rw.gov.ptc.session.entity.AgendaItem;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Activities scheduled for a file on a session agenda, and whether each was
 * completed. For type A they are the evaluation stages and stay in step with
 * the evaluation; the recommendation activity completes when its vote adopts it.
 */
public interface ActivityService {

    /** Codes allowed for this file type, in configured order. */
    List<String> allowed(String itemType);

    /** Activities of this file not yet completed, in configured order: the default when none are named. */
    List<String> outstanding(ProcFile f);

    /** Plans the named activities on an agenda item; carriedFrom maps a code to the activity it continues. */
    List<AgendaActivity> plan(AgendaItem item, ProcFile f, List<String> codes, Map<String, UUID> carriedFrom);

    /** For the agenda dialog: the activities of this file's type, and those still outstanding. */
    Map<String, Object> options(UUID sessionId, UUID fileId);

    AgendaActivity record(UUID sessionId, UUID activityId, RecordActivity req);

    /** Activities not completed in earlier sessions of this session's committee, not yet carried over. */
    List<Map<String, Object>> carryOverCandidates(UUID sessionId);

    /** Called when voting closes on an agenda item's recommendation. */
    void votingClosed(UUID agendaItemId, boolean adopted);

    List<Map<String, Object>> forSession(UUID sessionId);
}
