package edu.noah.monframework.tools;

import edu.noah.monframework.util.AnnotatedMethodExecutor;
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
            System.out.println("=== Executing annotated handlers for path: " + path + " ===");
            for (String handler : r.getHandlers()) {
                // handler format: full.class.Name#methodName[...] -> extract class
                String className = handler.split("#")[0];
                try {
                    Class<?> c = Class.forName(className);
                    try {
                        AnnotatedMethodExecutor.executeAnnotatedMethodsForPath(c, path);
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
