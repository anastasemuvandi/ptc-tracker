package rw.gov.ptc.auth.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import rw.gov.ptc.auth.api.IdentityAdmin;
import rw.gov.ptc.auth.api.InstitutionRole;
import rw.gov.ptc.common.Ids;
import rw.gov.ptc.support.Owner;
import rw.gov.ptc.support.TestBeans;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Test helper: creates real users through the invitation flow, signs them in
 * (enrolling TOTP where MFA is mandatory) and performs step-up, so tests go
 * through the same token and RLS path as production.
 */
public class TestActors {

    public static final String PASSWORD = "Correct-Horse-42";

    public static final class Actor {
        public final UUID userId;
        public final String email;
        public String token;
        public String totpSecret;
        long lastStep;

        Actor(UUID userId, String email) {
            this.userId = userId;
            this.email = email;
        }
    }

    private final MockMvc mvc;
    private final ObjectMapper json;
    private final IdentityAdmin identity;
    private final TestBeans.CapturingMailer mailer;

    public TestActors(MockMvc mvc, ObjectMapper json, IdentityAdmin identity, TestBeans.CapturingMailer mailer) {
        this.mvc = mvc;
        this.json = json;
        this.identity = identity;
        this.mailer = mailer;
    }

    public static UUID newInstitution(String prefix) {
        UUID id = Ids.newId();
        Owner.jdbc().update("INSERT INTO institution (id, name, abbreviation, institution_type) VALUES (?, ?, ?, 'MINISTRY')",
                id, "Institution " + prefix, prefix + (System.nanoTime() % 1_000_000));
        return id;
    }

    /** Invited and activated user (not yet signed in). */
    public Actor user(UUID institutionId, Set<InstitutionRole> roles, boolean procurementOfficer, String office)
            throws Exception {
        String email = "u" + Ids.newId() + "@example.rw";
        UUID id = identity.invite(new IdentityAdmin.Invitation(institutionId, email, "User " + email.substring(1, 9),
                null, office, procurementOfficer, "en", roles), null);
        mvc.perform(post("/api/auth/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("token", mailer.token(email), "password", PASSWORD))));
        return new Actor(id, email);
    }

    /** Signs in, enrolling or answering TOTP as required. */
    public Actor login(Actor a) throws Exception {
        JsonNode res = body(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", a.email, "password", PASSWORD)))).andReturn());
        String status = res.get("status").asText();
        switch (status) {
            case "OK" -> a.token = res.get("accessToken").asText();
            case "MFA_ENROLMENT_REQUIRED" -> {
                String challenge = res.get("challenge").asText();
                JsonNode enrol = body(mvc.perform(post("/api/auth/mfa/enrol/start").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("challenge", challenge)))).andReturn());
                a.totpSecret = enrol.get("secret").asText();
                JsonNode confirmed = body(mvc.perform(post("/api/auth/mfa/enrol/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("challenge", challenge, "code", nextCode(a))))).andReturn());
                a.token = confirmed.get("accessToken").asText();
            }
            case "MFA_REQUIRED" -> {
                JsonNode verified = body(mvc.perform(post("/api/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("challenge", res.get("challenge").asText(),
                                "code", nextCode(a))))).andReturn());
                a.token = verified.get("accessToken").asText();
            }
            default -> throw new IllegalStateException(status);
        }
        return a;
    }

    /** Re-authenticates for legally significant acts (TOTP if enrolled, else password). */
    public void stepUp(Actor a) throws Exception {
        Map<String, String> body = a.totpSecret != null ? Map.of("code", nextCode(a)) : Map.of("password", PASSWORD);
        MvcResult r = mvc.perform(post("/api/auth/step-up").header("Authorization", "Bearer " + a.token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andReturn();
        if (r.getResponse().getStatus() != 200) {
            throw new IllegalStateException("step-up failed: " + r.getResponse().getContentAsString());
        }
    }

    /** A code for a step not used before (the server refuses replays). */
    private String nextCode(Actor a) {
        long now = Instant.now().getEpochSecond() / Totp.STEP_SECONDS;
        long step = Math.max(now, a.lastStep + 1);
        if (step > now + 1) {
            throw new IllegalStateException("TOTP window exhausted for this test actor");
        }
        a.lastStep = step;
        return Totp.generate(Totp.unbase32(a.totpSecret), step);
    }

    // --------------------------------------------------------------- requests

    public MockHttpServletRequestBuilder get_(Actor a, String path, Object... vars) {
        return get(path, vars).header("Authorization", "Bearer " + a.token);
    }

    public MockHttpServletRequestBuilder post_(Actor a, Object body, String path, Object... vars) throws Exception {
        return write(post(path, vars), a, body);
    }

    public MockHttpServletRequestBuilder put_(Actor a, Object body, String path, Object... vars) throws Exception {
        return write(put(path, vars), a, body);
    }

    public MockHttpServletRequestBuilder delete_(Actor a, String path, Object... vars) {
        return delete(path, vars).header("Authorization", "Bearer " + a.token)
                .header("Idempotency-Key", Ids.newId().toString());
    }

    private MockHttpServletRequestBuilder write(MockHttpServletRequestBuilder b, Actor a, Object body) throws Exception {
        return b.header("Authorization", "Bearer " + a.token).header("Idempotency-Key", Ids.newId().toString())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body == null ? Map.of() : body));
    }

    public JsonNode body(MvcResult r) throws Exception {
        String s = r.getResponse().getContentAsString();
        return s.isEmpty() ? json.nullNode() : json.readTree(s);
    }
}
