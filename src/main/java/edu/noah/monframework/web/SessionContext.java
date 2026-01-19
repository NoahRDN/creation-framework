package edu.noah.monframework.web;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight session wrapper managed by {@link SessionManager} without relying
 * on {@code HttpSession}. Controllers can create/read/update/remove attributes
 * and invalidate the session entirely.
 */
public final class SessionContext {
    private final String id;
    private final boolean isNew;
    private volatile boolean invalidated;

    SessionContext(String id, boolean isNew) {
        this.id = id;
        this.isNew = isNew;
    }

    /** Unique identifier of this session. */
    public String getId() {
        return id;
    }

    /** Indicates whether the session was just created during the current request. */
    public boolean isNew() {
        return isNew;
    }

    /** True when the session was invalidated or has expired. */
    public boolean isInvalidated() {
        return invalidated || !SessionManager.isActive(id);
    }

    /** Read an attribute from the session (returns {@code null} if missing). */
    public Object getAttribute(String name) {
        if (isInvalidated()) return null;
        return SessionManager.readAttribute(id, name);
    }

    /** Read and cast an attribute from the session. */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String name, Class<T> targetType) {
        Object raw = getAttribute(name);
        if (raw == null || targetType == null) return null;
        return targetType.isInstance(raw) ? (T) raw : null;
    }

    /** Create or replace a session attribute. Passing {@code null} removes the key. */
    public SessionContext setAttribute(String name, Object value) {
        if (isInvalidated()) return this;
        SessionManager.writeAttribute(id, name, value);
        return this;
    }

    /** Remove a session attribute and return its previous value. */
    public Object removeAttribute(String name) {
        if (isInvalidated()) return null;
        return SessionManager.removeAttribute(id, name);
    }

    /** Returns a snapshot of all session attributes. */
    public Map<String, Object> attributes() {
        if (isInvalidated()) return Collections.emptyMap();
        return Collections.unmodifiableMap(new LinkedHashMap<>(SessionManager.snapshot(id)));
    }

    /** Clear the session and drop its cookie. */
    public void invalidate() {
        if (invalidated) return;
        invalidated = true;
        SessionManager.invalidate(id);
    }
}
