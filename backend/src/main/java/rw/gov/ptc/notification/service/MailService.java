package rw.gov.ptc.notification.service;

/**
 * Email through the institution's SMTP relay — the guaranteed delivery
 * channel for reminders. When no relay is configured (development) messages
 * are logged instead.
 */
public interface MailService {

    /** @throws org.springframework.mail.MailException when the relay rejects the message */
    void send(String to, String subject, String body);
}
