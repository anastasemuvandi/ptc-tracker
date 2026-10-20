package rw.gov.ptc.evaluation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import rw.gov.ptc.common.Rows;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-only view of the decision log for a file: the latest issued
 * recommendation and the CBM's latest response to it. Used to reconcile
 * evaluation stage A6 and amendment/extension status without depending on
 * the decision module's services.
 */
@Component
public class IssuedDecisions {

    private final JdbcTemplate jdbc;

    public IssuedDecisions(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Keys: id, recommendationCode, recommendedBidderRef, issuedAt, issuedBy, structuredOutcome. */
    public Optional<Map<String, Object>> latestIssued(UUID fileId) {
        return jdbc.queryForList("""
                SELECT id, recommendation_code, recommendation_text, recommended_bidder_ref, issued_at, issued_by,
                       structured_outcome
                  FROM decision WHERE file_id = ? AND status = 'ISSUED'
                 ORDER BY issued_at DESC LIMIT 1
                """, fileId).stream().map(Rows::camel).findFirst();
    }

    /** Keys: response, reasons, respondedAt. */
    public Optional<Map<String, Object>> latestCbmResponse(UUID decisionId) {
        return jdbc.queryForList("""
                SELECT response, reasons, responded_at FROM cbm_response
                 WHERE decision_id = ? ORDER BY responded_at DESC LIMIT 1
                """, decisionId).stream().map(Rows::camel).findFirst();
    }
}
