package rw.gov.ptc.auth.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import rw.gov.ptc.auth.api.IdentityAdmin;
import rw.gov.ptc.auth.api.InstitutionRole;
import rw.gov.ptc.common.Ids;
import rw.gov.ptc.support.Fixtures;
import rw.gov.ptc.support.IntegrationTest;
import rw.gov.ptc.support.Owner;
import rw.gov.ptc.support.TestBeans;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthFlowTest extends IntegrationTest {

    static final String PASSWORD = "Correct-Horse-42";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    IdentityAdmin identity;
    @Autowired
    TestBeans.CapturingMailer mailer;
    @Autowired
    SecretBox box;

    static Fixtures.Tenant tenant;

    @BeforeEach
    void seed() {
        if (tenant == null) {
            tenant = Fixtures.tenant("AU" + System.nanoTime() % 100000);
        }
    }

    private String newUser(Set<InstitutionRole> roles) throws Exception {
        String email = "u" + Ids.newId() + "@example.rw";
        identity.invite(new IdentityAdmin.Invitation(tenant.institutionId(), email, "Test User", null, null,
                false, "en", roles), null);
        mvc.perform(post("/api/auth/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", mailer.token(email), "password", PASSWORD))))
                .andExpect(status().isNoContent());
        return email;
    }

    private MvcResult login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password)))).andReturn();
    }

    private JsonNode body(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString());
    }

    @Test
    void loginRefreshRotationAndReuseDetection() throws Exception {
        String email = newUser(Set.of());
        MvcResult res = login(email, PASSWORD);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        String access = body(res).get("accessToken").asText();
        Cookie rt1 = res.getResponse().getCookie("ptc_rt");
        assertThat(rt1).isNotNull();
        assertThat(rt1.isHttpOnly()).isTrue();
        assertThat(rt1.getSecure()).isTrue();
        assertThat(res.getResponse().getHeader("Set-Cookie")).contains("SameSite=Strict").contains("Max-Age=");

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email));

        // Refresh requires the client header (CSRF defence for the cookie).
        mvc.perform(post("/api/auth/refresh").cookie(rt1)).andExpect(status().isBadRequest());

        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").cookie(rt1).header("X-PTC-Client", "web"))
                .andExpect(status().isOk()).andReturn();
        Cookie rt2 = refreshed.getResponse().getCookie("ptc_rt");
        assertThat(rt2.getValue()).isNotEqualTo(rt1.getValue());
        String access2 = body(refreshed).get("accessToken").asText();

        // Presenting the consumed token again = compromise: family revoked.
        mvc.perform(post("/api/auth/refresh").cookie(rt1).header("X-PTC-Client", "web"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("auth.session_revoked"));
        mvc.perform(post("/api/auth/refresh").cookie(rt2).header("X-PTC-Client", "web"))
                .andExpect(status().isUnauthorized());
        // ...and outstanding access tokens die with the bumped token_version.
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + access2)).andExpect(status().isUnauthorized());

        Integer revocations = Owner.jdbc().queryForObject(
                "SELECT count(*) FROM audit_event WHERE action = 'TOKEN_FAMILY_REVOKED' AND actor_id = (SELECT id FROM app_user WHERE email = ?)",
                Integer.class, email);
        assertThat(revocations).isEqualTo(1);
    }

    @Test
    void roleChangeRevokesAccessTokenImmediately() throws Exception {
        String email = newUser(Set.of());
        String access = body(login(email, PASSWORD)).get("accessToken").asText();
        UUID userId = Owner.jdbc().queryForObject("SELECT id FROM app_user WHERE email = ?", UUID.class, email);
        identity.setInstitutionRoles(userId, tenant.institutionId(), Set.of(InstitutionRole.READ_ONLY), null);
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + access)).andExpect(status().isUnauthorized());
    }

    @Test
    void lockoutAfterRepeatedFailures() throws Exception {
        String email = newUser(Set.of());
        for (int i = 0; i < 5; i++) {
            assertThat(login(email, "wrong-password").getResponse().getStatus()).isEqualTo(401);
        }
        MvcResult locked = login(email, PASSWORD);
        assertThat(locked.getResponse().getStatus()).isEqualTo(423);
        Integer lockEvents = Owner.jdbc().queryForObject(
                "SELECT count(*) FROM audit_event WHERE action = 'ACCOUNT_LOCKED' AND entity_id = (SELECT id FROM app_user WHERE email = ?)",
                Integer.class, email);
        assertThat(lockEvents).isGreaterThanOrEqualTo(1);
    }

    @Test
    void mandatoryMfaEnrolmentAndStepUp() throws Exception {
        String email = newUser(Set.of(InstitutionRole.CBM));
        JsonNode first = body(login(email, PASSWORD));
        assertThat(first.get("status").asText()).isEqualTo("MFA_ENROLMENT_REQUIRED");
        String challenge = first.get("challenge").asText();

        // The challenge is not an access token.
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + challenge)).andExpect(status().isUnauthorized());

        JsonNode enrol = body(mvc.perform(post("/api/auth/mfa/enrol/start").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("challenge", challenge)))).andReturn());
        String secret = enrol.get("secret").asText();
        long step = Instant.now().getEpochSecond() / Totp.STEP_SECONDS;
        JsonNode confirmed = body(mvc.perform(post("/api/auth/mfa/enrol/confirm").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("challenge", challenge,
                        "code", Totp.generate(Totp.unbase32(secret), step))))).andReturn());
        assertThat(confirmed.get("recoveryCodes")).hasSize(10);
        String access = confirmed.get("accessToken").asText();

        // Legally significant acts require recent step-up.
        mvc.perform(post("/api/auth/step-up").header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("code", Totp.generate(Totp.unbase32(secret), step)))))
                .andExpect(status().isUnauthorized()); // same step: replay refused
        mvc.perform(post("/api/auth/step-up").header("Authorization", "Bearer " + access)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("code", Totp.generate(Totp.unbase32(secret), step + 1)))))
                .andExpect(status().isOk());

        // Next login asks for the code; a recovery code works once.
        JsonNode again = body(login(email, PASSWORD));
        assertThat(again.get("status").asText()).isEqualTo("MFA_REQUIRED");
        String recovery = confirmed.get("recoveryCodes").get(0).asText();
        mvc.perform(post("/api/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("challenge", again.get("challenge").asText(), "recoveryCode", recovery))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/mfa/verify").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("challenge", again.get("challenge").asText(), "recoveryCode", recovery))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void administratorsMustEnrolMfa() throws Exception {
        String instAdmin = newUser(Set.of(InstitutionRole.INSTITUTION_ADMIN));
        JsonNode first = body(login(instAdmin, PASSWORD));
        assertThat(first.get("status").asText()).isEqualTo("MFA_ENROLMENT_REQUIRED");
        assertThat(first.has("accessToken")).isFalse();

        String platformAdmin = "u" + Ids.newId() + "@example.rw";
        identity.invite(new IdentityAdmin.Invitation(null, platformAdmin, "Platform Admin", null, null,
                false, "en", Set.of(InstitutionRole.PLATFORM_ADMIN)), null);
        mvc.perform(post("/api/auth/invitations/accept").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("token", mailer.token(platformAdmin), "password", PASSWORD))))
                .andExpect(status().isNoContent());
        assertThat(body(login(platformAdmin, PASSWORD)).get("status").asText()).isEqualTo("MFA_ENROLMENT_REQUIRED");
    }

    @Test
    void jwksAndStrictUuidValidation() throws Exception {
        mvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].d").doesNotExist());
        String access = body(login(newUser(Set.of(InstitutionRole.READ_ONLY)), PASSWORD)).get("accessToken").asText();
        mvc.perform(get("/api/audit").param("entityId", "1-2-3-4-5").header("Authorization", "Bearer " + access))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("invalid_uuid"));
        mvc.perform(get("/api/audit").param("entityId", "not-a-uuid").header("Authorization", "Bearer " + access))
                .andExpect(status().isBadRequest());
    }

    @Test
    void idempotencyKeyReplaysInsteadOfRepeating() throws Exception {
        String access = body(login(newUser(Set.of()), PASSWORD)).get("accessToken").asText();
        String body = "{\"phone\":\"+250788000000\"}";
        mvc.perform(put("/api/me").header("Authorization", "Bearer " + access)
                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("idempotency_key_required"));
        String key = Ids.newId().toString();
        mvc.perform(put("/api/me").header("Authorization", "Bearer " + access).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        mvc.perform(put("/api/me").header("Authorization", "Bearer " + access).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(header().string("Idempotent-Replay", "true"));
        mvc.perform(put("/api/me").header("Authorization", "Bearer " + access).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"other\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void readOnlyUsersCannotWrite() throws Exception {
        String access = body(login(newUser(Set.of(InstitutionRole.READ_ONLY)), PASSWORD)).get("accessToken").asText();
        mvc.perform(put("/api/me").header("Authorization", "Bearer " + access)
                        .header("Idempotency-Key", Ids.newId().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"1\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("read_only"));
    }
}
