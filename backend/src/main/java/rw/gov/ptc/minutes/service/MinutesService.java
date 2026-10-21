package rw.gov.ptc.minutes.service;

import rw.gov.ptc.minutes.dto.Download;
import rw.gov.ptc.minutes.dto.SessionRef;
import rw.gov.ptc.minutes.entity.Minutes;

import java.util.Map;
import java.util.UUID;

/**
 * Minutes lifecycle: generate (draft) → submit for review → each present
 * member acknowledges the current version → finalise. Regenerating or
 * editing bumps the version, which invalidates earlier acknowledgements.
 * FINAL minutes are immutable.
 */
public interface MinutesService {

    SessionRef session(UUID sessionId);

    Minutes generate(UUID sessionId);

    Map<String, Object> view(UUID sessionId);

    Minutes updateNotes(UUID minutesId, String notes, Long version);

    Minutes submitForReview(UUID minutesId);

    Map<String, Object> acknowledge(UUID minutesId, String comment);

    Minutes finalise(UUID minutesId);

    Download docx(UUID minutesId);
}
