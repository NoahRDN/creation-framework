package edu.noah.monframework.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central session registry that stores attributes in memory (ConcurrentHashMap)
 * and keeps track of session ids via a cookie. This avoids {@code HttpSession}
 * while preserving per-client state.
 */
public final class SessionManager {
    private SessionManager() {}

    private static final String COOKIE_NAME = "MONFRAMEWORK_SESSION";
    private static final String REQUEST_ATTRIBUTE = SessionManager.class.getName() + ".SESSION";
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Map<String, SessionState> SESSIONS = new ConcurrentHashMap<>();

    /** Retrieve the current session or create one if requested. */
    public static SessionContext currentSession(HttpServletRequest req,
                                                HttpServletResponse resp,
                                                boolean create) {
        SessionContext cached = lookupRequestSession(req);
        if (cached != null && !cached.isInvalidated()) {
            touch(cached.getId());
            return cached;
        }

        String sessionId = readCookie(req);
        SessionState state = findState(sessionId);
        boolean isNew = false;

        if (state == null) {
            if (!create) return null;
            sessionId = generateId();
            state = new SessionState();
            SESSIONS.put(sessionId, state);
            isNew = true;
        }

        touch(sessionId);
        writeCookie(resp, req, sessionId, false);

        SessionContext context = new SessionContext(sessionId, isNew);
        if (req != null) req.setAttribute(REQUEST_ATTRIBUTE, context);
        return context;
    }

    /** Invalidate and remove a session entirely. */
    public static void invalidate(String sessionId) {
        if (sessionId == null) return;
        SESSIONS.remove(sessionId);
    }

    /** Check whether a session id is currently active. */
    public static boolean isActive(String sessionId) {
        return findState(sessionId) != null;
    }

    /** Read a single attribute from the session map. */
    static Object readAttribute(String sessionId, String name) {
        SessionState state = findState(sessionId);
        if (state == null) return null;
        touch(state);
        return state.attributes.get(name);
    }

    /** Write a single attribute to the session map. */
    static void writeAttribute(String sessionId, String name, Object value) {
        SessionState state = findState(sessionId);
        if (state == null) return;
        touch(state);
        if (name == null || name.isBlank()) return;
        if (value == null) {
            state.attributes.remove(name);
        } else {
            state.attributes.put(name, value);
        }
    }

    /** Remove and return a single attribute from the session map. */
    static Object removeAttribute(String sessionId, String name) {
        SessionState state = findState(sessionId);
        if (state == null) return null;
        touch(state);
        return state.attributes.remove(name);
    }

    /** Returns a copy of all attributes for the session. */
    static Map<String, Object> snapshot(String sessionId) {
        SessionState state = findState(sessionId);
        if (state == null) return Collections.emptyMap();
        touch(state);
        return new java.util.LinkedHashMap<>(state.attributes);
    }

    // ----------------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------------
    private static SessionContext lookupRequestSession(HttpServletRequest req) {
        if (req == null) return null;
        Object cached = req.getAttribute(REQUEST_ATTRIBUTE);
        if (cached instanceof SessionContext ctx) {
            return ctx;
        }
        return null;
    }

    private static SessionState findState(String id) {
        if (id == null || id.isBlank()) return null;
        SessionState state = SESSIONS.get(id);
        if (state != null && state.isExpired()) {
            SESSIONS.remove(id);
            state = null;
        }
        return state;
    }

    private static void touch(String id) {
        SessionState state = SESSIONS.get(id);
        if (state != null) touch(state);
    }

    private static void touch(SessionState state) {
        state.expiresAt = System.currentTimeMillis() + TTL.toMillis();
    }

    private static String generateId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String readCookie(HttpServletRequest req) {
        if (req == null || req.getCookies() == null) return null;
        for (Cookie cookie : req.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static void writeCookie(HttpServletResponse resp,
                                    HttpServletRequest req,
                                    String value,
                                    boolean expire) {
        if (resp == null || value == null) return;
        Cookie cookie = new Cookie(COOKIE_NAME, expire ? "" : value);
        cookie.setHttpOnly(true);
        cookie.setPath(resolveCookiePath(req));
        cookie.setMaxAge(expire ? 0 : (int) TTL.toSeconds());
        resp.addCookie(cookie);
    }

    private static String resolveCookiePath(HttpServletRequest req) {
        if (req == null) return "/";
        String ctx = Objects.toString(req.getContextPath(), "").trim();
        return ctx.isEmpty() ? "/" : ctx;
    }

    private static final class SessionState {
        final Map<String, Object> attributes = new ConcurrentHashMap<>();
        volatile long expiresAt = System.currentTimeMillis() + TTL.toMillis();

        boolean isExpired() {
            return expiresAt < System.currentTimeMillis();
        }
    }
}
