package edu.noah.monframework.listener;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import edu.noah.monframework.registry.AnnotatedRouteRegistry;

@WebListener
public class AppInitializer implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            AnnotatedRouteRegistry.registerControllersInPackage("edu.noah.monframework");
            if (sce != null && sce.getServletContext() != null) {
                sce.getServletContext().log("[AppInitializer] Controller routes registered: " + AnnotatedRouteRegistry.getRegisteredPaths());
            } else {
                System.out.println("[AppInitializer] Controller routes registered: " + AnnotatedRouteRegistry.getRegisteredPaths());
            }
        } catch (Exception e) {
            System.out.println("[AppInitializer] Failed to register controller routes: " + e.getMessage());
            if (sce != null && sce.getServletContext() != null) sce.getServletContext().log("Failed to register controller routes", e);
        }
    }
}
    