package edu.noah.monframework.util;

import jakarta.servlet.ServletContext;

import java.net.MalformedURLException;

/**
 * Utility methods shared between the filter/registry to resolve static resources inside the
 * web application (css, js, html, jsp...).
 */
public final class ResourceHelper {

    public static final String ROOT_PATH = "/";

    private ResourceHelper() {}

    /** Ensure servlet paths always start with a single leading slash. */
    public static String normalizeResourcePath(String rawPath) {
        if (rawPath == null) return ROOT_PATH;
        String normalized = rawPath.trim();
        if (normalized.isEmpty()) return ROOT_PATH;
        if (!normalized.startsWith(ROOT_PATH)) {
            normalized = ROOT_PATH + normalized;
        }
        // Collapse duplicated slashes to avoid //styles.css
        while (normalized.contains("//")) {
            normalized = normalized.replace("//", ROOT_PATH);
        }
        return normalized;
    }

    /**
     * Resolve a path to a concrete resource. Supports directories that contain
     * index.html / index.jsp to mimic the logic from {@code MyFilter}.
     *
     * @return dispatchable servlet path or null when nothing matches.
     */
    public static String resolveResource(ServletContext context, String rawPath) {
        if (context == null) return null;
        String normalized = normalizeResourcePath(rawPath);
        if (ROOT_PATH.equals(normalized)) return null;

        try {
            // Try exact match first (jsp, html, css, js, etc.).
            if (!normalized.endsWith(ROOT_PATH) && context.getResource(normalized) != null) {
                return normalized;
            }

            // Directory handling -> look for index.* similar to filter logic.
            String dirPath = normalized.endsWith(ROOT_PATH) ? normalized : normalized + ROOT_PATH;
            if (context.getResource(dirPath + "index.html") != null) {
                return dirPath + "index.html";
            }
            if (context.getResource(dirPath + "index.jsp") != null) {
                return dirPath + "index.jsp";
            }
        } catch (MalformedURLException e) {
            return null;
        }

        return null;
    }

    /**
     * Determines if the provided path points to a static resource (or directory with index file).
     */
    public static boolean isStaticResource(ServletContext context, String rawPath) {
        return resolveResource(context, rawPath) != null;
    }
}
