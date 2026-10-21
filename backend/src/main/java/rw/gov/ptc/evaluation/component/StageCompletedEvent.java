package rw.gov.ptc.evaluation.component;

import java.util.UUID;

/** Published in the completing transaction when an evaluation stage is completed. */
public record StageCompletedEvent(UUID fileId, String stageCode) {
}
