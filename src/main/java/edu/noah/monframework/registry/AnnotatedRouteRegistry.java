package edu.noah.monframework.registry;

import edu.noah.monframework.annotation.InvokeMe;
import edu.noah.monframework.util.AnnotatedMethodExecutor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Replaced ClassGraph usage with in-project scanner
import java.lang.reflect.Method;
import java.util.List;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import edu.noah.monframework.util.RoutePathMatcher;
import edu.noah.monframework.util.ControllerScanner;

/**
 * Simple registry that maps paths (strings) to classes that declare @InvokeMe(path=...)
 * and exposes execution by path.
 */
public class AnnotatedRouteRegistry {
    private static final Map<String, List<Class<?>>> ROUTES = new LinkedHashMap<>();
    
    /** Execute handlers registered for a matching path (supports placeholders). Returns true if any handler was invoked. */
    public static boolean executePath(String path, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        List<RouteMatch> matches = findMatchingRoutes(path);
        if (matches.isEmpty()) return false;

        List<String> htmlChunks = new ArrayList<>();
        htmlChunks.add("<h2>URL Capturé avec annotation : " + escapeHtml(path) + "</h2>");
        for (Class<?> c : handlers) {
            htmlChunks.add("<div>Exécution des gestionnaires dans la classe : " + c.getName() + "</div>");
            try {
                List<InvocationResult> executed = AnnotatedMethodExecutor.executeAnnotatedMethodsForPath(c, path);
                if (executed.isEmpty()) {
                    htmlChunks.add("<div style=\"color:orange\">Aucune méthode @InvokeMe pour ce chemin dans " + c.getName() + "</div>");
                } else {
                    for (InvocationResult result : executed) {
                        Method method = result.method();
                        htmlChunks.add("<div style=\"color:green\">Exécuté : " + c.getName() + "#" + method.getName() + "</div>");
                        Class<?> returnType = method.getReturnType();
                        Object raw = result.returnValue();
                        if (ModelView.class.isAssignableFrom(returnType)) {
                            if (handleModelViewResult(raw, req, resp, htmlChunks)) {
                                return true;
                            }
                        } else if (String.class.equals(returnType)) {
                            String rendered = (raw == null) ? "null" : raw.toString();
                            htmlChunks.add("<div style=\"margin-left:20px\">Contenu retourné : " + escapeHtml(rendered) + "</div>");
                        }
                    }
                }
            } catch (Throwable t) {
                htmlChunks.add("<div style=\"color:red\">Erreur lors de l'exécution de " + c.getName() + ": " + escapeHtml(t.getMessage()) + "</div>");
            }
        }

        resp.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.println("<html><body>");
            out.println("<h2>URL Capturé avec annotation : " + path + "</h2>");
            for (RouteMatch match : matches) {
                for (Class<?> c : match.handlers) {
                    out.println("<div>Exécution des gestionnaires dans la classe : " + c.getName() + " (pattern : " + match.pattern + ")</div>");
                    try {
                        List<Method> executed = AnnotatedMethodExecutor.executeAnnotatedMethodsForPath(c, path);
                        if (executed.isEmpty()) {
                            out.println("<div style=\"color:orange\">Aucune méthode @InvokeMe pour ce chemin dans " + c.getName() + "</div>");
                        } else {
                            for (Method method : executed) {
                                out.println("<div style=\"color:green\">Exécuté : " + c.getName() + "#" + method.getName() + "</div>");
                            }
                        }
                    } catch (Throwable t) {
                        out.println("<div style=\"color:red\">Erreur lors de l'exécution de " + c.getName() + ": " + t.getMessage() + "</div>");
                    }
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

    /** Register a single class by scanning its @InvokeMe methods. */
    public static void register(Class<?> clazz) {
        for (Method m : clazz.getDeclaredMethods()) {
            InvokeMe inv = m.getAnnotation(InvokeMe.class);
            if (inv != null && !inv.path().isEmpty()) {
                ROUTES.computeIfAbsent(inv.path(), k -> new ArrayList<>()).add(clazz);
            }
        }
    }

    /** Find controller classes annotated with @Controller under the given package. */
    public static List<Class<?>> findControllersInPackage(String basePackage) {
        return ControllerScanner.findControllers(basePackage);
    }

    /** Auto-register all controller classes under the package by scanning for @Controller.
     * For each controller, registers any @InvokeMe methods it declares.
     */
    public static void registerControllersInPackage(String basePackage) {
        List<Class<?>> controllers = findControllersInPackage(basePackage);
        for (Class<?> c : controllers) {
            register(c);
        }
    }

    private static List<RouteMatch> findMatchingRoutes(String requestPath) {
        List<RouteMatch> matches = new ArrayList<>();
        for (Map.Entry<String, List<Class<?>>> entry : ROUTES.entrySet()) {
            if (RoutePathMatcher.matches(requestPath, entry.getKey())) {
                matches.add(new RouteMatch(entry.getKey(), entry.getValue()));
            }
        }
        return matches;
    }

    private static final class RouteMatch {
        final String pattern;
        final List<Class<?>> handlers;

        RouteMatch(String pattern, List<Class<?>> handlers) {
            this.pattern = pattern;
            this.handlers = handlers;
        }
    }
}
