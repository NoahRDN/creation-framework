package edu.noah.monframework.registry;

import edu.noah.monframework.annotation.InvokeMe;
import edu.noah.monframework.util.AnnotatedMethodExecutor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple registry that maps paths (strings) to classes that declare @InvokeMe(path=...)
 * and exposes execution by path.
 */
public class AnnotatedRouteRegistry {
    private static final Map<String, List<Class<?>>> ROUTES = new HashMap<>();

    /** Register a class: any methods annotated with @InvokeMe(path=...) will be associated. */
    public static void register(Class<?> clazz) {
        for (Method m : clazz.getDeclaredMethods()) {
            InvokeMe a = m.getAnnotation(InvokeMe.class);
            if (a != null) {
                String path = a.path();
                if (path == null || path.isEmpty()) continue;
                ROUTES.computeIfAbsent(path, k -> new ArrayList<>()).add(clazz);
            }
        }
    }

    /** Execute handlers registered for the exact path. Returns true if any handler was invoked. */
    public static boolean executePath(String path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        List<Class<?>> handlers = ROUTES.get(path);
        if (handlers == null || handlers.isEmpty()) return false;

        resp.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.println("<html><body>");
            out.println("<h2>Handlers for path: " + path + "</h2>");
            for (Class<?> c : handlers) {
                out.println("<div>Executing handlers in class: " + c.getName() + "</div>");
                try {
                    AnnotatedMethodExecutor.executeAnnotatedMethodsForPath(c, path);
                    out.println("<div style=\"color:green\">Executed: " + c.getName() + "</div>");
                } catch (Throwable t) {
                    out.println("<div style=\"color:red\">Error executing " + c.getName() + ": " + t.getMessage() + "</div>");
                }
            }
            out.println("</body></html>");
        }

        return true;
    }

    public static List<String> getRegisteredPaths() {
        List<String> out = new ArrayList<>(ROUTES.keySet());
        Collections.sort(out);
        return out;
    }
}
