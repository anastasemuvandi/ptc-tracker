package rw.gov.ptc.decision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import rw.gov.ptc.auth.api.IdentityAdmin;
import rw.gov.ptc.auth.api.InstitutionRole;
import rw.gov.ptc.auth.internal.TestActors.Actor;
import rw.gov.ptc.auth.internal.TestActors;
import rw.gov.ptc.decision.entity.Decision;
import rw.gov.ptc.decision.repository.DecisionRepository;
import rw.gov.ptc.support.IntegrationTest;
import rw.gov.ptc.support.TestBeans;
import rw.gov.ptc.tenancy.TenantContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end committee work: files, agenda scope, attendance and quorum,
 * conflict-of-interest declarations, voting with dissent and step-up,
 * issue, and the CBM's response — plus cross-tenant isolation over HTTP.
 */
@AutoConfigureMockMvc
class SessionDecisionFlowTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    IdentityAdmin identity;
    @Autowired
    TestBeans.CapturingMailer mailer;
    @Autowired
    DecisionRepository decisions;
    @Autowired
    TransactionTemplate tx;

    TestActors actors;

    private JsonNode ok(MvcResult r) throws Exception {
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return actors.body(r);
    }

    private JsonNode call(org.springframework.test.web.servlet.RequestBuilder b) throws Exception {
        return ok(mvc.perform(b).andReturn());
    }

    @Test
    void fullCommitteeFlow() throws Exception {
        actors = new TestActors(mvc, json, identity, mailer);
        UUID inst = TestActors.newInstitution("SD");
        Actor cbm = actors.login(actors.user(inst, Set.of(InstitutionRole.CBM), false, null));

        // ---- constitution (before members sign in: appointment bumps tokens)
        UUID committee = UUID.fromString(call(actors.post_(cbm, Map.of("name", "PTC A", "code", "PTCA", "size", 5),
                "/api/committees")).get("id").asText());
        Actor chair = actors.user(inst, Set.of(), false, null);
        Actor vice = actors.user(inst, Set.of(), false, null);
        Actor sec = actors.user(inst, Set.of(), true, null);
        Actor m1 = actors.user(inst, Set.of(), false, null);
        Actor m2 = actors.user(inst, Set.of(), false, null);
        Map<Actor, String> roles = new java.util.LinkedHashMap<>();
        roles.put(chair, "CHAIRPERSON");
        roles.put(vice, "VICE_CHAIRPERSON");
        roles.put(sec, "SECRETARY");
        roles.put(m1, "MEMBER");
        roles.put(m2, "MEMBER");
        Map<Actor, String> membership = new HashMap<>();
        for (var e : roles.entrySet()) {
            JsonNode res = call(actors.post_(cbm, Map.of("userId", e.getKey().userId, "role", e.getValue()),
                    "/api/committees/{id}/members", committee));
            membership.put(e.getKey(), res.get("membership").get("id").asText());
        }
        for (Actor a : roles.keySet()) {
            actors.login(a);
        }

        // ---- files and contracts
        String contract = call(actors.post_(sec, Map.of("contractRef", "CT-2026-001", "title", "Road works",
                "procurementType", "WORKS", "currency", "RWF", "originalValue", "1000000000.00",
                "originalCompletionDate", "2027-06-30"), "/api/contracts")).get("id").asText();
        // Files are registered by the committee's officers, not by the CBM.
        mvc.perform(actors.post_(cbm, Map.of("committeeId", committee, "itemType", "B", "title", "Tender document"), "/api/files"))
                .andExpect(status().isForbidden());
        mvc.perform(actors.post_(sec, Map.of("committeeId", committee, "itemType", "C", "title", "Addendum 1"), "/api/files"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("file.contract_required"));
        JsonNode fileC = call(actors.post_(sec, Map.of("committeeId", committee, "itemType", "C", "title", "Addendum 1",
                "contractId", contract), "/api/files"));
        assertThat(fileC.get("fileNumber").asText()).matches("F-\\d{4}/\\d{4}");
        assertThat(fileC.get("targetDate").isNull()).isFalse();
        JsonNode fileB = call(actors.post_(sec, Map.of("committeeId", committee, "itemType", "B",
                "title", "Tender document for school construction", "tenderRef", "T-2026-77"), "/api/files"));
        String fileBId = fileB.get("id").asText();

        // ---- session scheduling: CBM cannot; short notice needs a reason
        Instant soon = Instant.now().plus(2, ChronoUnit.HOURS);
        mvc.perform(actors.post_(cbm, Map.of("committeeId", committee, "kind", "ORDINARY", "scheduledAt", soon.toString()),
                "/api/sessions")).andExpect(status().isForbidden());
        mvc.perform(actors.post_(chair, Map.of("committeeId", committee, "kind", "ORDINARY", "scheduledAt", soon.toString()),
                "/api/sessions")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("session.short_notice_reason_required"));
        JsonNode session = call(actors.post_(chair, Map.of("committeeId", committee, "kind", "ORDINARY",
                "scheduledAt", soon.toString(), "shortNoticeReason", "Bid validity expires next week"), "/api/sessions"));
        String sessionId = session.get("id").asText();
        assertThat(session.get("shortNotice").asBoolean()).isTrue();
        assertThat(session.get("sessionNumber").asText()).matches("PTCA \\d{4}/\\d{3}");

        // ---- agenda scope is enforced
        mvc.perform(actors.post_(chair, Map.of("fileId", fileBId, "itemType", "A"), "/api/sessions/{id}/agenda", sessionId))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("agenda.type_mismatch"));
        mvc.perform(actors.post_(chair, Map.of("itemType", "I", "adminNote", "Budget discussion"), "/api/sessions/{id}/agenda", sessionId))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("agenda.out_of_scope"));
        mvc.perform(actors.post_(chair, Map.of("itemType", "Z", "adminNote", "Any other business"), "/api/sessions/{id}/agenda", sessionId))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("agenda.out_of_scope"));
        mvc.perform(actors.post_(m1, Map.of("fileId", fileBId, "itemType", "B"), "/api/sessions/{id}/agenda", sessionId))
                .andExpect(status().isForbidden());
        call(actors.post_(chair, Map.of("itemType", "I", "adminKind", "ADOPT_MINUTES"), "/api/sessions/{id}/agenda", sessionId));
        String item = call(actors.post_(chair, Map.of("fileId", fileBId, "itemType", "B"), "/api/sessions/{id}/agenda", sessionId))
                .get("id").asText();
        assertThat(call(actors.get_(sec, "/api/files/{id}", fileBId)).get("file").get("stage").asText()).isEqualTo("SCHEDULED");

        // ---- apology and attendance; quorum against committee size 5 (3/5 → 3)
        call(actors.post_(vice, Map.of("kind", "ON_LEAVE", "reason", "Annual leave"), "/api/sessions/{id}/apologies", sessionId));
        JsonNode q = null;
        for (Actor a : List.of(chair, sec)) {
            q = call(actors.put_(chair, Map.of("status", "PRESENT", "clientRecordedAt", Instant.now().toString()),
                    "/api/sessions/{id}/attendance/{m}", sessionId, membership.get(a))).get("quorum");
        }
        assertThat(q.get("met").asBoolean()).isFalse();
        assertThat(q.get("needed").asInt()).isEqualTo(1);
        q = call(actors.put_(chair, Map.of("status", "PRESENT", "clientRecordedAt", Instant.now().toString()),
                "/api/sessions/{id}/attendance/{m}", sessionId, membership.get(m1))).get("quorum");
        assertThat(q.get("met").asBoolean()).isTrue();
        assertThat(q.get("exactlyAtQuorum").asBoolean()).isTrue();
        JsonNode m2Mark = call(actors.put_(chair, Map.of("status", "PRESENT", "clientRecordedAt", Instant.now().toString()),
                "/api/sessions/{id}/attendance/{m}", sessionId, membership.get(m2)));
        assertThat(m2Mark.get("quorum").get("present").asInt()).isEqualTo(4);
        assertThat(m2Mark.get("quorum").get("exactlyAtQuorum").asBoolean()).isFalse();

        // Stale offline mark (captured earlier, based on an old version) is refused.
        mvc.perform(actors.put_(sec, Map.of("status", "ABSENT_UNJUSTIFIED", "baseVersion", -1,
                        "clientRecordedAt", Instant.now().minus(10, ChronoUnit.MINUTES).toString()),
                        "/api/sessions/{id}/attendance/{m}", sessionId, membership.get(m2)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("attendance.conflict"))
                .andExpect(jsonPath("$.params.server.status").value("PRESENT"));

        JsonNode view = call(actors.get_(m1, "/api/sessions/{id}", sessionId));
        JsonNode viceRow = null;
        for (JsonNode m : view.get("members")) {
            if (m.get("userId").asText().equals(vice.userId.toString())) viceRow = m;
        }
        assertThat(viceRow.get("attendance").get("status").asText()).isEqualTo("ON_LEAVE");
        assertThat(viceRow.get("apology").get("timely").asBoolean()).isTrue();
        assertThat(viceRow.get("unnotifiedAbsence").asBoolean()).isFalse();

        // ---- draft recommendation; type I items cannot carry one
        call(actors.post_(sec, Map.of("status", "IN_PROGRESS"), "/api/sessions/{id}/status", sessionId));
        String adminItem = view.get("agenda").get(0).get("id").asText();
        mvc.perform(actors.post_(sec, Map.of("recommendationCode", "AS_SUBMITTED", "recommendationText", "x"),
                "/api/agenda-items/{id}/decision", adminItem)).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("decision.not_applicable"));
        mvc.perform(actors.post_(sec, Map.of("recommendationCode", "AWARD", "recommendationText", "x"),
                "/api/agenda-items/{id}/decision", item)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("decision.invalid_code"));
        JsonNode decision = call(actors.post_(sec, Map.of("recommendationCode", "WITH_CHANGES",
                "recommendationText", "Recommend publication once brand names are replaced with 'or equivalent'."),
                "/api/agenda-items/{id}/decision", item));
        String decisionId = decision.get("id").asText();
        assertThat(decision.get("configSnapshot").get("values").get("voting.rule")).isNotNull();

        // ---- conflict of interest: step-up required; one member declares an interest
        mvc.perform(actors.post_(m1, Map.of("hasInterest", false), "/api/agenda-items/{id}/declarations", item))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("auth.step_up_required"));
        mvc.perform(actors.post_(chair, Map.of(), "/api/decisions/{id}/open-voting", decisionId))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("decision.declarations_pending"));
        for (Actor a : List.of(chair, sec, m1, m2)) {
            actors.stepUp(a);
        }
        for (Actor a : List.of(chair, sec, m1)) {
            call(actors.post_(a, Map.of("hasInterest", false), "/api/agenda-items/{id}/declarations", item));
        }
        call(actors.post_(m2, Map.of("hasInterest", true, "interestKind", "INDIRECT",
                "details", "Spouse employed by a likely bidder"), "/api/agenda-items/{id}/declarations", item));

        // ---- threshold: 4 present, 1 excluded → 3 eligible, 3/5 of 3 → 2 required
        JsonNode opened = call(actors.post_(chair, Map.of(), "/api/decisions/{id}/open-voting", decisionId));
        assertThat(opened.get("presentCount").asInt()).isEqualTo(4);
        assertThat(opened.get("eligibleVoters").asInt()).isEqualTo(3);
        assertThat(opened.get("requiredVotes").asInt()).isEqualTo(2);

        mvc.perform(actors.post_(m2, Map.of("choice", "FOR"), "/api/decisions/{id}/votes", decisionId))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("vote.not_eligible"));
        mvc.perform(actors.post_(m1, Map.of("choice", "AGAINST"), "/api/decisions/{id}/votes", decisionId))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("vote.dissent_reason_required"));
        call(actors.post_(m1, Map.of("choice", "AGAINST", "dissentReason", "Qualification criteria remain disproportionate"),
                "/api/decisions/{id}/votes", decisionId));
        mvc.perform(actors.post_(m1, Map.of("choice", "FOR"), "/api/decisions/{id}/votes", decisionId))
                .andExpect(status().isConflict());
        call(actors.post_(chair, Map.of("choice", "FOR"), "/api/decisions/{id}/votes", decisionId));
        call(actors.post_(sec, Map.of("choice", "FOR"), "/api/decisions/{id}/votes", decisionId));

        JsonNode closed = call(actors.post_(sec, Map.of(), "/api/decisions/{id}/close-voting", decisionId));
        assertThat(closed.get("status").asText()).isEqualTo("ADOPTED");
        assertThat(closed.get("votesFor").asInt()).isEqualTo(2);
        assertThat(closed.get("votesAgainst").asInt()).isEqualTo(1);

        mvc.perform(actors.post_(sec, Map.of(), "/api/decisions/{id}/issue", decisionId)).andExpect(status().isForbidden());
        call(actors.post_(chair, Map.of(), "/api/decisions/{id}/issue", decisionId));
        assertThat(call(actors.get_(sec, "/api/files/{id}", fileBId)).get("file").get("stage").asText())
                .isEqualTo("RECOMMENDATION_ISSUED");

        // Dissent is visible on the decision record.
        JsonNode forItem = call(actors.get_(m2, "/api/agenda-items/{id}/decision", item));
        assertThat(forItem.get("votes").toString()).contains("Qualification criteria remain disproportionate");

        // ---- CBM response is a separate act
        JsonNode pending = call(actors.get_(cbm, "/api/cbm/pending"));
        assertThat(pending.toString()).contains(fileB.get("fileNumber").asText());
        mvc.perform(actors.post_(cbm, Map.of("response", "RETURNED_FOR_CORRECTION"), "/api/decisions/{id}/cbm-response", decisionId))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("cbm.reasons_required"));
        call(actors.post_(cbm, Map.of("response", "RETURNED_FOR_CORRECTION", "reasons", "Timeline section incomplete"),
                "/api/decisions/{id}/cbm-response", decisionId));
        assertThat(call(actors.get_(sec, "/api/files/{id}", fileBId)).get("file").get("stage").asText())
                .isEqualTo("RETURNED_FOR_CORRECTION");
        assertThat(call(actors.get_(sec, "/api/decisions/{id}", decisionId)).get("status").asText()).isEqualTo("ISSUED");

        // ---- COI export uses business numbers only
        MvcResult csv = mvc.perform(actors.get_(chair, "/api/declarations/export").param("sessionId", sessionId)).andReturn();
        String text = csv.getResponse().getContentAsString();
        assertThat(text).contains(fileB.get("fileNumber").asText()).doesNotContain(fileBId);

        // ---- optimistic locking on decision records
        UUID did = UUID.fromString(decisionId);
        TenantContext.runAs(new TenantContext.Scope(inst, chair.userId, false), () -> {
            Decision stale = tx.execute(s -> decisions.findById(did).orElseThrow());
            tx.executeWithoutResult(s -> {
                Decision fresh = decisions.findById(did).orElseThrow();
                fresh.recommendationText = fresh.recommendationText + " ";
                decisions.saveAndFlush(fresh);
            });
            stale.recommendationText = "overwrite";
            assertThatThrownBy(() -> tx.executeWithoutResult(s -> decisions.saveAndFlush(stale)))
                    .isInstanceOf(ObjectOptimisticLockingFailureException.class);
        });

        // ---- another institution sees nothing of this one
        UUID other = TestActors.newInstitution("OT");
        Actor outsider = actors.login(actors.user(other, Set.of(InstitutionRole.READ_ONLY), false, null));
        mvc.perform(actors.get_(outsider, "/api/committees/{id}", committee)).andExpect(status().isNotFound());
        mvc.perform(actors.get_(outsider, "/api/files/{id}", fileBId)).andExpect(status().isNotFound());
        mvc.perform(actors.get_(outsider, "/api/sessions/{id}", sessionId)).andExpect(status().isNotFound());
        mvc.perform(actors.get_(outsider, "/api/decisions/{id}", decisionId)).andExpect(status().isNotFound());
        assertThat(call(actors.get_(outsider, "/api/files"))).isEmpty();
    }
}
