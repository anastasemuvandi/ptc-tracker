package rw.gov.ptc.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import rw.gov.ptc.auth.api.AuthMailer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@TestConfiguration
public class TestBeans {

    /** Captures links that would be emailed, keyed by recipient. */
    public static class CapturingMailer implements AuthMailer {
        public final Map<String, String> lastLink = new ConcurrentHashMap<>();

        @Override
        public void sendInvitation(UUID userId, String email, String fullName, String locale, String link) {
            lastLink.put(email.toLowerCase(), link);
        }

        @Override
        public void sendPasswordReset(UUID userId, String email, String fullName, String locale, String link) {
            lastLink.put(email.toLowerCase(), link);
        }

        @Override
        public void sendSecurityNotice(UUID userId, String email, String fullName, String locale, String subject, String body) {
        }

        public String token(String email) {
            String link = lastLink.get(email.toLowerCase());
            return link.substring(link.indexOf("token=") + 6);
        }
    }

    @Bean
    @Primary
    public CapturingMailer capturingMailer() {
        return new CapturingMailer();
    }
}
