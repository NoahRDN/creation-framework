package edu.noah.monframework.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;

public class MyFilter implements Filter {
    private ServletContext context;

    @Override
    public void init(FilterConfig filterConfig) {
        context = filterConfig.getServletContext();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        String path = req.getRequestURI().substring(req.getContextPath().length());

        String realPath = context.getRealPath(path);
        File file = new File(realPath);

        if (file.exists() && !file.isDirectory()) {
            chain.doFilter(request, response);
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void destroy() {}
}
