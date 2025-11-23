package servlet;

import class_annotations.Controller;
import method_annotations.Route;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import view.ModelView;
import utiles.RouteHandler;
import utiles.ClasspathScanner;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

public class FrontServlet extends HttpServlet {

    private static final String ROUTES_KEY = "app.routes";
    private RequestDispatcher defaultDispatcher;

    @Override
    public void init() throws ServletException {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");

        ServletContext context = getServletContext();
        Map<String, RouteHandler> routes = (Map<String, RouteHandler>) context.getAttribute(ROUTES_KEY);

        if (routes == null) {
            routes =ClasspathScanner.scanRoutes();
            context.setAttribute(ROUTES_KEY, routes); 
            System.out.println("Routes stockées dans ServletContext");
        }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String url = req.getRequestURI().substring(req.getContextPath().length());
        if (url.isEmpty()) url = "/";

        Map<String, RouteHandler> routes = (Map<String, RouteHandler>) getServletContext().getAttribute(ROUTES_KEY);

        RouteHandler handler = routes != null ? routes.get(url) : null;

            if (handler != null) {
                try {
                    Object controller = handler.getClazz().getDeclaredConstructor().newInstance();
                    Object result = handler.getMethod().invoke(controller);

                    resp.getWriter().println("200 OK : " + url);
                    resp.getWriter().println("Class : " + controller.getClass().getName());
                    resp.getWriter().println("Method : " + handler.getMethod().getName());
                    // resp.getWriter().println("Type : " + handler.getMethod().getReturnType().getName());
                    // resp.getWriter().println("Value : " + result);

                    if(handler.getMethod().getReturnType().getName().equals("java.lang.String")) {
                        // req.getRequestDispatcher("/home.jsp").forward(req, resp);
                        resp.getWriter().println("Value : " +(String) result);
                    }
                    else if (handler.getMethod().getReturnType().getName().equals("view.ModelView")) {
                        ModelView model = (ModelView) result;

                        for (Map.Entry<String, Object> entry : model.getData().entrySet()) {
                            req.setAttribute(entry.getKey(), entry.getValue());
                        }

                        req.getRequestDispatcher(model.getView()).forward(req, resp);
                    }

                    else {
                        resp.getWriter().println("Value : Not Supported");
    ;
                    }


            } catch (Exception e) {
                throw new ServletException("Erreur", e);
            }
        } else {
            if (!"/".equals(url) && getServletContext().getResource(url) != null) {
                defaultDispatcher.forward(req, resp);
            } else {
                resp.setStatus(404);
                resp.getWriter().println("404 - Not Found : " + url);
            }
        }
    }
}