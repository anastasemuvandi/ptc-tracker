package rw.gov.ptc.auth.internal;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-client-address attempt budget per minute for unauthenticated auth
 * endpoints, complementing per-account lockout (which an attacker spraying
 * many accounts would not trigger).
 */
@Component
public class LoginThrottle {

    private final int perMinute;
    private final ConcurrentHashMap<String, AtomicInteger> window = new ConcurrentHashMap<>();

    public LoginThrottle(AuthProperties props) {
        this.perMinute = props.loginAttemptsPerMinutePerIp();
    }

    public boolean tryAcquire(String clientIp) {
        String key = clientIp == null ? "unknown" : clientIp;
        return window.computeIfAbsent(key, k -> new AtomicInteger()).incrementAndGet() <= perMinute;
    }

    @Scheduled(fixedRate = 60_000)
    void reset() {
        window.clear();
    }
}
