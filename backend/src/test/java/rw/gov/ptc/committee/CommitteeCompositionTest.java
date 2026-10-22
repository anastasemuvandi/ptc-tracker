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
import rw.gov.ptc.auth.internal.TestActors.Actor;
import rw.gov.ptc.auth.internal.TestActors;
import rw.gov.ptc.common.Time;
import rw.gov.ptc.support.IntegrationTest;
import rw.gov.ptc.support.Owner;
import rw.gov.ptc.support.TestBeans;

import java.time.LocalDate;
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
        var body = new java.util.HashMap<String, Object>(Map.of("name", "Committee " + code, "periodStart", Time.today().toString()));
        body.putAll(extra);
        MvcResult r = mvc.perform(actors.post_(cbm, body, "/api/committees")).andExpect(status().isOk()).andReturn();
        return UUID.fromString(actors.body(r).get("id").asText());
    }

    private MvcResult appoint(UUID committee, Actor who, String role) throws Exception {
        return mvc.perform(actors.post_(cbm, Map.of("userId", who.userId, "role", role), "/api/committees/{id}/members", committee))
                .andReturn();
    }

    @Test
    void cbmGivesAUniqueNameAndSchedulesTheStart() throws Exception {
        mvc.perform(actors.post_(cbm, Map.of("name", "PTC A"), "/api/committees"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("committee.invalid"));

        // The CBM only schedules the start; the end, code and size follow.
        LocalDate start = Time.today().plusDays(10);
        UUID a = committee("x", Map.of("name", "PTC A", "periodStart", start.toString()));
        JsonNode detail = actors.body(mvc.perform(actors.get_(cbm, "/api/committees/{id}", a)).andReturn());
        assertThat(detail.get("code").asText()).isEqualTo("PTCA");
        assertThat(detail.get("size").asInt()).isEqualTo(5);
        assertThat(detail.get("currentPeriod").get("startsOn").asText()).isEqualTo(start.toString());
        assertThat(detail.get("currentPeriod").get("endsOn").asText()).isEqualTo(start.plusYears(3).minusDays(1).toString());

        // Seats appointed before a scheduled start begin with the period.
        Actor chair = actors.user(inst, Set.of(), false, null);
        JsonNode seat = actors.body(appoint(a, chair, "CHAIRPERSON")).get("membership");
        assertThat(seat.get("termStart").asText()).isEqualTo(start.toString());

        // Unique in the institution, ignoring case and spacing, dissolved committees included.
        mvc.perform(actors.post_(cbm, Map.of("name", "  ptc   a ", "periodStart", Time.today().toString()), "/api/committees"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("committee.name_taken"));
        Owner.jdbc().update("UPDATE committee SET status = 'DISSOLVED' WHERE id = ?", a);
        mvc.perform(actors.post_(cbm, Map.of("name", "PTC A", "periodStart", Time.today().toString()), "/api/committees"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("committee.name_taken"));

        // A further committee needs no extra input; a code clash gets a number.
        UUID b = committee("x", Map.of("name", "PTC-A"));
        assertThat(actors.body(mvc.perform(actors.get_(cbm, "/api/committees/{id}", b)).andReturn()).get("code").asText())
                .isEqualTo("PTCA2");
        mvc.perform(actors.put_(cbm, Map.of("name", "PTC a"), "/api/committees/{id}", b))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("committee.name_taken"));

        // Another institution may use the same name.
        UUID other = TestActors.newInstitution("CO");
        Actor otherCbm = actors.login(actors.user(other, Set.of(InstitutionRole.CBM), false, "CBM"));
        mvc.perform(actors.post_(otherCbm, Map.of("name", "PTC A", "periodStart", Time.today().toString()), "/api/committees"))
                .andExpect(status().isOk());
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

        mvc.perform(actors.post_(cbm, Map.of("scope", "ALL", "reason", "short"), "/api/committees/{id}/replacements", c))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("committee.replacement_reason_required"));

        JsonNode res = actors.body(mvc.perform(actors.post_(cbm, Map.of(
                "scope", "ALL",
                "reason", "Audit report found the committee failed to meet for six months",
                "appointments", List.of(Map.of("userId", newChair.userId, "role", "CHAIRPERSON"))),
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
    void periodsEndAutomaticallyAndFollowEachAppointment() throws Exception {
        String today = Time.today().toString();
        String firstStart = Time.today().minusYears(2).toString();
        String firstEnd = Time.today().minusYears(2).plusMonths(36).minusDays(1).toString();
        UUID c = committee("PTC-A", Map.of("periodStart", firstStart));
        JsonNode first = actors.body(mvc.perform(actors.get_(cbm, "/api/committees/{id}", c)).andReturn()).get("currentPeriod");
        assertThat(first.get("endsOn").asText()).isEqualTo(firstEnd);

        // Seats end with the period they start in.
        Actor chair = actors.user(inst, Set.of(), false, null);
        Actor secretary = actors.user(inst, Set.of(), true, null);
        assertThat(actors.body(appoint(c, chair, "CHAIRPERSON")).get("membership").get("termEnd").asText()).isEqualTo(firstEnd);
        appoint(c, secretary, "SECRETARY");

        // Replacing all members appoints the committee again from today; the Secretary stays.
        Actor newChair = actors.user(inst, Set.of(), false, null);
        JsonNode res = actors.body(mvc.perform(actors.post_(cbm, Map.of("scope", "ALL",
                "reason", "The committee's mandate was withdrawn by the accounting officer",
                "appointments", List.of(Map.of("userId", newChair.userId, "role", "CHAIRPERSON"))),
                "/api/committees/{id}/replacements", c)).andExpect(status().isOk()).andReturn());
        String secondEnd = res.get("period").get("endsOn").asText();
        assertThat(res.get("period").get("startsOn").asText()).isEqualTo(today);
        assertThat(secondEnd).isEqualTo(Time.today().plusMonths(36).minusDays(1).toString());
        assertThat(res.get("appointed").get(0).get("termEnd").asText()).isEqualTo(secondEnd);

        JsonNode detail = actors.body(mvc.perform(actors.get_(cbm, "/api/committees/{id}", c)).andReturn());
        assertThat(detail.get("name").asText()).isEqualTo("Committee PTC-A");
        assertThat(detail.get("periods")).hasSize(2);
        assertThat(detail.get("periods").get(1).get("closedOn").asText()).isEqualTo(Time.today().minusDays(1).toString());
        JsonNode members = detail.get("members");
        assertThat(members).hasSize(2);
        JsonNode sec = members.get(1);
        assertThat(sec.get("role").asText()).isEqualTo("SECRETARY");
        assertThat(sec.get("userId").asText()).isEqualTo(secretary.userId.toString());
        assertThat(sec.get("termEnd").asText()).isEqualTo(secondEnd);

        // A member of the past committee may join the next one.
        assertThat(appoint(c, chair, "MEMBER").getResponse().getStatus()).isEqualTo(200);

        mvc.perform(actors.post_(cbm, Map.of(), "/api/committees/{id}/periods", c))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("committee.period_start_required"));
        mvc.perform(actors.post_(cbm, Map.of("startsOn", Time.today().plusDays(5).toString()), "/api/committees/{id}/periods", c))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("committee.period_future"));
        mvc.perform(actors.post_(cbm, Map.of("startsOn", today), "/api/committees/{id}/periods", c))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("committee.period_overlap"));
        Actor officer = actors.login(actors.user(inst, Set.of(), true, null));
        mvc.perform(actors.post_(officer, Map.of(), "/api/committees/{id}/periods", c)).andExpect(status().isForbidden());
    }

    @Test
    void onlyTheCbmConstitutesCommittees() throws Exception {
        Actor admin = actors.login(actors.user(inst, Set.of(InstitutionRole.INSTITUTION_ADMIN), false, null));
        mvc.perform(actors.post_(admin, Map.of("name", "X", "periodStart", Time.today().toString()), "/api/committees"))
                .andExpect(status().isForbidden());
    }
}
