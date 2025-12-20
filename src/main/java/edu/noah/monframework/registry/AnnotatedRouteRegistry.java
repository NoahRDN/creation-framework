package edu.noah.monframework.registry;

import edu.noah.monframework.annotation.InvokeMe;
import edu.noah.monframework.annotation.GetMapping;
import edu.noah.monframework.annotation.PostMapping;
import edu.noah.monframework.annotation.ConvertJson;
import edu.noah.monframework.util.AnnotatedMethodExecutor;
import edu.noah.monframework.util.AnnotatedMethodExecutor.InvocationResult;
import edu.noah.monframework.util.JsonRenderer;
import edu.noah.monframework.util.ResourceHelper;
import edu.noah.monframework.web.ModelView;
import edu.noah.monframework.web.JsonResponse;
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

    private static final Map<String, ClassMethode> ROUTES = new LinkedHashMap<>();

    // ============================================================
    //  MAIN EXECUTION ENTRYPOINT
    // ============================================================

    /** Executes all handlers registered for the given path. Returns true if at least one handler was executed. */
    public static boolean executePath(String path, HttpServletRequest req, HttpServletResponse resp)
            throws IOException, ServletException {

        List<RouteMatch> matches = findMatchingRoutes(path);
        if (matches.isEmpty()) return false;
        String httpMethod = (req != null) ? req.getMethod() : null;

        List<ExecutionBatch> batches = new ArrayList<>();

        boolean methodRejected = false;
        boolean executedAny = false;
        // Phase 1: execute handlers and short-circuit if a @ConvertJson handler is found.
        for (RouteMatch match : matches) {
            if (!match.classMethode.isMethodAllowed(httpMethod)) {
                methodRejected = true;
                continue;
            }
            for (Class<?> c : match.handlers) {
                List<InvocationResult> executed =
                        AnnotatedMethodExecutor.executeAnnotatedMethodsForPath(c, path, req, resp);
                batches.add(new ExecutionBatch(match.pattern, c, executed));

                for (InvocationResult result : executed) {
                    Method method = result.method();
                    if (result.invoked() && method.isAnnotationPresent(ConvertJson.class)) {
                        ConvertJson cfg = method.getAnnotation(ConvertJson.class);
                        if (result.returnValue() instanceof ModelView) {
                            JsonResponse err = JsonResponse.of(
                                    500,
                                    Map.of("error", "ConvertJson cannot be used with ModelView return type"),
                                    Map.of("reason", "Unsupported combination"));
                            JsonRenderer.render(err, null, req, resp);
                            return true;
                        }
                        JsonRenderer.render(result.returnValue(), cfg, req, resp);
                        return true;
                    }
                    if (result.invoked()) executedAny = true;
                }
            }
        }

        // No JSON handler: render HTML diagnostics or forward ModelView/String like before.
        resp.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.println("<html><body>");
            out.println("<h2>URL captured with annotation: " + escapeHtml(path) + "</h2>");
            if (httpMethod != null) {
                out.println("<div>HTTP Method: " + escapeHtml(httpMethod) + "</div>");
            }
            if (methodRejected) {
                out.println("<div style='color:red'>HTTP method not allowed for this route: "
                        + escapeHtml(String.valueOf(httpMethod)) + "</div>");
            }

            for (ExecutionBatch batch : batches) {
                out.println("<div>Executing handlers in class: "
                        + escapeHtml(batch.handlerClass().getName())
                        + " (pattern: " + escapeHtml(batch.pattern()) + ")</div>");

                if (batch.results().isEmpty()) {
                    out.println("<div style='color:orange'>No @InvokeMe method found for this path in "
                            + escapeHtml(batch.handlerClass().getName()) + "</div>");
                    continue;
                }

                for (InvocationResult result : batch.results()) {
                    Method method = result.method();

                    // Ignored method (unsupported return type)
                    if (!result.invoked()) {
                        out.println("<div style='color:red;margin-left:20px'>Ignored: "
                                + escapeHtml(batch.handlerClass().getName() + "#" + method.getName())
                                + " (unsupported return type: "
                                + escapeHtml(method.getReturnType().getSimpleName())
                                + ")</div>");
                        continue;
                    }

                    // Method executed successfully
                    executedAny = true;
                    out.println("<div style='color:green'>Executed: "
                            + escapeHtml(batch.handlerClass().getName() + "#" + method.getName()) + "</div>");

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
            }

            out.println("</body></html>");
        }

        if (!executedAny && methodRejected) {
            resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        }
        return executedAny || methodRejected;
    }

    // ============================================================
    //  ROUTE MATCHING LOGIC
    // ============================================================

    /** Returns all registered routes matching the requested path (including placeholder routes). */
    private static List<RouteMatch> findMatchingRoutes(String requestPath) {
        List<RouteMatch> matches = new ArrayList<>();

        for (Map.Entry<String, ClassMethode> entry : ROUTES.entrySet()) {
            if (RoutePathMatcher.matches(requestPath, entry.getKey())) {
                matches.add(new RouteMatch(entry.getKey(), entry.getValue()));
            }
        }

        return matches;
    }

    private static final class RouteMatch {
        final String pattern;
        final ClassMethode classMethode;
        final List<Class<?>> handlers;

        RouteMatch(String pattern, ClassMethode classMethode) {
            this.pattern = pattern;
            this.classMethode = classMethode;
            this.handlers = classMethode.getHandlers();
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

        // Inject model data as request attributes so the view can render them.
        for (Map.Entry<String, Object> entry : mv.getModel().entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) continue;
            req.setAttribute(key, entry.getValue());
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
    private record ExecutionBatch(String pattern, Class<?> handlerClass, List<InvocationResult> results) {}

    // ============================================================
    //  REGISTRATION
    // ============================================================

    /** Registers a controller class by scanning its @InvokeMe methods. */
    public static void register(Class<?> clazz) {
        for (Method m : clazz.getDeclaredMethods()) {
            InvokeMe inv = m.getAnnotation(InvokeMe.class);
            if (inv == null || inv.path().isEmpty()) continue;

            ClassMethode cm = ROUTES.computeIfAbsent(inv.path(), ClassMethode::new);
            cm.addHandler(clazz);

            // Register allowed HTTP methods based on annotations
            boolean hasGet = m.isAnnotationPresent(GetMapping.class);
            boolean hasPost = m.isAnnotationPresent(PostMapping.class);
            if (hasGet) cm.addHttpMethod("GET");
            if (hasPost) cm.addHttpMethod("POST");
            if (!hasGet && !hasPost) {
                // Default: allow both when no specific mapping is declared.
                cm.addHttpMethod("GET");
                cm.addHttpMethod("POST");
            }
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
