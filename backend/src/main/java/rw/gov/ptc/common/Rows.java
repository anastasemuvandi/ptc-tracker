package rw.gov.ptc.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JDBC result rows as API JSON: snake_case columns become camelCase keys. */
public final class Rows {

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    private Rows() {
    }

    public static List<Map<String, Object>> camel(List<Map<String, Object>> rows) {
        return rows.stream().map(Rows::camel).toList();
    }

    public static Map<String, Object> camel(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        row.forEach((k, v) -> out.put(camel(k), unwrap(v)));
        return out;
    }

    static String camel(String snake) {
        StringBuilder sb = new StringBuilder(snake.length());
        boolean upper = false;
        for (char c : snake.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else {
                sb.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return sb.toString();
    }

    private static Object unwrap(Object v) {
        if (v instanceof java.sql.Array a) {
            try {
                return List.of((Object[]) a.getArray());
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException(e);
            }
        }
        if (v instanceof java.sql.Timestamp t) {
            return t.toInstant();
        }
        if (v instanceof java.sql.Date d) {
            return d.toLocalDate();
        }
        if (v instanceof org.postgresql.util.PGobject pg && "jsonb".equals(pg.getType())) {
            try {
                return MAPPER.readTree(pg.getValue());
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        }
        return v;
    }
}
