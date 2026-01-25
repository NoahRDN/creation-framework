package edu.noah.monframework.web;

import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

/**
 * Represents the connected user's state stored in the session as a single object.
 * <p>
 * The framework never instantiates implementations: application developers provide
 * their own concrete class and store it in the {@link SessionContext}.
 */
public interface UserSession extends Serializable {

    /** Login/identifier of the connected user (may be {@code null} when disconnected). */
    String getLogin();

    /** Roles of the connected user (never {@code null}). */
    default Collection<String> getRoles() {
        return Collections.emptyList();
    }

    /** True when the user is considered connected. */
    default boolean isConnected() {
        String login = getLogin();
        return login != null && !login.isBlank();
    }

    /** Convenient role check (exact match after trimming). */
    default boolean hasRole(String role) {
        if (role == null || role.isBlank()) return false;
        String wanted = role.trim();
        for (String r : getRoles()) {
            if (Objects.equals(wanted, r != null ? r.trim() : null)) return true;
        }
        return false;
    }
}
