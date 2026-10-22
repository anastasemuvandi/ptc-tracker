package rw.gov.ptc.notification.component;

import org.springframework.stereotype.Component;
import rw.gov.ptc.auth.api.AuthMailer;
import rw.gov.ptc.notification.service.MailService;

import java.util.Map;
import java.util.UUID;

/** Auth emails (invitation, reset, security notices) in the user's language. */
@Component
public class SmtpAuthMailer implements AuthMailer {

    private static final Map<String, String[]> INVITE = Map.of(
            "en", new String[]{"You are invited to PTC Tracker",
                    "Hello %s,\n\nYou have been invited to PTC Tracker, the tender committee progress tracker of your institution.\n\nYour username is this email address: %s\n\nSet your password here (the link is valid for 72 hours and can be used once):\n%s\n"},
            "fr", new String[]{"Invitation à PTC Tracker",
                    "Bonjour %s,\n\nVous êtes invité(e) à PTC Tracker, l'outil de suivi du comité des marchés publics de votre institution.\n\nVotre nom d'utilisateur est cette adresse e-mail : %s\n\nDéfinissez votre mot de passe ici (lien valable 72 heures, utilisable une seule fois) :\n%s\n"},
            "rw", new String[]{"Mutumiwe muri PTC Tracker",
                    "Muraho %s,\n\nMutumiwe gukoresha PTC Tracker, uburyo bwo gukurikirana imirimo ya komite y'amasoko ya Leta y'ikigo cyanyu.\n\nIzina ryo kwinjira ni iyi imeri: %s\n\nShyiraho ijambobanga ryanyu hano (iyi link imara amasaha 72 kandi ikoreshwa rimwe):\n%s\n"});

    private static final Map<String, String[]> RESET = Map.of(
            "en", new String[]{"Reset your PTC Tracker password",
                    "Hello %s,\n\nUse this link within 30 minutes to choose a new password:\n%s\n\nIf you did not ask for this, ignore this message."},
            "fr", new String[]{"Réinitialisation du mot de passe PTC Tracker",
                    "Bonjour %s,\n\nUtilisez ce lien dans les 30 minutes pour choisir un nouveau mot de passe :\n%s\n\nSi vous n'êtes pas à l'origine de cette demande, ignorez ce message."},
            "rw", new String[]{"Guhindura ijambobanga rya PTC Tracker",
                    "Muraho %s,\n\nKoresha iyi link mu minota 30 uhitemo ijambobanga rishya:\n%s\n\nNiba atari mwe mwabisabye, mwirengagize ubu butumwa."});

    private final MailService mail;

    public SmtpAuthMailer(MailService mail) {
        this.mail = mail;
    }

    @Override
    public void sendInvitation(UUID userId, String email, String fullName, String locale, String link) {
        String[] t = INVITE.getOrDefault(locale, INVITE.get("en"));
        mail.send(email, t[0], t[1].formatted(fullName, email, link));
    }

    @Override
    public void sendPasswordReset(UUID userId, String email, String fullName, String locale, String link) {
        String[] t = RESET.getOrDefault(locale, RESET.get("en"));
        mail.send(email, t[0], t[1].formatted(fullName, link));
    }

    @Override
    public void sendSecurityNotice(UUID userId, String email, String fullName, String locale, String subject, String body) {
        mail.send(email, subject, body);
    }
}
