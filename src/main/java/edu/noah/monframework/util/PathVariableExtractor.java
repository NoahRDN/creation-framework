package edu.noah.monframework.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Extracts placeholder values from request paths for annotated patterns such as "/user/{id}".
 */
public final class PathVariableExtractor {
    private PathVariableExtractor() {}

    /** Returns a map of placeholder name -> value for matching segments, or empty if lengths mismatch. */
    public static Map<String, String> extract(String requestPath, String annotatedPath) {
        if (requestPath == null || annotatedPath == null) return Collections.emptyMap();
        String[] reqParts = trimAndSplit(requestPath);
        String[] annParts = trimAndSplit(annotatedPath);
        if (reqParts.length != annParts.length) return Collections.emptyMap();

        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < annParts.length; i++) {
            String ann = annParts[i];
            if (isPlaceholder(ann)) {
                String name = ann.substring(1, ann.length() - 1);
                if (!name.isBlank()) values.put(name, reqParts[i]);
            }
        }
        return values;
    }

    private static String[] trimAndSplit(String path) {
        String normalized = path;
        if (normalized.startsWith("/")) normalized = normalized.substring(1);
        if (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.isEmpty()) return new String[0];
        return normalized.split("/");
    }

    private static boolean isPlaceholder(String segment) {
        return segment != null && segment.startsWith("{") && segment.endsWith("}") && segment.length() > 2;
    }
}
