package rw.gov.ptc.contractchange;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.common.Time;
import rw.gov.ptc.contractchange.dto.AmendmentInput;
import rw.gov.ptc.contractchange.dto.CostClaimInput;
import rw.gov.ptc.contractchange.dto.ExtensionInput;
import rw.gov.ptc.contractchange.service.ContractChangeService;
import rw.gov.ptc.contractchange.service.ContractTimelineService;
import rw.gov.ptc.support.Fixtures;
import rw.gov.ptc.support.IntegrationTest;
import rw.gov.ptc.support.Owner;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static rw.gov.ptc.evaluation.TestScopes.asOfficer;
import static rw.gov.ptc.evaluation.TestScopes.file;
import static rw.gov.ptc.evaluation.TestScopes.issueDecision;

class ContractChangeServiceTest extends IntegrationTest {

    @Autowired
    ContractChangeService changes;
    @Autowired
    ContractTimelineService timeline;

    private static Fixtures.Tenant tenant() {
        return Fixtures.tenant("CC" + System.nanoTime() % 1_000_000);
    }

    private static AmendmentInput amendment(String value, Boolean affectsSubstance) {
        return new AmendmentInput(new BigDecimal(value), null, "Additional drainage works",
                affectsSubstance, affectsSubstance == null ? null : "Same works, same site, quantities only",
                false, true, true, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> assessment(Map<String, Object> view) {
        return (Map<String, Object>) view.get("assessment");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sub(Map<String, Object> m, String key) {
        return (Map<String, Object>) m.get(key);
    }

    private static ApiException apiError(Runnable r) {
        try {
            r.run();
        } catch (ApiException e) {
            return e;
        }
        throw new AssertionError("expected ApiException");
    }

    /** Contract original value 100,000,000 RWF; ceiling 20 %, warning 15 %. */
    @Test
    void successiveAmendmentsUnderTheCeilingAreSummed() {
        Fixtures.Tenant t = tenant();
        // First amendment: 12 % — approved by the CBM.
        asOfficer(t, () -> changes.createAmendment(t.fileId(), amendment("12000000.00", false)));
        issueDecision(t, t.fileId(), "C", "APPROVE_AMENDMENT", true);

        // Second amendment: 10 % on its own, 22 % cumulatively.
        UUID second = file(t, "C", t.contractId(), null);
        Map<String, Object> view = asOfficer(t, () -> changes.createAmendment(second, amendment("10000000.00", false)));
        Map<String, Object> a = assessment(view);
        assertThat(a.get("priorApprovedCumulative")).isEqualTo("12000000.00");
        assertThat(a.get("resultingCumulative")).isEqualTo("22000000.00");
        assertThat((BigDecimal) a.get("resultingPct")).isEqualByComparingTo("22.00");
        assertThat(a.get("level")).isEqualTo("EXCEEDED");
        assertThat(sub(a, "quantitative").get("withinPct")).isEqualTo(false);
        assertThat(a.get("route")).isEqualTo("ESCALATED");

        String firstStatus = Owner.jdbc().queryForObject("SELECT status FROM amendment_request WHERE file_id = ?",
                String.class, t.fileId());
        assertThat(firstStatus).isEqualTo("APPROVED");

        Map<String, Object> tl = asOfficer(t, () -> timeline.timeline(t.contractId()));
        assertThat(tl.get("cumulativeApproved")).isEqualTo("12000000.00");
        assertThat((List<?>) tl.get("events")).hasSize(2);
        assertThat(tl.get("level")).isEqualTo("OK");
    }

    @Test
    void qualitativeFailureIsNotCuredByPassingTheQuantitativeTest() {
        Fixtures.Tenant t = tenant();
        Map<String, Object> a = assessment(asOfficer(t, () -> changes.createAmendment(t.fileId(), amendment("1000000.00", true))));
        assertThat(sub(a, "quantitative").get("passes")).isEqualTo(true);
        assertThat(a.get("route")).isEqualTo("ENTITY");
        assertThat(sub(a, "qualitative").get("passes")).isEqualTo(false);
        assertThat(a.get("barred")).isEqualTo(true);
        assertThat(a.get("admissible")).isEqualTo(false);
        assertThat(String.valueOf(a.get("warnings"))).contains("amendment.qualitative_bar");
    }

    @Test
    void issuedRecommendationFreezesTheAssessment() {
        Fixtures.Tenant t = tenant();
        asOfficer(t, () -> changes.createAmendment(t.fileId(), amendment("5000000.00", false)));
        issueDecision(t, t.fileId(), "C", "APPROVE_AMENDMENT", false);
        Map<String, Object> view = asOfficer(t, () -> changes.getAmendment(t.fileId()));
        assertThat(view.get("frozen")).isEqualTo(true);
        ApiException e = apiError(() -> asOfficer(t, () -> changes.updateAmendment(t.fileId(), amendment("6000000.00", false))));
        assertThat(e.code()).isEqualTo("amendment.frozen");
    }

    @Test
    void extensionWithCostClaimCreatesLinkedAmendmentFile() {
        Fixtures.Tenant t = tenant();
        UUID ext = file(t, "D", t.contractId(), null);
        LocalDate completion = Time.today().plusDays(100);
        Map<String, Object> view = asOfficer(t, () -> changes.createExtension(ext, new ExtensionInput(
                completion.plusDays(60), "BIDDER_FAULT", "Late mobilisation", Time.today(), null, null, null, null, null)));
        @SuppressWarnings("unchecked")
        Map<String, Object> a = (Map<String, Object>) view.get("assessment");
        assertThat(a.get("receivedBeforeCompletion")).isEqualTo(true);
        assertThat(a.get("penaltiesPrompt")).isEqualTo(true);
        assertThat(a.get("readyForRecommendation")).isEqualTo(false);
        assertThat(a.get("extensionDays")).isEqualTo(60L);

        Map<String, Object> claim = asOfficer(t, () -> changes.raiseCostClaim(ext,
                new CostClaimInput(new BigDecimal("3000000.00"), null)));
        UUID claimFile = (UUID) claim.get("fileId");
        var row = Owner.jdbc().queryForMap("SELECT f.item_type, a.linked_extension_file_id, a.contract_id FROM proc_file f JOIN amendment_request a ON a.file_id = f.id WHERE f.id = ?", claimFile);
        assertThat(row.get("item_type")).isEqualTo("C");
        assertThat(row.get("linked_extension_file_id")).isEqualTo(ext);
        assertThat(row.get("contract_id")).isEqualTo(t.contractId());
        UUID linked = Owner.jdbc().queryForObject("SELECT linked_amendment_file_id FROM extension_request WHERE file_id = ?", UUID.class, ext);
        assertThat(linked).isEqualTo(claimFile);
    }

    @Test
    void approvedExtensionMovesTheContractCompletionDate() {
        Fixtures.Tenant t = tenant();
        UUID ext = file(t, "D", t.contractId(), null);
        LocalDate requested = Time.today().plusDays(160);
        asOfficer(t, () -> changes.createExtension(ext, new ExtensionInput(
                requested, "UNFORESEEN_PHYSICAL_CONDITIONS", "Rock encountered", Time.today(), null, null, null, null, null)));
        issueDecision(t, ext, "D", "GRANT_EXTENSION", true);
        Map<String, Object> tl = asOfficer(t, () -> timeline.timeline(t.contractId()));
        assertThat(tl.get("currentCompletionDate")).isEqualTo(requested);
    }

    @Test
    void otherInstitutionsContractsAreNotFound() {
        Fixtures.Tenant a = tenant();
        Fixtures.Tenant b = tenant();
        ApiException e = apiError(() -> asOfficer(a, () -> timeline.timeline(b.contractId())));
        assertThat(e.status().value()).isEqualTo(404);
        e = apiError(() -> asOfficer(a, () -> changes.getAmendment(b.fileId())));
        assertThat(e.status().value()).isEqualTo(404);
    }
}
