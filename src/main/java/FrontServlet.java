package servlet;

import java.io.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

public class FrontServlet extends HttpServlet {
    RequestDispatcher defaultDispatcher;

    @Override
    public void init() {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");

    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        String url = req.getRequestURI().substring(req.getContextPath().length());
        boolean ressourceFound = getServletContext().getResource(url) != null;

        if(ressourceFound && !url.equals("/")) {
           defaultDispatcher.forward(req, res);
            
        }
        else {
            res.getWriter().println("Requested URL: " + url);

        }  
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
