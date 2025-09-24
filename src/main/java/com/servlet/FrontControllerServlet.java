package com.servlet;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

public class FrontControllerServlet extends HttpServlet {

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        String lastSegment = path.substring(path.lastIndexOf("/") + 1);

        if (!lastSegment.contains(".")) {
            response.setContentType("text/html;charset=UTF-8");
            try (PrintWriter out = response.getWriter()) {
                out.println("<html><body>");
                out.println("<h2>URL valide capturée : " + path + "</h2>");
                out.println("</body></html>");
            }
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Page non trouvée");
        }
    }
}
