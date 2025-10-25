package edu.noah.monframework.registry;

import edu.noah.monframework.annotation.InvokeMe;
import edu.noah.monframework.util.AnnotatedMethodExecutor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import java.lang.reflect.Method;
import java.util.List;

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Simple registry that maps paths (strings) to classes that declare @InvokeMe(path=...)
 * and exposes execution by path.
 */
public class AnnotatedRouteRegistry {
    private static final Map<String, List<Class<?>>> ROUTES = new HashMap<>();

    /**
     * Auto-register all annotated classes found under the given package.
     */
    public static void autoRegister(String basePackage) {
        System.out.println("[AutoScan] Scanning package: " + basePackage);
        try (ScanResult scanResult = new ClassGraph()
                .enableAllInfo()
                .acceptPackages(basePackage)
                .scan()) {

            List<Class<?>> classes = scanResult.getClassesWithMethodAnnotation(InvokeMe.class.getName())
                                            .loadClasses();

            for (Class<?> clazz : classes) {
                for (Method m : clazz.getDeclaredMethods()) {
                    if (m.isAnnotationPresent(InvokeMe.class)) {
                        InvokeMe inv = m.getAnnotation(InvokeMe.class);
                        if (inv != null && !inv.path().isEmpty()) {
                            ROUTES.computeIfAbsent(inv.path(), k -> new ArrayList<>()).add(clazz);
                            System.out.println("[AutoScan] Route: " + inv.path() + " -> " + clazz.getSimpleName());
                        }
                    }
                }
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
            out.println("<h2>URL Capturé avec annotation : " + path + "</h2>");
            for (Class<?> c : handlers) {
                out.println("<div>Exécution des gestionnaires dans la classe : " + c.getName() + "</div>");
                try {
                    AnnotatedMethodExecutor.executeAnnotatedMethodsForPath(c, path);
                    out.println("<div style=\"color:green\">Exécuté : " + c.getName() + "</div>");
                } catch (Throwable t) {
                    out.println("<div style=\"color:red\">Erreur lors de l'exécution de " + c.getName() + ": " + t.getMessage() + "</div>");
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
