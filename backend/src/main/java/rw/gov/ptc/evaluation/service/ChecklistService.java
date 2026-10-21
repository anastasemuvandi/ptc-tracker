package rw.gov.ptc.evaluation.service;

import java.util.Map;
import java.util.UUID;

/**
 * Checklist prompts for item types that carry one (B: TOR / tender document
 * review; E, F, G). The prompts come from configuration (checklist.items).
 */
public interface ChecklistService {

    Map<String, Object> get(UUID fileId);

    Map<String, Object> answer(UUID fileId, String itemCode, String answer, String note, Long version);
}
