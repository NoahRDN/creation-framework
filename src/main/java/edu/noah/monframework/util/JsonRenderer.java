package edu.noah.monframework.util;

import edu.noah.monframework.annotation.ConvertJson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Very small JSON renderer to avoid external dependencies.
 * Supports primitives, Strings, enums, Maps, Collections, arrays and simple POJOs.
 */
public final class JsonRenderer {
    private JsonRenderer() {}

    /**
     * Renders a JSON response for handlers annotated with {@link ConvertJson}.
     * Returns the JSON string that was written to the response.
     */
    public static String render(Object body,
                                ConvertJson config,
                                HttpServletRequest req,
                                HttpServletResponse resp) {
        if (resp != null) {
            resp.setContentType("application/json;charset=UTF-8");
            if (config != null) {
                resp.setStatus(config.status());
            }
        }

        Object payload = body;
        if (config != null && config.includeMeta()) {
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("data", body);

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("status", config.status());
            if (req != null && req.getRequestURI() != null) {
                meta.put("path", req.getRequestURI());
            }
            meta.put("timestamp", Instant.now().toString());
            if (!config.message().isBlank()) {
                meta.put("message", config.message());
            }
            wrapper.put("meta", meta);
            payload = wrapper;
        }

        String json = toJson(payload);
        if (resp != null) {
            try {
                resp.getWriter().write(json);
                resp.getWriter().flush();
            } catch (Exception ignored) {
            }
        }
        return json;
    }

    /** Basic JSON serialization without external libs. */
    public static String toJson(Object value) {
        if (value == null) return "null";

        if (value instanceof String s) {
            return "\"" + escapeString(s) + "\"";
        }
        if (value instanceof Character c) {
            return "\"" + escapeString(c.toString()) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Enum<?> e) {
            return "\"" + escapeString(e.name()) + "\"";
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String)) continue;
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(escapeString((String) entry.getKey())).append("\":");
                sb.append(toJson(entry.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        if (value instanceof Collection<?> coll) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            boolean first = true;
            for (Object o : coll) {
                if (!first) sb.append(",");
                first = false;
                sb.append(toJson(o));
            }
            sb.append("]");
            return sb.toString();
        }
        if (value.getClass().isArray()) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            int len = Array.getLength(value);
            for (int i = 0; i < len; i++) {
                if (i > 0) sb.append(",");
                sb.append(toJson(Array.get(value, i)));
            }
            sb.append("]");
            return sb.toString();
        }

        // Fallback: reflect public + private fields (non-static)
        Map<String, Object> map = new LinkedHashMap<>();
        for (Field f : value.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) continue;
            if (!f.canAccess(value)) f.setAccessible(true);
            try {
                map.put(f.getName(), f.get(value));
            } catch (IllegalAccessException ignored) {
            }
        }
        return toJson(map);
    }

    private static String escapeString(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : raw.toCharArray()) {
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 32 || c > 126) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
