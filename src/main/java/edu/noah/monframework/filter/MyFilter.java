package edu.noah.monframework.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MyFilter implements Filter {
    private ServletContext context;

    @Override
    public void init(FilterConfig filterConfig) {
        this.context = filterConfig.getServletContext();
        if (this.context != null) this.context.log("MyFilter initialized");
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest req = (HttpServletRequest) request;
        String path = req.getRequestURI().substring(req.getContextPath().length());
        if (path == null || path.isEmpty()) path = "/";

        // Try to load resource from the web application (works for exploded or packaged WAR)
        InputStream is = null;
        try {
            is = context.getResourceAsStream(path);
        } catch (Exception e) {
            if (context != null) context.log("Error reading resource stream for " + path + ": " + e.getMessage());
        }

        if (is != null) {
            try (InputStream in = is; OutputStream out = response.getOutputStream()) {
                String mime = context.getMimeType(path);
                if (mime != null) response.setContentType(mime);
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    out.write(buffer, 0, len);
                }
                out.flush();
            }
            return; 
        }

        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {
        if (this.context != null) this.context.log("MyFilter destroyed");
    }
}
