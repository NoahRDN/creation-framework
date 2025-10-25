package edu.noah.monframework.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;

import edu.noah.monframework.registry.AnnotatedRouteRegistry;

@WebFilter("/*")
public class MyFilter implements Filter {

    private ServletContext context;
    private static final String DEFAULT_PATH = "/";

    @Override
    public void init(FilterConfig filterConfig) {
        this.context = filterConfig.getServletContext();
        if (this.context != null) this.context.log("[MyFilter] Initialisé");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String path = req.getRequestURI().substring(req.getContextPath().length());
        if (path.isEmpty()) path = DEFAULT_PATH;

        if (isStaticResource(path)) {
            chain.doFilter(request, response);
            return; 
        }

        boolean handled = AnnotatedRouteRegistry.executePath(path, req, resp);

        if (!handled) {
            send404(resp, path);
        }
    }

    private void send404(HttpServletResponse resp, String path) throws IOException {
        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
        resp.setContentType("text/html;charset=UTF-8");
        try (PrintWriter out = resp.getWriter()) {
            out.println("<html><body>");
            out.println("<h2>URL Capturé: " + path + "</h2>");
            out.println("</body></html>");
        }
    }

    private boolean isStaticResource(String path) {
        try {
            return !DEFAULT_PATH.equals(path) && context.getResource(path) != null;
        } catch (MalformedURLException e) {
            return false;
        }
    }


    @Override
    public void destroy() {
        if (this.context != null) this.context.log("[MyFilter] Détruit");
    }
}
