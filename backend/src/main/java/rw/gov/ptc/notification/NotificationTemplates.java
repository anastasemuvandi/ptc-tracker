package rw.gov.ptc.notification;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Notification texts per locale (en, fr, rw) from notifications*.properties.
 * Keys are &lt;kind&gt;.title and &lt;kind&gt;.body with positional {0}, {1}…
 * placeholders.
 */
@Component
public class NotificationTemplates {

    public record Rendered(String title, String body) {
    }

    private static final ResourceBundle.Control NO_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);

    public Rendered render(String kind, String locale, List<String> args) {
        ResourceBundle bundle = ResourceBundle.getBundle("notifications",
                Locale.forLanguageTag(locale == null ? "en" : locale), NO_FALLBACK);
        return new Rendered(format(text(bundle, kind + ".title"), args), format(text(bundle, kind + ".body"), args));
    }

    private static String text(ResourceBundle bundle, String key) {
        try {
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    static String format(String template, List<String> args) {
        String out = template;
        for (int i = 0; i < args.size(); i++) {
            out = out.replace("{" + i + "}", args.get(i) == null ? "" : args.get(i));
        }
        return out;
    }
}
