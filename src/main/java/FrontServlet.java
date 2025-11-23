package servlet;

import class_annotations.Controller;
import method_annotations.Route;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import view.ModelView;
import utiles.RouteHandler;
import utiles.UrlUtils;
import utiles.ClasspathScanner;
import utiles.ParamResolver;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
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

    Map<String, RouteHandler> routes = (Map<String, RouteHandler>) 
            getServletContext().getAttribute(ROUTES_KEY);

    RouteHandler handler = UrlUtils.findRoute(url, routes);


    if(handler != null) {
        try {
            Object controller = handler.getClazz().getDeclaredConstructor().newInstance();
            Method method = handler.getMethod();

            Object[] args = ParamResolver.resolveArguments(method, req, handler);
            Object result = method.invoke(controller, args);

            if (result instanceof String str) {
                resp.getWriter().println("String Value : " + str);
            } else if (result instanceof ModelView mv) {
                mv.getData().forEach(req::setAttribute);
                req.getRequestDispatcher(mv.getView()).forward(req, resp);
            } else {
                resp.getWriter().println("Résultat : " + result);
            }

            resp.getWriter().println("200 OK : " + url);
            resp.getWriter().println("Class : " + controller.getClass().getName());
            resp.getWriter().println("Method : " + method.getName());
            resp.getWriter().println("Value : " + result);

            return;

        } catch (IllegalArgumentException e) {
            resp.setStatus(400); // Bad Request
            resp.setContentType("text/html; charset=UTF-8");
            resp.getWriter().println("<h2 style='color:red'>400 Error - Invalid request</h2>");
            resp.getWriter().println("<p><strong>" + e.getMessage() + "</strong></p>");
            resp.getWriter().println("<p>URL : " + req.getRequestURI() + "</p>");
            return;

        } catch (Exception e) {
            e.printStackTrace();
            throw new ServletException("Erreur serveur interne", e);
        }
    }
    
    // ====================== 404 ou ressources statiques ======================
    if (!"/".equals(url) && getServletContext().getResource(url) != null) {
        defaultDispatcher.forward(req, resp);
        return;
    } else {
        resp.setStatus(404);
        resp.getWriter().println("404 - Not Found : " + url);
    }
}
}