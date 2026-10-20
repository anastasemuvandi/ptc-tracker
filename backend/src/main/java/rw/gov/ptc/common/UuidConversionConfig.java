package rw.gov.ptc.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

import java.beans.PropertyEditorSupport;

import java.io.IOException;
import java.util.UUID;

/**
 * Strict UUID handling on every inbound path: path variables, query
 * parameters and JSON bodies only accept the canonical 8-4-4-4-12 form.
 * Anything else is a 400 and never reaches the database.
 */
@Configuration
@ControllerAdvice
public class UuidConversionConfig {

    /** Custom editors take precedence over the default lenient String→UUID converter. */
    @InitBinder
    public void strictUuids(WebDataBinder binder) {
        binder.registerCustomEditor(UUID.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(text == null || text.isEmpty() ? null : Ids.parse(text));
            }
        });
    }

    @Bean
    public SimpleModule strictUuidModule() {
        SimpleModule module = new SimpleModule("strict-uuid");
        module.addDeserializer(UUID.class, new JsonDeserializer<>() {
            @Override
            public UUID deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
                String text = p.getValueAsString();
                if (!Ids.isCanonical(text)) {
                    throw InvalidFormatException.from(p, "Malformed UUID", text, UUID.class);
                }
                return UUID.fromString(text);
            }
        });
        return module;
    }
}
