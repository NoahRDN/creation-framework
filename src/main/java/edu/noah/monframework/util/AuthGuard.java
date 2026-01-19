package edu.noah.monframework.util;

import edu.noah.monframework.annotation.Authorized;
import edu.noah.monframework.annotation.Role;
import edu.noah.monframework.web.SessionContext;
import edu.noah.monframework.web.SessionManager;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Performs authorization/role checks based on @Authorized and @Role annotations.
 */
public final class AuthGuard {
    private AuthGuard() {}

    public static boolean isAllowed(Method method, HttpServletRequest req, HttpServletResponse resp) {
        if (method == null) return true;
        Authorized auth = method.getAnnotation(Authorized.class);
        Role role = method.getAnnotation(Role.class);

        // No security annotations -> allow
        if (auth == null && role == null) return true;

        if (req == null || resp == null) return true; // cannot enforce without context

        ServletContext ctx = req.getServletContext();
        AuthConfig cfg = AuthConfig.load(ctx);
        SessionContext session = SessionManager.currentSession(req, resp, false);

        // Authentication
        Object user = session != null ? session.getAttribute(cfg.userKey()) : null;
        if (auth != null && user == null) {
            render(resp, HttpServletResponse.SC_UNAUTHORIZED,
                    auth.message() + " (clé: " + cfg.userKey() + ")");
            return false;
        }

        // Role check
        if (role != null) {
            Object rolesObj = session != null ? session.getAttribute(cfg.roleKey()) : null;
            if (!hasRequiredRole(rolesObj, role.value())) {
                render(resp, HttpServletResponse.SC_FORBIDDEN,
                        role.message() + " (clé: " + cfg.roleKey() + ")");
                return false;
            }
        }

        return true;
    }

    private static boolean hasRequiredRole(Object rolesObj, String[] required) {
        if (required == null || required.length == 0) return true;
        if (rolesObj == null) return false;

        Set<String> wanted = new HashSet<>();
        for (String s : required) {
            if (s != null && !s.isBlank()) wanted.add(s.trim());
        }
        if (wanted.isEmpty()) return true;

        if (rolesObj instanceof String s) {
            return anyMatch(parseRolesString(s), wanted);
        }
        if (rolesObj instanceof Collection<?> col) {
            return anyMatch(col, wanted);
        }
        if (rolesObj.getClass().isArray()) {
            return anyMatch(Arrays.asList((Object[]) rolesObj), wanted);
        }
        return false;
    }

    private static boolean anyMatch(Collection<?> provided, Set<String> wanted) {
        if (provided == null) return false;
        for (Object o : provided) {
            if (o == null) continue;
            String val = o.toString().trim();
            if (wanted.contains(val)) return true;
        }
        return false;
    }

    private static Collection<String> parseRolesString(String value) {
        if (value == null || value.isBlank()) return java.util.List.of();
        String[] split = value.split("[,; ]+");
        java.util.List<String> roles = new java.util.ArrayList<>();
        for (String s : split) {
            if (!s.isBlank()) roles.add(s.trim());
        }
        return roles;
    }

    private static void render(HttpServletResponse resp, int status, String message) {
        try {
            if (resp.isCommitted()) return;
            resp.setStatus(status);
            resp.setContentType("text/plain;charset=UTF-8");
            resp.getWriter().write(message);
        } catch (Exception ignored) {
        }
    }
}
