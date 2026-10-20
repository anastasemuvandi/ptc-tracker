package rw.gov.ptc.auth.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import rw.gov.ptc.auth.api.IdentityAdmin;
import rw.gov.ptc.auth.api.InstitutionRole;

import java.util.Set;

/**
 * Onboarding is invitation-only, so a fresh installation needs one way in:
 * when no platform administrator exists and PTC_BOOTSTRAP_ADMIN_EMAIL is
 * set, that address is invited as platform administrator at startup.
 */
@Component
public class PlatformBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformBootstrap.class);

    private final AuthStore store;
    private final IdentityAdmin identity;
    private final String email;

    public PlatformBootstrap(AuthStore store, IdentityAdmin identity,
                             @Value("${PTC_BOOTSTRAP_ADMIN_EMAIL:}") String email) {
        this.store = store;
        this.identity = identity;
        this.email = email;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email == null || email.isBlank() || store.platformAdminExists()) {
            return;
        }
        if (store.userByEmail(email).isPresent()) {
            log.warn("Bootstrap email {} already belongs to a user without platform administration; not changed", email);
            return;
        }
        identity.invite(new IdentityAdmin.Invitation(null, email.trim(), "Platform Administrator", null, null,
                false, "en", Set.of(InstitutionRole.PLATFORM_ADMIN)), null);
        log.info("Invited {} as the first platform administrator", email);
    }
}
