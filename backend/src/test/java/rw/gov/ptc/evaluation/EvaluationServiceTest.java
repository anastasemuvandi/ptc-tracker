package rw.gov.ptc.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.common.Time;
import rw.gov.ptc.file.ProcFile;
import rw.gov.ptc.support.Fixtures;
import rw.gov.ptc.support.IntegrationTest;
import rw.gov.ptc.support.Owner;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static rw.gov.ptc.evaluation.TestScopes.asOfficer;

class EvaluationServiceTest extends IntegrationTest {

    @Autowired
    EvaluationService evaluation;
    @Autowired
    ChecklistService checklist;
    @Autowired
    ObjectMapper json;

    private static Fixtures.Tenant tenant() {
        return Fixtures.tenant("EV" + System.nanoTime() % 1_000_000);
    }

    @SuppressWarnings("unchecked")
    private static List<String> blockerCodes(ApiException e) {
        return ((List<Map<String, Object>>) e.params().get("blockers")).stream()
                .map(b -> (String) b.get("code")).toList();
    }

    private static ApiException apiError(Runnable r) {
        try {
            r.run();
        } catch (ApiException e) {
            return e;
        }
        throw new AssertionError("expected ApiException");
    }

    private EvaluationService.BidderInput pass(String ref, Integer rank) {
        return new EvaluationService.BidderInput(ref, "PASS", null, null, rank, null);
    }

    /** Walks a file through A1–A3 and checks every gate on the way. */
    @Test
    void stageSequenceGatesAndAbnormalPriceRejection() {
        Fixtures.Tenant t = tenant();
        UUID file = TestScopes.file(t, "A", null, Time.today().plusDays(90));
        asOfficer(t, () -> {
            // The view works before anything is persisted.
            assertThat(evaluation.progress(file).get("evaluation")).isNull();

            ApiException e = apiError(() -> evaluation.startStage(file, "A1"));
            assertThat(blockerCodes(e)).contains("evaluation.blocker.criteria_not_locked");

            evaluation.lockCriteria(file, true, null);
            evaluation.startStage(file, "A1");

            // A4 cannot start before technical and financial evaluation.
            e = apiError(() -> evaluation.startStage(file, "A4"));
            assertThat(blockerCodes(e)).contains("evaluation.blocker.previous_stage_incomplete",
                    "evaluation.blocker.admin_after_technical_financial");

            // A bare "non-compliant" is not a reason.
            e = apiError(() -> evaluation.recordBidder(file, "A1", new EvaluationService.BidderInput(
                    "BID-3", "FAIL", "BID_SECURITY_OR_DECLARATION", "Non-compliant", null, null)));
            assertThat(e.code()).isEqualTo("evaluation.reason_insufficient");
            evaluation.recordBidder(file, "A1", pass("BID-1", null));
            evaluation.recordBidder(file, "A1", pass("BID-2", null));
            evaluation.recordBidder(file, "A1", new EvaluationService.BidderInput("BID-3", "FAIL",
                    "BID_SECURITY_OR_DECLARATION", "Bid security issued for 30 days, below required validity", null, null));
            evaluation.completeStage(file, "A1", "2 of 3 bidders passed preliminary examination");

            evaluation.startStage(file, "A2");
            e = apiError(() -> evaluation.recordBidder(file, "A2", pass("BID-3", 1)));
            assertThat(e.code()).isEqualTo("evaluation.bidder_eliminated");

            // No scores in stage data.
            e = apiError(() -> evaluation.updateStage(file, "A2",
                    json.createObjectNode().put("technicalScore", 80), null, null));
            assertThat(e.code()).isEqualTo("evaluation.scores_not_stored");

            evaluation.recordBidder(file, "A2", pass("BID-1", 1));
            evaluation.recordBidder(file, "A2", pass("BID-2", 2));
            var data = json.createObjectNode().put("evaluatedCount", 2).put("passingCount", 2);
            data.putObject("resourcePerson").put("name", "Eng. K.").put("institution", "RTDA");
            evaluation.updateStage(file, "A2", data, "Both technically responsive", null);
            e = apiError(() -> evaluation.completeStage(file, "A2", null));
            assertThat(blockerCodes(e)).containsExactly("evaluation.blocker.resource_person_unconfirmed");
            data.with("resourcePerson").put("noDecisionRole", true).put("noInterest", true);
            evaluation.updateStage(file, "A2", data, null, null);
            evaluation.completeStage(file, "A2", null);

            evaluation.startStage(file, "A3");
            // Rejection on abnormal-price grounds without the written exchange is refused.
            e = apiError(() -> evaluation.createAbnormal(file, new EvaluationService.AbnormalInput("BID-1",
                    "ABNORMALLY_LOW", null, null, null, null, null, false, "REJECTED", "Too low", null)));
            assertThat(e.code()).isEqualTo("evaluation.abnormal_rejection_without_exchange");
            assertThat(String.valueOf(e.params().get("missing"))).contains("requestRef", "requestDate", "analysis");

            var check = evaluation.createAbnormal(file, new EvaluationService.AbnormalInput("BID-1",
                    "ABNORMALLY_LOW", null, null, null, null, null, null, "PENDING", null, null));
            e = apiError(() -> evaluation.completeStage(file, "A3", "x"));
            assertThat(blockerCodes(e)).contains("evaluation.blocker.abnormal_price_pending",
                    "evaluation.blocker.arithmetic_check_missing");
            var rejected = evaluation.updateAbnormal(check.id, new EvaluationService.AbnormalInput("BID-1",
                    "ABNORMALLY_LOW", "PTC/2026/77", Time.today().minusDays(5), "Bidder cites own quarry",
                    Time.today().minusDays(2), "Quarry claim not evidenced; unit rates 40% below market",
                    false, "REJECTED", "Doubt not lifted after written exchange", check.version));
            assertThat(rejected.decision).isEqualTo("REJECTED");
        });
    }

    @Test
    void cureDeadlineCountsWorkingDaysSkippingWeekendsAndHolidays() {
        Fixtures.Tenant t = tenant();
        UUID file = TestScopes.file(t, "A", null, null);
        asOfficer(t, () -> {
            evaluation.lockCriteria(file, true, null);
            evaluation.startStage(file, "A1");
            evaluation.recordBidder(file, "A1", pass("BID-1", null));
            evaluation.completeStage(file, "A1", "ok");
            evaluation.startStage(file, "A2");
            evaluation.recordBidder(file, "A2", pass("BID-1", 1));
            evaluation.updateStage(file, "A2", json.createObjectNode().put("evaluatedCount", 1).put("passingCount", 1), "ok", null);
            evaluation.completeStage(file, "A2", null);
            evaluation.startStage(file, "A3");
            evaluation.recordBidder(file, "A3", pass("BID-1", 1));
            evaluation.updateStage(file, "A3", json.createObjectNode().put("arithmeticCheckDone", true)
                    .put("taxesIncludedInComparison", true), "BID-1 ranked first", null);
            evaluation.completeStage(file, "A3", null);
            evaluation.startStage(file, "A4");

            ApiException e = apiError(() -> evaluation.createCure(file, new EvaluationService.CureInput("BID-9",
                    List.of(new EvaluationService.CureItem("TAX_CLEARANCE", null, null, null, null)),
                    LocalDate.of(2026, 12, 23), null, null, null, null, null)));
            assertThat(e.code()).isEqualTo("evaluation.cure_not_first_ranked");

            // Wed 23 Dec 2026 + 5 working days: 24, (25 holiday, 26–27 weekend), 28, 29, 30, 31.
            Map<String, Object> cure = evaluation.createCure(file, new EvaluationService.CureInput("BID-1",
                    List.of(new EvaluationService.CureItem("TAX_CLEARANCE", null, null, null, null)),
                    LocalDate.of(2026, 12, 23), null, null, null, null, null));
            assertThat(cure.get("deadline")).hasToString("2026-12-31");
            assertThat(cure.get("overdue")).isEqualTo(false);
            assertThat((Integer) cure.get("workingDaysRemaining")).isPositive();

            e = apiError(() -> evaluation.completeStage(file, "A4", "x"));
            assertThat(blockerCodes(e)).contains("evaluation.blocker.cure_pending");
        });
    }

    @Test
    void bidValidityClockStates() {
        Fixtures.Tenant t = tenant();
        LocalDate today = Time.today();
        // Defaults: request must reach the regulator 15 days before expiry; alerts 10 / 7 days.
        Map<LocalDate, String> expected = new java.util.LinkedHashMap<>();
        expected.put(today.plusDays(40), "OK");
        expected.put(today.plusDays(24), "REQUEST_DUE_SOON");
        expected.put(today.plusDays(10), "REQUEST_OVERDUE");
        expected.put(today.plusDays(5), "EXPIRING");
        expected.put(today.minusDays(1), "EXPIRED");
        asOfficer(t, () -> {
            ProcFile f = new ProcFile();
            assertThat(evaluation.bidValidity(f).get("state")).isEqualTo("NOT_SET");
            expected.forEach((expiry, state) -> {
                f.bidValidityExpiry = expiry;
                assertThat(evaluation.bidValidity(f).get("state")).as(expiry.toString()).isEqualTo(state);
            });
            f.bidValidityExpiry = today.plusDays(40);
            assertThat(evaluation.bidValidity(f).get("regulatorRequestDeadline")).isEqualTo(today.plusDays(25));
        });
    }

    @Test
    void otherInstitutionsFilesAreNotFound() {
        Fixtures.Tenant a = tenant();
        Fixtures.Tenant b = tenant();
        UUID bFile = TestScopes.file(b, "A", null, null);
        asOfficer(a, () -> {
            ApiException e = apiError(() -> evaluation.progress(bFile));
            assertThat(e.status().value()).isEqualTo(404);
            e = apiError(() -> evaluation.lockCriteria(bFile, true, null));
            assertThat(e.status().value()).isEqualTo(404);
        });
        assertThat(Owner.jdbc().queryForObject("SELECT count(*) FROM evaluation WHERE file_id = ?", Integer.class, bFile))
                .isZero();
    }

    @Test
    void raisingRejectionOfAllCreatesTypeFFile() {
        Fixtures.Tenant t = tenant();
        UUID file = TestScopes.file(t, "A", null, null);
        asOfficer(t, () -> {
            evaluation.lockCriteria(file, true, null);
            evaluation.startStage(file, "A1");
            evaluation.recordBidder(file, "A1", pass("BID-1", null));
            evaluation.completeStage(file, "A1", "ok");
            evaluation.startStage(file, "A2");
            evaluation.recordBidder(file, "A2", pass("BID-1", 1));
            evaluation.updateStage(file, "A2", json.createObjectNode().put("evaluatedCount", 1).put("passingCount", 1), "ok", null);
            evaluation.completeStage(file, "A2", null);
            evaluation.startStage(file, "A3");
            Map<String, Object> raised = evaluation.raiseRejectionOfAll(file, "ALL_OVER_BUDGET", "All bids exceed the budget");
            assertThat((String) raised.get("fileNumber")).matches("F-\\d{4}/\\d{4}");
        });
        String type = Owner.jdbc().queryForObject("SELECT item_type FROM proc_file WHERE file_number = (SELECT data->>'rejectionOfAllFileNumber' FROM evaluation_stage WHERE file_id = ? AND stage_code = 'A3') AND institution_id = ?",
                String.class, file, t.institutionId());
        assertThat(type).isEqualTo("F");
    }

    @Test
    void checklistRequiresNoteForFindings() {
        Fixtures.Tenant t = tenant();
        UUID file = TestScopes.file(t, "B", null, null);
        asOfficer(t, () -> {
            ApiException e = apiError(() -> checklist.answer(file, "SPECS_NEUTRAL", "NO", null, null));
            assertThat(e.code()).isEqualTo("checklist.note_required");
            Map<String, Object> view = checklist.answer(file, "SPECS_NEUTRAL", "NO", "Brand name without 'or equivalent'", null);
            @SuppressWarnings("unchecked")
            Map<String, Object> summary = (Map<String, Object>) view.get("summary");
            assertThat(summary.get("findings")).isEqualTo(1);
            assertThat(summary.get("total")).isEqualTo(7);
        });
    }
}
