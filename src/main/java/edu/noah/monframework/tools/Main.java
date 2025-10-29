package edu.noah.monframework.tools;

import java.util.List;
import edu.noah.monframework.tools.RouteLister;
import edu.noah.monframework.tools.RouteInfo;
import edu.noah.monframework.tools.RouteProcessor;
import edu.noah.monframework.registry.AnnotatedRouteRegistry;
import java.util.stream.Collectors;

/**
 * Small test harness that scans the specified base package for methods annotated with
 * {@link InvokeMe}, prints the mapping (path -> class#method) and then simulates
 * executing the handlers for each discovered path.
 *
 * Usage: java edu.noah.monframework.tools.Main [base.package.to.scan]
 * Example: java edu.noah.monframework.tools.Main edu.noah.monframework
 */
public class Main {
    public static void main(String[] args) {

        String basePackage = (args.length > 0) ? args[0] : "edu.noah.monframework";
        String webappPath = (args.length > 1) ? args[1] : "src/main/webapp";

        System.out.println("Collecting routes for package='" + basePackage + "' and webapp='" + webappPath + "'");

        // Test: find controller classes annotated with @Controller in the package
        System.out.println("Looking for @Controller classes under package: " + basePackage);
        List<Class<?>> controllers = AnnotatedRouteRegistry.findControllersInPackage(basePackage);
        if (controllers == null || controllers.isEmpty()) {
            System.out.println("No controller classes found.");
        } else {
            System.out.println("Found " + controllers.size() + " controller(s):");
            for (Class<?> c : controllers) {
                System.out.println(" - " + c.getName());
            }
            System.out.println();
        }

        // Continue with existing route discovery and processing
        List<RouteInfo> routes = RouteLister.collectAll(basePackage, webappPath);
        if (routes.isEmpty()) {
            System.out.println("No routes found.");
            return;
        }

        RouteProcessor.processRoutes(routes);
    }
}
