package rw.gov.ptc.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import rw.gov.ptc.auth.api.IdentityAdmin;
import rw.gov.ptc.common.Ids;
import rw.gov.ptc.common.Time;
import rw.gov.ptc.support.Fixtures;
import rw.gov.ptc.support.IntegrationTest;
import rw.gov.ptc.support.Owner;
import rw.gov.ptc.support.TestBeans;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end through HTTP with a real Secretary token (MFA-enrolled, as officers must be). */
@AutoConfigureMockMvc
class EvaluationApiTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    IdentityAdmin identity;
    @Autowired
    TestBeans.CapturingMailer mailer;

    private String postJson(String path, Object body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString();
    }

    private String secretaryToken(Fixtures.Tenant t) throws Exception {
        String email = "sec" + Ids.newId() + "@example.rw";
        UUID userId = identity.invite(new IdentityAdmin.Invitation(t.institutionId(), email, "Secretary", null, null,
                true, "en", Set.of()), null);
        postJson("/api/auth/invitations/accept", Map.of("token", mailer.token(email), "password", "Correct-Horse-42"));
        Owner.jdbc().update("""
                INSERT INTO committee_membership (id, institution_id, committee_id, user_id, role, term_start, term_end)
                VALUES (?, ?, ?, ?, 'SECRETARY', current_date - 1, current_date + 700)
                """, Ids.newId(), t.institutionId(), t.committeeId(), userId);
        JsonNode login = json.readTree(postJson("/api/auth/login", Map.of("email", email, "password", "Correct-Horse-42")));
        String challenge = login.get("challenge").asText();
        String secret = json.readTree(postJson("/api/auth/mfa/enrol/start", Map.of("challenge", challenge))).get("secret").asText();
        JsonNode confirmed = json.readTree(postJson("/api/auth/mfa/enrol/confirm",
                Map.of("challenge", challenge, "code", totp(secret))));
        return confirmed.get("accessToken").asText();
    }

    @Test
    void secretaryLocksCriteriaAndCannotReachAnotherInstitution() throws Exception {
        Fixtures.Tenant t = Fixtures.tenant("EA" + System.nanoTime() % 1_000_000);
        Fixtures.Tenant other = Fixtures.tenant("EB" + System.nanoTime() % 1_000_000);
        UUID file = TestScopes.file(t, "A", null, Time.today().plusDays(60));
        UUID foreign = TestScopes.file(other, "A", null, null);
        String token = secretaryToken(t);

        mvc.perform(get("/api/files/{id}/evaluation", file).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criteriaLocked").value(false))
                .andExpect(jsonPath("$.stages[0].code").value("A1"))
                .andExpect(jsonPath("$.bidValidity.state").value("OK"));

        mvc.perform(post("/api/files/{id}/evaluation/criteria-lock", file).header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criteriaLocked").value(true))
                .andExpect(jsonPath("$.stages[0].nextAction").value("START"))
                .andExpect(jsonPath("$.stages[0].blockers").isEmpty())
                .andExpect(jsonPath("$.stages[3].blockers[0].code").value("evaluation.blocker.previous_stage_incomplete"));

        mvc.perform(post("/api/files/{id}/evaluation/stages/A2/start", file).header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", Ids.newId().toString()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("evaluation.stage_blocked"));

        mvc.perform(get("/api/files/{id}/evaluation", foreign).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/files/{id}/evaluation", "not-a-uuid").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/files/{id}/evaluation", file)).andExpect(status().isUnauthorized());
    }

    private static String totp(String secret) throws Exception {
        long step = Instant.now().getEpochSecond() / 30;
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(base32(secret), "HmacSHA1"));
        byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
        int o = h[h.length - 1] & 0x0F;
        int bin = ((h[o] & 0x7F) << 24) | ((h[o + 1] & 0xFF) << 16) | ((h[o + 2] & 0xFF) << 8) | (h[o + 3] & 0xFF);
        return String.format("%06d", bin % 1_000_000);
    }

    private static byte[] base32(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : s.toUpperCase().toCharArray()) {
            buffer = (buffer << 5) | (c >= 'A' && c <= 'Z' ? c - 'A' : c - '2' + 26);
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
