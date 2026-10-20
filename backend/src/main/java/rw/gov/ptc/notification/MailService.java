package rw.gov.ptc.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Email through the institution's SMTP relay — the guaranteed delivery
 * channel for reminders. When no relay is configured (development) messages
 * are logged instead.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final String from;
    private final boolean enabled;

    public MailService(ObjectProvider<JavaMailSender> sender,
                       @Value("${ptc.mail.from:ptc-tracker@localhost}") String from,
                       @Value("${ptc.mail.enabled:false}") boolean enabled) {
        this.sender = sender;
        this.from = from;
        this.enabled = enabled;
    }

    /** @throws org.springframework.mail.MailException when the relay rejects the message */
    public void send(String to, String subject, String body) {
        if (!enabled || sender.getIfAvailable() == null) {
            log.info("[mail disabled] to={} subject={}\n{}", to, subject, body);
            return;
        }
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(from);
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(body);
        sender.getObject().send(msg);
    }
}
