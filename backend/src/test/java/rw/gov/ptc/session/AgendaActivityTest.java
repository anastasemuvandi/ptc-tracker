package rw.gov.ptc.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import rw.gov.ptc.auth.api.IdentityAdmin;
import rw.gov.ptc.auth.api.InstitutionRole;
import rw.gov.ptc.auth.internal.TestActors;
import rw.gov.ptc.auth.internal.TestActors.Actor;
import rw.gov.ptc.common.Time;
import rw.gov.ptc.support.IntegrationTest;
import rw.gov.ptc.support.TestBeans;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Activities scheduled per agenda file, their recording, the link to the evaluation, and carry-over. */
@AutoConfigureMockMvc
class AgendaActivityTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    IdentityAdmin identity;
    @Autowired
    TestBeans.CapturingMailer mailer;

    TestActors actors;

    private JsonNode call(RequestBuilder b) throws Exception {
        MvcResult r = mvc.perform(b).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return actors.body(r);
    }

    private String session(Actor chair, UUID committee) throws Exception {
        return call(actors.post_(chair, Map.of("committeeId", committee, "kind", "ORDINARY",
                "scheduledAt", Instant.now().plus(2, ChronoUnit.HOURS).toString(),
                "shortNoticeReason", "Bid validity expires next week"), "/api/sessions")).get("id").asText();
    }

    private JsonNode activity(Actor who, String sessionId, String fileId, String code) throws Exception {
        for (JsonNode item : call(actors.get_(who, "/api/sessions/{id}", sessionId)).get("agenda")) {
            if (fileId.equals(item.path("fileId").asText())) {
                for (JsonNode a : item.get("activities")) {
                    if (code.equals(a.get("activityCode").asText())) return a;
                }
            }
        }
        throw new AssertionError("No activity " + code);
    }

    @Test
    void activitiesAreScheduledRecordedLinkedAndCarriedOver() throws Exception {
        actors = new TestActors(mvc, json, identity, mailer);
        UUID inst = TestActors.newInstitution("AG");
        Actor cbm = actors.login(actors.user(inst, Set.of(InstitutionRole.CBM), false, null));
        UUID committee = UUID.fromString(call(actors.post_(cbm, Map.of("name", "PTC A", "periodStart", Time.today().toString()),
                "/api/committees")).get("id").asText());
        Actor chair = actors.user(inst, Set.of(), false, null);
        Actor sec = actors.user(inst, Set.of(), true, null);
        call(actors.post_(cbm, Map.of("userId", chair.userId, "role", "CHAIRPERSON"), "/api/committees/{id}/members", committee));
        call(actors.post_(cbm, Map.of("userId", sec.userId, "role", "SECRETARY"), "/api/committees/{id}/members", committee));
        actors.login(chair);
        actors.login(sec);

        String fileB = call(actors.post_(sec, Map.of("committeeId", committee, "itemType", "B",
                "title", "Tender document for school construction"), "/api/files")).get("id").asText();
        String fileA = call(actors.post_(sec, Map.of("committeeId", committee, "itemType", "A",
                "title", "Evaluation of school construction bids", "tenderRef", "T-2026-77"), "/api/files")).get("id").asText();

        // ---- scheduling: activities are named per file and must suit its type
        String first = session(chair, committee);
        mvc.perform(actors.post_(sec, Map.of("itemType", "B", "fileId", fileB, "activities", List.of()), "/api/sessions/{id}/agenda", first))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("agenda.activity_required"));
        mvc.perform(actors.post_(sec, Map.of("itemType", "B", "fileId", fileB, "activities", List.of("AMENDMENT_REVIEW")),
                "/api/sessions/{id}/agenda", first))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("agenda.activity_unknown"));
        call(actors.post_(sec, Map.of("itemType", "B", "fileId", fileB, "activities", List.of("DOCUMENT_REVIEW")), "/api/sessions/{id}/agenda", first));
        call(actors.post_(sec, Map.of("itemType", "A", "fileId", fileA, "activities", List.of("A1", "A2")), "/api/sessions/{id}/agenda", first));
        String review = activity(sec, first, fileB, "DOCUMENT_REVIEW").get("id").asText();
        String a1 = activity(sec, first, fileA, "A1").get("id").asText();
        String a2 = activity(sec, first, fileA, "A2").get("id").asText();
        assertThat(activity(sec, first, fileA, "A1").get("status").asText()).isEqualTo("PLANNED");

        // ---- recording happens while the session is in progress
        mvc.perform(actors.post_(sec, Map.of("status", "COMPLETED"), "/api/sessions/{id}/activities/{a}", first, review))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("activity.session_not_in_progress"));
        call(actors.post_(sec, Map.of("status", "IN_PROGRESS"), "/api/sessions/{id}/status", first));
        mvc.perform(actors.post_(sec, Map.of("status", "NOT_COMPLETED"), "/api/sessions/{id}/activities/{a}", first, review))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("activity.reason_required"));
        call(actors.post_(sec, Map.of("status", "NOT_COMPLETED", "note", "Specifications not yet received"),
                "/api/sessions/{id}/activities/{a}", first, review));

        // ---- type A: completing the activity completes the evaluation stage, with its checks
        mvc.perform(actors.post_(sec, Map.of("status", "COMPLETED", "note", "All bidders eligible"),
                        "/api/sessions/{id}/activities/{a}", first, a1))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("evaluation.stage_blocked"));
        call(actors.post_(sec, Map.of("confirmed", true), "/api/files/{id}/evaluation/criteria-lock", fileA));
        call(actors.post_(sec, Map.of(), "/api/files/{id}/evaluation/stages/A1/start", fileA));
        call(actors.put_(sec, Map.of("bidderRef", "B-01", "result", "PASS"), "/api/files/{id}/evaluation/stages/A1/bidders", fileA));
        // ... and completing the stage in the evaluation completes the activity.
        call(actors.post_(sec, Map.of("outcome", "All bidders eligible"), "/api/files/{id}/evaluation/stages/A1/complete", fileA));
        assertThat(activity(sec, first, fileA, "A1").get("status").asText()).isEqualTo("COMPLETED");
        mvc.perform(actors.post_(sec, Map.of("status", "NOT_COMPLETED", "note", "x"), "/api/sessions/{id}/activities/{a}", first, a1))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("activity.stage_completed"));

        // ---- the session closes only once every activity is recorded
        mvc.perform(actors.post_(sec, Map.of("status", "HELD"), "/api/sessions/{id}/status", first))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("session.activities_unrecorded"));
        call(actors.post_(sec, Map.of("status", "NOT_COMPLETED", "note", "Technical report not ready"),
                "/api/sessions/{id}/activities/{a}", first, a2));
        call(actors.post_(sec, Map.of("status", "HELD"), "/api/sessions/{id}/status", first));

        // ---- carry-over to the next session, once
        String second = session(chair, committee);
        JsonNode pending = call(actors.get_(sec, "/api/sessions/{id}/carry-over", second));
        assertThat(pending).hasSize(2);
        mvc.perform(actors.post_(sec, Map.of("itemType", "A", "fileId", fileA, "activities", List.of("A1")), "/api/sessions/{id}/agenda", second))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("agenda.activity_done"));
        call(actors.post_(sec, Map.of("activityIds", List.of(review, a2)), "/api/sessions/{id}/carry-over", second));
        JsonNode carried = activity(sec, second, fileA, "A2");
        assertThat(carried.get("status").asText()).isEqualTo("PLANNED");
        assertThat(carried.get("carriedFromSession").asText()).matches("PTCA \\d{4}/\\d{3}");
        assertThat(activity(sec, second, fileB, "DOCUMENT_REVIEW").get("carriedFromId").asText()).isEqualTo(review);
        assertThat(call(actors.get_(sec, "/api/sessions/{id}/carry-over", second))).isEmpty();
        mvc.perform(actors.post_(sec, Map.of("activityIds", List.of(a2)), "/api/sessions/{id}/carry-over", second))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("agenda.carry_over_invalid"));
    }
}
