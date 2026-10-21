package rw.gov.ptc.notification.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import rw.gov.ptc.notification.service.MailService;

@Service
public class MailServiceImpl implements MailService {

    private static final Logger log = LoggerFactory.getLogger(MailServiceImpl.class);

    private final ObjectProvider<JavaMailSender> sender;
    private final String from;
    private final boolean enabled;

    public MailServiceImpl(ObjectProvider<JavaMailSender> sender,
                       @Value("${ptc.mail.from:ptc-tracker@localhost}") String from,
                       @Value("${ptc.mail.enabled:false}") boolean enabled) {
        this.sender = sender;
        this.from = from;
        this.enabled = enabled;
    }

    @Override
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
