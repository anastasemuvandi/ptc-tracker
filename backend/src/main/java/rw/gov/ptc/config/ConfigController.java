package rw.gov.ptc.config;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rw.gov.ptc.auth.api.InstitutionRole;
import rw.gov.ptc.common.ApiException;
import rw.gov.ptc.security.Access;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    public record RuleRequest(String key, JsonNode value, Instant effectiveFrom, String reason,
                              String legalReference, String scope) {
    }

    private final ConfigService config;
    private final Access access;

    public ConfigController(ConfigService config, Access access) {
        this.config = config;
        this.access = access;
    }

    /** Resolved configuration (now, or at a past instant for audit). */
    @GetMapping
    public JsonNode resolved(@RequestParam(required = false) Instant at) {
        return config.snapshot(at == null ? Instant.now() : at).json();
    }

    @GetMapping("/{key}/history")
    public List<Map<String, Object>> history(@PathVariable String key) {
        return config.history(key);
    }

    @PostMapping
    public Map<String, Object> add(@RequestBody RuleRequest req) {
        boolean platform = "PLATFORM".equals(req.scope());
        if (platform) {
            access.requireAny(InstitutionRole.PLATFORM_ADMIN);
        } else {
            access.requireAny(InstitutionRole.INSTITUTION_ADMIN);
        }
        if (req.key() == null || req.key().isBlank()) {
            throw ApiException.badRequest("config.unknown_key", "Key is required");
        }
        UUID id = config.addRule(new ConfigService.NewRule(req.key(), req.value(), req.effectiveFrom(),
                req.reason(), req.legalReference(), platform));
        return Map.of("id", id);
    }
}
