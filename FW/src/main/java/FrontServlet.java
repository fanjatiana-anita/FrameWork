package servlet;

import java.io.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

public class FrontServlet extends HttpServlet {

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        String url = req.getRequestURI();

        req.setAttribute("requestedUrl", url);

        RequestDispatcher dispatcher = req.getRequestDispatcher("/showUrl.jsp");
        dispatcher.forward(req, res);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        service(req, res); 
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        service(req, res);
    }
}
