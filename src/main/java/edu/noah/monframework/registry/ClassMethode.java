package edu.noah.monframework.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Represents a mapping between an annotated URL and its handler classes.
 */
public class ClassMethode {
    private final String url;
    private final List<Class<?>> handlers = new ArrayList<>();

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

    public List<Class<?>> getHandlers() {
        return Collections.unmodifiableList(handlers);
    }
}
