package rw.gov.ptc.auth.api;

import java.util.UUID;

/** Outbound email used by the auth module; implemented by the notification layer. */
public interface AuthMailer {

    void sendInvitation(UUID userId, String email, String fullName, String locale, String link);

    void sendPasswordReset(UUID userId, String email, String fullName, String locale, String link);

    void sendSecurityNotice(UUID userId, String email, String fullName, String locale, String subject, String body);
}
