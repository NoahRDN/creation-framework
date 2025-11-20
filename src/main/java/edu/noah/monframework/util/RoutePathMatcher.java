package edu.noah.monframework.util;

import java.util.regex.Pattern;

/**
 * Utility to check if a request path matches an @InvokeMe path pattern.
 * Supports placeholders like "/hello/{id}" where each placeholder matches a single path segment.
 */
public final class RoutePathMatcher {
    private RoutePathMatcher() {}

    /**
     * Returns true if the requestPath matches the annotatedPath. Braced segments in
     * the annotatedPath (e.g. {id}) are treated as wildcards for a single path segment.
     */
    public static boolean matches(String requestPath, String annotatedPath) {
        if (requestPath == null || annotatedPath == null || annotatedPath.isEmpty()) return false;
        if (requestPath.equals(annotatedPath)) return true;
        String regex = toRegex(annotatedPath);
        return Pattern.matches(regex, requestPath);
    }

    /** Convert a placeholder-based annotated path into a safe regular expression. */
    public static String toRegex(String annotatedPath) {
        StringBuilder regex = new StringBuilder();
        regex.append("^");
        for (int i = 0; i < annotatedPath.length();) {
            char ch = annotatedPath.charAt(i);
            if (ch == '{') {
                int closing = annotatedPath.indexOf('}', i + 1);
                if (closing > i) {
                    regex.append("[^/]+"); // wildcard for one segment
                    i = closing + 1;
                    continue;
                }
            }
            if ("\\.[]{}()+-^$|*?".indexOf(ch) >= 0 || ch == '\\') regex.append("\\");
            regex.append(ch);
            i++;
        }
        regex.append("$");
        return regex.toString();
    }
}
