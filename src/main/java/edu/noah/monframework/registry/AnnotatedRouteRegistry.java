package edu.noah.monframework.registry;

import edu.noah.monframework.annotation.InvokeMe;
import edu.noah.monframework.util.AnnotatedMethodExecutor;
import edu.noah.monframework.util.AnnotatedMethodExecutor.InvocationResult;
import edu.noah.monframework.util.ResourceHelper;
import edu.noah.monframework.web.ModelView;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.lang.reflect.Method;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import edu.noah.monframework.util.RoutePathMatcher;
import edu.noah.monframework.util.ControllerScanner;

/**
 * Registry mapping annotated @InvokeMe paths to controller classes,
 * and executing handlers for matching paths.
 */
public class AnnotatedRouteRegistry {

    private static final Map<String, List<Class<?>>> ROUTES = new LinkedHashMap<>();

    // ============================================================
    //  MAIN EXECUTION ENTRYPOINT
    // ============================================================

    /** Executes all handlers registered for the given path. Returns true if at least one handler was executed. */
    public static boolean executePath(String path, HttpServletRequest req, HttpServletResponse resp)
            throws IOException, ServletException {

        List<RouteMatch> matches = findMatchingRoutes(path);
        if (matches.isEmpty()) return false;

        resp.setContentType("text/html;charset=UTF-8");

        try (PrintWriter out = resp.getWriter()) {
            out.println("<html><body>");
            out.println("<h2>URL captured with annotation: " + escapeHtml(path) + "</h2>");

            for (RouteMatch match : matches) {
                for (Class<?> c : match.handlers) {

                    out.println("<div>Executing handlers in class: "
                            + escapeHtml(c.getName())
                            + " (pattern: " + escapeHtml(match.pattern) + ")</div>");

                    try {
                        List<InvocationResult> executed =
                                AnnotatedMethodExecutor.executeAnnotatedMethodsForPath(c, path);

                        if (executed.isEmpty()) {
                            out.println("<div style='color:orange'>No @InvokeMe method found for this path in "
                                    + escapeHtml(c.getName()) + "</div>");
                            continue;
                        }

                        for (InvocationResult result : executed) {
                            Method method = result.method();

                            // Ignored method (unsupported return type)
                            if (!result.invoked()) {
                                out.println("<div style='color:red;margin-left:20px'>Ignored: "
                                        + escapeHtml(c.getName() + "#" + method.getName())
                                        + " (unsupported return type: "
                                        + escapeHtml(method.getReturnType().getSimpleName())
                                        + ")</div>");
                                continue;
                            }

                            // Method executed successfully
                            out.println("<div style='color:green'>Executed: "
                                    + escapeHtml(c.getName() + "#" + method.getName()) + "</div>");

                            // Handle return type
                            Object raw = result.returnValue();
                            Class<?> returnType = method.getReturnType();

                            // ======== ModelView support ========
                            if (ModelView.class.isAssignableFrom(returnType)) {
                                if (handleModelViewResult(raw, req, resp)) {
                                    return true; // forward() has taken over the response
                                }
                            }
                            // ======== String support ========
                            else if (String.class.equals(returnType)) {
                                String rendered = (raw == null) ? "null" : raw.toString();
                                out.println("<div style='margin-left:20px'>Returned content: "
                                        + escapeHtml(rendered) + "</div>");
                            }
                        }

                    } catch (Throwable t) {
                        out.println("<div style='color:red'>Error while executing "
                                + escapeHtml(c.getName()) + ": "
                                + escapeHtml(t.getMessage()) + "</div>");
                    }
                }
            }

            out.println("</body></html>");
        }

        return true;
    }

    // ============================================================
    //  ROUTE MATCHING LOGIC
    // ============================================================

    /** Returns all registered routes matching the requested path (including placeholder routes). */
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

    // ============================================================
    //  MODELVIEW SUPPORT
    // ============================================================

    /**
     * Handles execution of a ModelView return type.
     * Returns true if forwarding was performed, meaning the response is now fully handled.
     */
    private static boolean handleModelViewResult(Object raw,
                                                 HttpServletRequest req,
                                                 HttpServletResponse resp)
            throws ServletException, IOException {

        ModelView mv = (raw instanceof ModelView) ? (ModelView) raw : null;

        if (mv == null) {
            resp.getWriter().println("<div style='color:red;margin-left:20px'>Returned ModelView is null</div>");
            return false;
        }

        ViewResolution view = resolveView(req.getServletContext(), mv.getView());
        if (view == null) {
            resp.getWriter().println("<div style='color:red;margin-left:20px'>ModelView resource not found: "
                    + escapeHtml(String.valueOf(mv.getView())) + "</div>");
            return false;
        }

        RequestDispatcher dispatcher = req.getRequestDispatcher(view.dispatchPath());
        if (dispatcher == null) {
            resp.getWriter().println("<div style='color:red;margin-left:20px'>Unable to obtain a dispatcher for: "
                    + escapeHtml(view.dispatchPath()) + "</div>");
            return false;
        }

        // IMPORTANT: forward()
        dispatcher.forward(req, resp);
        return true;
    }

    private static ViewResolution resolveView(ServletContext context, String requestedView) {
        if (requestedView == null || requestedView.isBlank()) return null;

        String normalized = ResourceHelper.normalizeResourcePath(requestedView);
        if (ResourceHelper.ROOT_PATH.equals(normalized)) return null;

        String resolved = ResourceHelper.resolveResource(context, normalized);
        if (resolved != null) return new ViewResolution(resolved);

        return new ViewResolution(normalized);
    }

    private record ViewResolution(String dispatchPath) {}

    // ============================================================
    //  REGISTRATION
    // ============================================================

    /** Registers a controller class by scanning its @InvokeMe methods. */
    public static void register(Class<?> clazz) {
        for (Method m : clazz.getDeclaredMethods()) {
            InvokeMe inv = m.getAnnotation(InvokeMe.class);
            if (inv == null || inv.path().isEmpty()) continue;

            ROUTES.computeIfAbsent(inv.path(), k -> new ArrayList<>()).add(clazz);
        }
    }

    /** Finds all controller classes annotated with @Controller under a given package. */
    public static List<Class<?>> findControllersInPackage(String basePackage) {
        return ControllerScanner.findControllers(basePackage);
    }

    /** Automatically registers all controllers under a given package. */
    public static void registerControllersInPackage(String basePackage) {
        for (Class<?> c : findControllersInPackage(basePackage)) {
            register(c);
        }
    }

    // ============================================================
    //  UTILS
    // ============================================================

    /** Returns all registered paths sorted alphabetically. */
    public static List<String> getRegisteredPaths() {
        List<String> out = new ArrayList<>(ROUTES.keySet());
        Collections.sort(out);
        return out;
    }

    /** Simple HTML escaping utility. */
    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
