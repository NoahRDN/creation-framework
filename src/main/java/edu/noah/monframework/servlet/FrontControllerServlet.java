package edu.noah.monframework.servlet;

import edu.noah.monframework.example.AnnotatedExample;
import edu.noah.monframework.registry.AnnotatedRouteRegistry;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@WebServlet("/*")
public class FrontControllerServlet extends HttpServlet {

    @Override
    public void init() throws ServletException {
        super.init();
        AnnotatedRouteRegistry.register(AnnotatedExample.class);
        if (getServletContext() != null) getServletContext().log("Annotated routes registered: " + AnnotatedRouteRegistry.getRegisteredPaths());
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && path.startsWith(context)) {
            path = path.substring(context.length());
        }

        boolean handled = AnnotatedRouteRegistry.executePath(path, request, response);
        if (!handled) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.setContentType("text/html;charset=UTF-8");
            try (PrintWriter out = response.getWriter()) {
                out.println("<html><body>");
                out.println("<h2>404 - No handler for: " + path + "</h2>");
                out.println("</body></html>");
            }
        }
    }
}
