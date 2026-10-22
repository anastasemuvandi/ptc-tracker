package rw.gov.ptc.auth.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import rw.gov.ptc.auth.api.IdentityAdmin;
import rw.gov.ptc.auth.api.InstitutionRole;
import rw.gov.ptc.auth.internal.TestActors.Actor;
import rw.gov.ptc.support.IntegrationTest;
import rw.gov.ptc.support.Owner;
import rw.gov.ptc.support.TestBeans;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The platform administrator can recover an institution administrator's lost authenticator, and nothing more. */
@AutoConfigureMockMvc
class PlatformMfaResetTest extends IntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    IdentityAdmin identity;
    @Autowired
    TestBeans.CapturingMailer mailer;

    @Test
    void platformAdminResetsInstitutionAdminMfa() throws Exception {
        TestActors actors = new TestActors(mvc, json, identity, mailer);
        UUID inst = TestActors.newInstitution("PM");
        UUID other = TestActors.newInstitution("PO");
        Actor platform = actors.login(actors.user(null, Set.of(InstitutionRole.PLATFORM_ADMIN), false, null));
        Actor admin = actors.login(actors.user(inst, Set.of(InstitutionRole.INSTITUTION_ADMIN), false, null));
        Actor cbm = actors.login(actors.user(inst, Set.of(InstitutionRole.CBM), false, null));
        String resetPath = "/api/platform/institutions/{id}/admins/{userId}/reset-mfa";

        JsonNode admins = actors.body(mvc.perform(actors.get_(platform, "/api/platform/institutions/{id}/admins", inst))
                .andExpect(status().isOk()).andReturn());
        assertThat(admins).hasSize(1);
        assertThat(admins.get(0).get("id").asText()).isEqualTo(admin.userId.toString());
        assertThat(admins.get(0).get("mfaEnrolled").asBoolean()).isTrue();

        // Only the platform administrator, and only with a fresh confirmation.
        mvc.perform(actors.get_(admin, "/api/platform/institutions/{id}/admins", inst)).andExpect(status().isForbidden());
        mvc.perform(actors.post_(platform, null, resetPath, inst, admin.userId))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("auth.step_up_required"));
        actors.stepUp(platform);

        // Never anyone other than an institution administrator, never through another institution.
        mvc.perform(actors.post_(platform, null, resetPath, inst, cbm.userId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("platform.not_institution_admin"));
        mvc.perform(actors.post_(platform, null, resetPath, other, admin.userId)).andExpect(status().isNotFound());

        mvc.perform(actors.post_(platform, null, resetPath, inst, admin.userId)).andExpect(status().isNoContent());

        // The old session dies and the next sign-in sets up a new authenticator.
        mvc.perform(actors.get_(admin, "/api/me")).andExpect(status().isUnauthorized());
        admin.totpSecret = null;
        actors.login(admin);
        assertThat(admin.totpSecret).isNotNull();
        mvc.perform(actors.get_(admin, "/api/me")).andExpect(status().isOk());

        Integer resets = Owner.jdbc().queryForObject("""
                SELECT count(*) FROM audit_event WHERE action = 'MFA_RESET' AND entity_id = ? AND actor_id = ?
                   AND institution_id = ? AND new_value->>'by' = 'PLATFORM_ADMIN'
                """, Integer.class, admin.userId, platform.userId, inst);
        assertThat(resets).isEqualTo(1);
    }
}
