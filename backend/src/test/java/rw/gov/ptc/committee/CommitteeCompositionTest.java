package rw.gov.ptc.committee;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import rw.gov.ptc.auth.api.IdentityAdmin;
import rw.gov.ptc.auth.api.InstitutionRole;
import rw.gov.ptc.auth.internal.TestActors;
import rw.gov.ptc.auth.internal.TestActors.Actor;
import rw.gov.ptc.common.Time;
import rw.gov.ptc.support.IntegrationTest;
import rw.gov.ptc.support.TestBeans;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CommitteeCompositionTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    IdentityAdmin identity;
    @Autowired
    TestBeans.CapturingMailer mailer;

    TestActors actors;
    UUID inst;
    Actor cbm;

    @BeforeEach
    void setUp() throws Exception {
        actors = new TestActors(mvc, json, identity, mailer);
        inst = TestActors.newInstitution("CC");
        cbm = actors.login(actors.user(inst, Set.of(InstitutionRole.CBM), false, "CBM"));
    }

    private UUID committee(String code, Map<String, Object> extra) throws Exception {
        var body = new java.util.HashMap<String, Object>(Map.of("name", "Committee " + code, "code", code, "size", 5));
        body.putAll(extra);
        MvcResult r = mvc.perform(actors.post_(cbm, body, "/api/committees")).andExpect(status().isOk()).andReturn();
        return UUID.fromString(actors.body(r).get("id").asText());
    }

    private MvcResult appoint(UUID committee, Actor who, String role) throws Exception {
        return mvc.perform(actors.post_(cbm, Map.of("userId", who.userId, "role", role), "/api/committees/{id}/members", committee))
                .andReturn();
    }

    @Test
    void sizeAndRegulatorAuthorisation() throws Exception {
        mvc.perform(actors.post_(cbm, Map.of("name", "X", "code", "X", "size", 6), "/api/committees"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("committee.size_not_allowed"));
        committee("PTC-A", Map.of());
        mvc.perform(actors.post_(cbm, Map.of("name", "B", "code", "PTC-B", "size", 7), "/api/committees"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("committee.regulator_authorisation_required"));
        committee("PTC-B", Map.of("size", 7, "regulatorAuthRef", "RPPA/123", "regulatorAuthDate", "2026-01-15"));
    }

    @Test
    void cbmAndExcludedOfficesAreBarred() throws Exception {
        UUID c = committee("PTC-A", Map.of());
        MvcResult r = appoint(c, cbm, "MEMBER");
        assertThat(r.getResponse().getStatus()).isEqualTo(422);
        assertThat(actors.body(r).get("code").asText()).isEqualTo("composition.cbm_cannot_be_member");

        Actor auditor = actors.user(inst, Set.of(), false, "INTERNAL_AUDITOR");
        r = appoint(c, auditor, "MEMBER");
        assertThat(r.getResponse().getStatus()).isEqualTo(422);
        assertThat(actors.body(r).get("code").asText()).isEqualTo("composition.excluded_office");

        // The candidates list explains ineligibility up front.
        JsonNode candidates = actors.body(mvc.perform(actors.get_(cbm, "/api/committees/{id}/candidates", c)).andReturn());
        for (JsonNode n : candidates) {
            if (n.get("userId").asText().equals(auditor.userId.toString())) {
                assertThat(n.get("eligible").asBoolean()).isFalse();
                assertThat(n.get("ineligibleReason").asText()).isEqualTo("composition.excluded_office");
            }
        }
    }

    @Test
    void procurementOfficersMustNotFormAMajority() throws Exception {
        UUID c = committee("PTC-A", Map.of());
        assertThat(appoint(c, actors.user(inst, Set.of(), true, null), "SECRETARY").getResponse().getStatus()).isEqualTo(200);
        assertThat(appoint(c, actors.user(inst, Set.of(), true, null), "MEMBER").getResponse().getStatus()).isEqualTo(200);
        MvcResult r = appoint(c, actors.user(inst, Set.of(), true, null), "MEMBER");
        assertThat(r.getResponse().getStatus()).isEqualTo(422);
        JsonNode err = actors.body(r);
        assertThat(err.get("code").asText()).isEqualTo("composition.procurement_officer_majority");
        assertThat(err.get("params").get("procurementOfficers").asInt()).isEqualTo(3);
        assertThat(err.get("params").get("max").asInt()).isEqualTo(2);

        // Officer roles are unique.
        r = appoint(c, actors.user(inst, Set.of(), false, null), "SECRETARY");
        assertThat(actors.body(r).get("code").asText()).isEqualTo("composition.role_taken");
    }

    @Test
    void appointmentRevokesTheMembersCurrentToken() throws Exception {
        UUID c = committee("PTC-A", Map.of());
        Actor member = actors.login(actors.user(inst, Set.of(), false, null));
        mvc.perform(actors.get_(member, "/api/me")).andExpect(status().isOk());
        assertThat(appoint(c, member, "MEMBER").getResponse().getStatus()).isEqualTo(200);
        mvc.perform(actors.get_(member, "/api/me")).andExpect(status().isUnauthorized());
        actors.login(member);
        mvc.perform(actors.get_(member, "/api/me")).andExpect(status().isOk())
                .andExpect(jsonPath("$.committees[0].role").value("MEMBER"));
    }

    @Test
    void wholesaleReplacementCreatesRegulatorTask() throws Exception {
        UUID c = committee("PTC-A", Map.of());
        appoint(c, actors.user(inst, Set.of(), false, null), "CHAIRPERSON");
        appoint(c, actors.user(inst, Set.of(), true, null), "SECRETARY");
        Actor newChair = actors.user(inst, Set.of(), false, null);
        Actor newSec = actors.user(inst, Set.of(), true, null);

        mvc.perform(actors.post_(cbm, Map.of("scope", "ALL", "reason", "short"), "/api/committees/{id}/replacements", c))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("committee.replacement_reason_required"));

        JsonNode res = actors.body(mvc.perform(actors.post_(cbm, Map.of(
                "scope", "ALL",
                "reason", "Audit report found the committee failed to meet for six months",
                "appointments", List.of(Map.of("userId", newChair.userId, "role", "CHAIRPERSON"),
                        Map.of("userId", newSec.userId, "role", "SECRETARY"))),
                "/api/committees/{id}/replacements", c)).andExpect(status().isOk()).andReturn());
        JsonNode task = res.get("regulatorTask");
        assertThat(task.get("kind").asText()).isEqualTo("REGULATOR_NOTIFICATION");
        assertThat(task.get("dueDate").asText()).isEqualTo(Time.today().plusDays(7).toString());

        JsonNode detail = actors.body(mvc.perform(actors.get_(cbm, "/api/committees/{id}", c)).andReturn());
        assertThat(detail.get("members")).hasSize(2);

        // Completing the task requires the reference of the notification.
        mvc.perform(actors.post_(cbm, Map.of(), "/api/tasks/{id}/complete", task.get("id").asText()))
                .andExpect(status().isBadRequest());
        mvc.perform(actors.post_(cbm, Map.of("reference", "Letter 2026/77"), "/api/tasks/{id}/complete", task.get("id").asText()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void onlyTheCbmConstitutesCommittees() throws Exception {
        Actor admin = actors.login(actors.user(inst, Set.of(InstitutionRole.INSTITUTION_ADMIN), false, null));
        mvc.perform(actors.post_(admin, Map.of("name", "X", "code", "X", "size", 5), "/api/committees"))
                .andExpect(status().isForbidden());
    }
}
