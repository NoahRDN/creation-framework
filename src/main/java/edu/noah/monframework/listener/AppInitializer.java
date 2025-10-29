package edu.noah.monframework.listener;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import edu.noah.monframework.registry.AnnotatedRouteRegistry;

@WebListener
public class AppInitializer implements ServletContextListener {
    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // AnnotatedRouteRegistry.autoRegister("edu.noah.monframework.example");
        // System.out.println("[AppInitializer] Routes auto-enregistrées !");
    }
}
    