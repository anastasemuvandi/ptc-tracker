package rw.gov.ptc.decision.service;

import rw.gov.ptc.decision.dto.ResponseRequest;
import rw.gov.ptc.decision.entity.CbmResponse;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The CBM's response to an issued recommendation: a separate, logged act
 * that never edits the committee's decision record.
 */
public interface CbmService {

    List<Map<String, Object>> pending();

    CbmResponse respond(UUID decisionId, ResponseRequest req);
}
