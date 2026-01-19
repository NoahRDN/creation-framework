package edu.noah.monframework.util;

import jakarta.servlet.ServletContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

/**
 * Loads auth-related configuration from /WEB-INF/auth-config.xml when present.
 * Defaults: userKey="user", roleKey="roles".
 *
 * Example:
 * <auth>
 *   <session-user-key>loggedUser</session-user-key>
 *   <session-role-key>userRoles</session-role-key>
 * </auth>
 */
public final class AuthConfig {
    private static final String DEFAULT_USER_KEY = "user";
    private static final String DEFAULT_ROLE_KEY = "roles";

    private static volatile AuthConfig cached;

    private final String userKey;
    private final String roleKey;

    private AuthConfig(String userKey, String roleKey) {
        this.userKey = userKey;
        this.roleKey = roleKey;
    }

    public String userKey() {
        return userKey;
    }

    public String roleKey() {
        return roleKey;
    }

    public static AuthConfig load(ServletContext context) {
        AuthConfig existing = cached;
        if (existing != null) return existing;
        synchronized (AuthConfig.class) {
            if (cached != null) return cached;
            cached = readConfig(context);
            return cached;
        }
    }

    private static AuthConfig readConfig(ServletContext context) {
        if (context != null) {
            try (InputStream in = context.getResourceAsStream("/WEB-INF/auth-config.xml")) {
                if (in != null) {
                    Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
                    Element root = doc.getDocumentElement();
                    String user = text(root, "session-user-key", DEFAULT_USER_KEY);
                    String role = text(root, "session-role-key", DEFAULT_ROLE_KEY);
                    return new AuthConfig(user, role);
                }
            } catch (Exception ignored) {
            }
        }
        return new AuthConfig(DEFAULT_USER_KEY, DEFAULT_ROLE_KEY);
    }

    private static String text(Element root, String tag, String fallback) {
        if (root == null) return fallback;
        var nodes = root.getElementsByTagName(tag);
        if (nodes == null || nodes.getLength() == 0) return fallback;
        String value = nodes.item(0).getTextContent();
        if (value == null || value.isBlank()) return fallback;
        return value.trim();
    }
}
