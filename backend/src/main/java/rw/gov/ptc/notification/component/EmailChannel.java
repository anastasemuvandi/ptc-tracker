package rw.gov.ptc.notification.component;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import rw.gov.ptc.notification.dto.Recipient;
import rw.gov.ptc.notification.entity.Notification;
import rw.gov.ptc.notification.service.MailService;

/** Guaranteed channel: every notification is also emailed. */
@Component
public class EmailChannel implements NotificationChannel {

    private final MailService mail;
    private final String baseUrl;

    public EmailChannel(MailService mail, @Value("${ptc.auth.public-base-url:http://localhost:5173}") String baseUrl) {
        this.mail = mail;
        this.baseUrl = baseUrl;
    }

    @Override
    public String name() {
        return "email";
    }

    @Override
    public boolean guaranteed() {
        return true;
    }

    @Override
    public boolean deliver(Notification n, Recipient r) {
        mail.send(r.email(), "PTC Tracker — " + n.title,
                "Hello " + r.fullName() + ",\n\n" + n.body + "\n\n" + baseUrl + n.link
                        + "\n\n— PTC Tracker (internal working tool; the system of record is Umucyo)");
        return true;
    }
}
