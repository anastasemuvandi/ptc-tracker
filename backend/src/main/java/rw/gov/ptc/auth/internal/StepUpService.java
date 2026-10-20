package rw.gov.ptc.auth.internal;

import org.springframework.stereotype.Service;
import rw.gov.ptc.auth.api.AuthPrincipal;
import rw.gov.ptc.auth.api.StepUpVerifier;
import rw.gov.ptc.common.ApiException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
public class StepUpService implements StepUpVerifier {

    private final AuthStore store;

    public StepUpService(AuthStore store) {
        this.store = store;
    }

    @Override
    public Instant requireRecent(AuthPrincipal principal, String act) {
        long minutes = store.config(principal.institutionId(), "stepUp.windowMinutes")
                .map(n -> n.asLong(5)).orElse(5L);
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(minutes));
        return store.lastStepUp(principal.userId(), principal.sessionId())
                .filter(t -> t.isAfter(cutoff))
                .orElseThrow(() -> {
                    store.audit(principal.institutionId(), principal.userId(), "STEP_UP_CHALLENGED",
                            principal.sessionId(), Map.of("act", act));
                    return new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "auth.step_up_required",
                            "Confirm your identity to continue", Map.of("act", act, "windowMinutes", minutes));
                });
    }
}
