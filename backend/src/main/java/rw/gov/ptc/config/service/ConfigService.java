package rw.gov.ptc.config.service;

import com.fasterxml.jackson.databind.JsonNode;
import rw.gov.ptc.config.dto.ConfigSnapshot;
import rw.gov.ptc.config.dto.NewRule;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves configuration for the current institution at an instant: the
 * latest institution row effective at that instant, otherwise the latest
 * platform row. Rules are never edited; a new row with a later effective
 * date supersedes the old one and the history is retained.
 */
public interface ConfigService {

    ConfigSnapshot snapshot();

    /** All keys resolved at the given instant for the current institution (RLS-scoped). */
    ConfigSnapshot snapshot(Instant at);

    /** Re-hydrates a stored snapshot (decisions, sessions) without re-resolving. */
    ConfigSnapshot fromStored(JsonNode stored);

    List<Map<String, Object>> history(String key);

    /**
     * Adds a superseding rule. Retroactive rules are refused: a rule may take
     * effect now or later, never in the past, so decisions already taken
     * keep the rules that applied to them.
     */
    UUID addRule(NewRule rule);
}
