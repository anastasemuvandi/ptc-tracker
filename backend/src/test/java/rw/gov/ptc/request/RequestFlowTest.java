package rw.gov.ptc.request;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import rw.gov.ptc.auth.api.IdentityAdmin;
import rw.gov.ptc.auth.api.InstitutionRole;
import rw.gov.ptc.auth.internal.TestActors;
import rw.gov.ptc.auth.internal.TestActors.Actor;
import rw.gov.ptc.common.Ids;
import rw.gov.ptc.common.Time;
import rw.gov.ptc.institution.service.RequesterAccessService;
import rw.gov.ptc.support.IntegrationTest;
import rw.gov.ptc.support.Owner;
import rw.gov.ptc.support.TestBeans;
import rw.gov.ptc.tenancy.TenantContext;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Requesters onboarded by the administrator submit requests with documents; officers accept or return them. */
@AutoConfigureMockMvc
class RequestFlowTest extends IntegrationTest {

    private static final byte[] PDF = "%PDF-1.4\n% supporting memo\n".getBytes(StandardCharsets.US_ASCII);

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    IdentityAdmin identity;
    @Autowired
    TestBeans.CapturingMailer mailer;
    @Autowired
    RequesterAccessService requesterAccess;

    TestActors actors;

    private JsonNode call(RequestBuilder b) throws Exception {
        MvcResult r = mvc.perform(b).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return actors.body(r);
    }

    private RequestBuilder upload(Actor a, String requestId, String name, String type, byte[] content) {
        return multipart("/api/requests/{id}/documents", requestId)
                .file(new MockMultipartFile("file", name, type, content))
                .header("Authorization", "Bearer " + a.token).header("Idempotency-Key", Ids.newId().toString());
    }

    @Test
    void requesterSubmitsAndOfficersDecide() throws Exception {
        actors = new TestActors(mvc, json, identity, mailer);
        UUID inst = TestActors.newInstitution("RQ");
        Actor admin = actors.login(actors.user(inst, Set.of(InstitutionRole.INSTITUTION_ADMIN), false, null));
        Actor cbm = actors.login(actors.user(inst, Set.of(InstitutionRole.CBM), false, null));
        UUID committee = UUID.fromString(call(actors.post_(cbm, Map.of("name", "PTC A", "code", "PTCA", "size", 5),
                "/api/committees")).get("id").asText());
        Actor chair = actors.user(inst, Set.of(), false, null);
        Actor sec = actors.user(inst, Set.of(), true, null);
        call(actors.post_(cbm, Map.of("userId", chair.userId, "role", "CHAIRPERSON"), "/api/committees/{id}/members", committee));
        call(actors.post_(cbm, Map.of("userId", sec.userId, "role", "SECRETARY"), "/api/committees/{id}/members", committee));
        actors.login(chair);
        actors.login(sec);

        // ---- onboarding is the administrator's: a requester needs short- or long-term access
        mvc.perform(actors.post_(admin, Map.of("email", "req.a@example.rw", "fullName", "Contract Manager", "locale", "en",
                        "roles", List.of("REQUESTER")), "/api/users"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("user.requester_category_required"));
        mvc.perform(actors.post_(admin, Map.of("email", "req.a@example.rw", "fullName", "Contract Manager", "locale", "en",
                        "roles", List.of("REQUESTER"), "requesterCategory", "SHORT_TERM"), "/api/users"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("user.access_until_required"));
        mvc.perform(actors.post_(cbm, Map.of("email", "req.a@example.rw", "fullName", "Contract Manager", "locale", "en",
                        "roles", List.of("REQUESTER"), "requesterCategory", "LONG_TERM"), "/api/users"))
                .andExpect(status().isForbidden());
        String invited = call(actors.post_(admin, Map.of("email", "req.a@example.rw", "fullName", "Contract Manager",
                "locale", "en", "roles", List.of("REQUESTER"), "requesterCategory", "SHORT_TERM",
                "accessUntil", Time.today().plusDays(30).toString()), "/api/users")).get("id").asText();
        assertThat(mailer.token("req.a@example.rw")).isNotBlank();
        JsonNode row = null;
        for (JsonNode u : call(actors.get_(admin, "/api/users"))) {
            if (invited.equals(u.get("id").asText())) row = u;
        }
        assertThat(row.get("requesterCategory").asText()).isEqualTo("SHORT_TERM");

        Actor requester = actors.user(inst, Set.of(InstitutionRole.REQUESTER), false, null);
        call(actors.put_(admin, Map.of("category", "LONG_TERM"), "/api/users/{id}/requester-access", requester.userId));
        actors.login(requester);
        Actor other = actors.user(inst, Set.of(InstitutionRole.REQUESTER), false, null);
        call(actors.put_(admin, Map.of("category", "LONG_TERM"), "/api/users/{id}/requester-access", other.userId));
        actors.login(other);

        // ---- drafting: allowed types, contract reference, documents
        mvc.perform(actors.post_(requester, Map.of("itemType", "A", "title", "x", "description", "y"), "/api/requests"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("request.invalid_type"));
        mvc.perform(actors.post_(requester, Map.of("itemType", "C", "title", "Addendum 2", "description", "Extra drainage"), "/api/requests"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("request.contract_ref_required"));
        JsonNode draft = call(actors.post_(requester, Map.of("itemType", "C", "title", "Addendum 2 — drainage works",
                "description", "Additional drainage on km 4–6 found during works", "contractRef", "CT-2026-009"), "/api/requests"));
        String id = draft.get("id").asText();
        assertThat(draft.get("requestNumber").asText()).matches("REQ-\\d{4}/\\d{4}");
        assertThat(draft.get("status").asText()).isEqualTo("DRAFT");

        mvc.perform(actors.post_(requester, Map.of("declarationConfirmed", true), "/api/requests/{id}/submit", id))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("request.documents_required"));
        mvc.perform(upload(requester, id, "memo.pdf", "application/pdf", "hello".getBytes(StandardCharsets.US_ASCII)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("request.document_type"));
        mvc.perform(upload(requester, id, "tool.exe", "application/x-msdownload", PDF))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("request.document_type"));
        String doc = actors.body(mvc.perform(upload(requester, id, "C:\\temp\\site-report.pdf", "application/pdf", PDF))
                .andExpect(status().isOk()).andReturn()).get("id").asText();
        mvc.perform(actors.post_(requester, Map.of("declarationConfirmed", false), "/api/requests/{id}/submit", id))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("request.declaration_required"));

        // A draft is the requester's alone.
        mvc.perform(actors.get_(sec, "/api/requests/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(actors.get_(other, "/api/requests/{id}", id)).andExpect(status().isNotFound());

        call(actors.post_(requester, Map.of("declarationConfirmed", true), "/api/requests/{id}/submit", id));
        mvc.perform(actors.put_(requester, Map.of("itemType", "C", "title", "t", "description", "d", "contractRef", "x"),
                "/api/requests/{id}", id))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("request.not_editable"));

        // ---- officers see it, read the documents, and return or accept it
        JsonNode inbox = call(actors.get_(sec, "/api/requests/inbox"));
        assertThat(inbox).hasSize(1);
        assertThat(inbox.get(0).get("documentCount").asInt()).isEqualTo(1);
        mvc.perform(actors.get_(requester, "/api/requests/inbox")).andExpect(status().isForbidden());
        MvcResult file = mvc.perform(actors.get_(chair, "/api/requests/{id}/documents/{d}", id, doc)).andReturn();
        assertThat(file.getResponse().getContentAsByteArray()).isEqualTo(PDF);
        assertThat(file.getResponse().getHeader("Content-Disposition")).contains("site-report.pdf");

        mvc.perform(actors.post_(sec, Map.of(), "/api/requests/{id}/return", id))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("request.reason_required"));
        call(actors.post_(sec, Map.of("reason", "Attach the supervising engineer's report"), "/api/requests/{id}/return", id));
        assertThat(call(actors.get_(requester, "/api/requests/{id}", id)).get("decisionNote").asText())
                .isEqualTo("Attach the supervising engineer's report");
        mvc.perform(upload(requester, id, "engineer-report.pdf", "application/pdf", PDF)).andExpect(status().isOk());
        call(actors.post_(requester, Map.of("declarationConfirmed", true), "/api/requests/{id}/submit", id));

        String contract = call(actors.post_(sec, Map.of("contractRef", "CT-2026-009", "title", "Road works",
                "procurementType", "WORKS", "currency", "RWF", "originalValue", "500000000.00",
                "originalCompletionDate", "2027-06-30"), "/api/contracts")).get("id").asText();
        mvc.perform(actors.post_(cbm, Map.of("committeeId", committee, "contractId", contract), "/api/requests/{id}/accept", id))
                .andExpect(status().isForbidden());
        JsonNode accepted = call(actors.post_(sec, Map.of("committeeId", committee, "contractId", contract),
                "/api/requests/{id}/accept", id));
        assertThat(accepted.get("status").asText()).isEqualTo("ACCEPTED");
        String fileId = accepted.get("fileId").asText();
        mvc.perform(actors.post_(sec, Map.of("reason", "late"), "/api/requests/{id}/return", id)).andExpect(status().isConflict());

        // ---- the requester follows the file, not the committee's work
        JsonNode mine = call(actors.get_(requester, "/api/requests/{id}", id));
        assertThat(mine.get("file").get("fileNumber").asText()).matches("F-\\d{4}/\\d{4}");
        assertThat(mine.get("file").get("stage").asText()).isEqualTo("RECEIVED");
        mvc.perform(actors.get_(requester, "/api/files/{id}", fileId)).andExpect(status().is4xxClientError());
        JsonNode behind = call(actors.get_(sec, "/api/files/{id}/request", fileId));
        assertThat(behind.get("documents")).hasSize(2);
        assertThat(call(actors.get_(requester, "/api/requests/mine"))).hasSize(1);

        // ---- short-term access ends on its date: the account is disabled
        Owner.jdbc().update("UPDATE app_user SET requester_category = 'SHORT_TERM', access_until = ? WHERE id = ?",
                Time.today().minusDays(1), other.userId);
        int ended = TenantContext.callAs(new TenantContext.Scope(inst, null, false), () -> requesterAccess.offboardExpired(Time.today()));
        assertThat(ended).isEqualTo(1);
        assertThat(Owner.jdbc().queryForObject("SELECT status FROM app_user WHERE id = ?", String.class, other.userId))
                .isEqualTo("DISABLED");
        mvc.perform(actors.get_(other, "/api/requests/mine")).andExpect(status().isUnauthorized());
    }
}
