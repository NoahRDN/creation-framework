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

// Replaced ClassGraph usage with in-project scanner
import java.lang.reflect.Method;
import java.util.List;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import edu.noah.monframework.util.ControllerScanner;

/**
 * Simple registry that maps paths (strings) to classes that declare @InvokeMe(path=...)
 * and exposes execution by path.
 */
public class AnnotatedRouteRegistry {
    private static final Map<String, List<Class<?>>> ROUTES = new HashMap<>();
    
    /** Execute handlers registered for the exact path. Returns true if any handler was invoked. */
    public static boolean executePath(String path, HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        List<Class<?>> handlers = ROUTES.get(path);
        if (handlers == null || handlers.isEmpty()) return false;

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
            out.println("<h1>Handlers exécutés pour le chemin annoté</h1>");
            for (String chunk : htmlChunks) {
                out.println(chunk);
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

    private static boolean handleModelViewResult(Object raw, HttpServletRequest req, HttpServletResponse resp,
                                                 List<String> htmlChunks) throws ServletException, IOException {
        ModelView mv = (raw instanceof ModelView) ? (ModelView) raw : null;
        if (mv == null) {
            htmlChunks.add("<div style=\"color:red;margin-left:20px\">ModelView null retourné</div>");
            return false;
        }
        ViewResolution view = resolveView(req.getServletContext(), mv.getView());
        if (view == null) {
            htmlChunks.add("<div style=\"color:red;margin-left:20px\">Ressource ModelView introuvable ou invalide : "
                    + escapeHtml(String.valueOf(mv.getView())) + "</div>");
            return false;
        }
        RequestDispatcher dispatcher = req.getRequestDispatcher(view.dispatchPath());
        if (dispatcher == null) {
            htmlChunks.add("<div style=\"color:red;margin-left:20px\">Impossible d'obtenir un dispatcher pour : "
                    + escapeHtml(view.dispatchPath()) + "</div>");
            return false;
        }
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

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
