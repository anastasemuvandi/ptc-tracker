package rw.gov.ptc.config.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import rw.gov.ptc.common.ApiException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuration as resolved at a point in time. Decisions store the
 * underlying JSON and are always re-read through this type, never
 * re-resolved, so a historical record is judged by the rules in force when
 * it was taken.
 *
 * <p>JSON shape: {"resolvedAt": "...", "values": {key: value},
 * "sources": {key: {"scope": "PLATFORM|INSTITUTION", "effectiveFrom": "..."}}}
 */
public record ConfigSnapshot(ObjectNode json) {

    public Instant resolvedAt() {
        return Instant.parse(json.path("resolvedAt").asText());
    }

    public JsonNode get(String key) {
        JsonNode v = json.path("values").get(key);
        if (v == null || v.isNull()) {
            throw new ApiException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "config.missing",
                    "Configuration " + key + " is not defined", java.util.Map.of("key", key));
        }
        return v;
    }

    public boolean has(String key) {
        JsonNode v = json.path("values").get(key);
        return v != null && !v.isNull();
    }

    public int getInt(String key) {
        return get(key).asInt();
    }

    public boolean getBool(String key) {
        return get(key).asBoolean();
    }

    public BigDecimal getDecimal(String key) {
        JsonNode v = get(key);
        return v.isTextual() ? new BigDecimal(v.asText()) : v.decimalValue();
    }

    public List<String> getStrings(String key) {
        List<String> out = new ArrayList<>();
        get(key).forEach(n -> out.add(n.asText()));
        return out;
    }

    /** A {"numerator": n, "denominator": d} rule. */
    public Fraction getFraction(String key) {
        JsonNode v = get(key);
        return new Fraction(v.path("numerator").asInt(), v.path("denominator").asInt());
    }

    public record Fraction(int numerator, int denominator) {

        /** Smallest whole number of members that meets the fraction of base. */
        public int ceilOf(int base) {
            return (int) Math.ceil((double) base * numerator / denominator);
        }

        @Override
        public String toString() {
            return numerator + "/" + denominator;
        }
    }
}
