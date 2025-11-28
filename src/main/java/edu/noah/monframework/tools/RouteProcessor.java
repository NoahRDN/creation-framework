package edu.noah.monframework.tools;

import edu.noah.monframework.util.AnnotatedMethodExecutor;
import edu.noah.monframework.util.AnnotatedMethodExecutor.InvocationResult;
import edu.noah.monframework.web.ModelView;
import java.util.List;

/**
 * Encapsule l'affichage des routes et l'exécution des handlers annotés.
 * Permet à Main de simplement passer la liste de routes à traiter.
 */
public class RouteProcessor {

    /** Affiche les routes et exécute les handlers annotés. */
    public static void processRoutes(List<RouteInfo> routes) {
        if (routes == null || routes.isEmpty()) {
            System.out.println("No routes to process.");
            return;
        }

        System.out.println("Discovered routes (url -> source):");
        for (RouteInfo r : routes) {
            System.out.println("- " + r.getUrl() + " -> " + r.getSource());
            if (!r.getHandlers().isEmpty()) {
                for (String h : r.getHandlers()) System.out.println("    -> " + h);
            }
        }

        System.out.println();
        System.out.println("Executing annotated handlers (if any):\n");

        for (RouteInfo r : routes) {
            if (r.getSource() != RouteInfo.Source.ANNOTATION) continue;
            String path = r.getUrl();
            String execPath = path;
            boolean hasPlaceholder = path.contains("{") && path.contains("}");
            if (hasPlaceholder) {
                execPath = path.replaceAll("\\{[^/]+\\}", "123"); // sample value for placeholder
                System.out.println("=== Executing annotated handlers for path: " + execPath + " (pattern: " + path + ") ===");
            } else {
                System.out.println("=== Executing annotated handlers for path: " + path + " ===");
            }
            for (String handler : r.getHandlers()) {
                // handler format: full.class.Name#methodName[...] -> extract class
                String className = handler.split("#")[0];
                try {
                    Class<?> c = Class.forName(className);
                    try {
                        List<InvocationResult> results = AnnotatedMethodExecutor.executeAnnotatedMethodsForPath(c, execPath, null, null);
                        for (InvocationResult result : results) {
                            if (!result.invoked()) {
                                System.out.println("      -> Ignoré (type non supporté) : "
                                        + result.method().getReturnType().getSimpleName());
                                continue;
                            }
                            Class<?> returnType = result.method().getReturnType();
                            if (String.class.equals(returnType)) {
                                System.out.println("      -> Retour String: " + result.returnValue());
                            } else if (ModelView.class.isAssignableFrom(returnType)) {
                                ModelView mv = (ModelView) result.returnValue();
                                if (mv == null) {
                                    System.out.println("      -> ModelView vers: null");
                                } else {
                                    System.out.println("      -> ModelView vers: " + mv.getView());
                                    if (!mv.getModel().isEmpty()) {
                                        System.out.println("         model data:");
                                        mv.getModel().forEach((k, v) -> System.out.println("           - " + k + " = " + v));
                                    }
                                }
                            }
                        }
                    } catch (Throwable t) {
                        System.err.println("Failed executing on " + className + ": " + t.getMessage());
                        t.printStackTrace(System.err);
                    }
                } catch (ClassNotFoundException cnf) {
                    System.err.println("Class not found for handler: " + handler + " (" + cnf.getMessage() + ")");
                }
            }
            System.out.println();
        }
    }
}
