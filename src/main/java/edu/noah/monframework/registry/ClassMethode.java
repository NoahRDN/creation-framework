package edu.noah.monframework.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Represents a mapping between an annotated URL and its handler classes.
 */
public class ClassMethode {
    private final String url;
    private final List<Class<?>> handlers = new ArrayList<>();
    private final Set<String> allowedHttpMethods = new LinkedHashSet<>();

    public ClassMethode(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public void addHandler(Class<?> handler) {
        if (handler != null) {
            handlers.add(handler);
        }
    }

    public void addHttpMethod(String method) {
        if (method == null || method.isBlank()) return;
        allowedHttpMethods.add(method.toUpperCase(Locale.ROOT));
    }

    /** If none registered, consider all methods allowed. */
    public boolean isMethodAllowed(String method) {
        if (method == null) return allowedHttpMethods.isEmpty();
        if (allowedHttpMethods.isEmpty()) return true;
        return allowedHttpMethods.contains(method.toUpperCase(Locale.ROOT));
    }

    public Set<String> getAllowedHttpMethods() {
        return Collections.unmodifiableSet(allowedHttpMethods);
    }

    public List<Class<?>> getHandlers() {
        return Collections.unmodifiableList(handlers);
    }
}
