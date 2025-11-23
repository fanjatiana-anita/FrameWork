package servlet;

import class_annotations.Controller;
import method_annotations.Route;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import view.ModelView;
import utiles.RouteHandler;
import utiles.UrlUtils;
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

    Map<String, RouteHandler> routes = (Map<String, RouteHandler>) 
            getServletContext().getAttribute(ROUTES_KEY);

    RouteHandler handler = routes != null ? routes.get(url) : null;

    // ====================== ROUTES DYNAMIQUES ======================
    if (handler == null) {
        RouteHandler dynamicHandler = UrlUtils.matchDynamicUrl(url, routes);
        if (dynamicHandler != null) {
            try {
                Object controller = dynamicHandler.getClazz().getDeclaredConstructor().newInstance();
                Method method = dynamicHandler.getMethod();
                Object result;

                String id = null;
                    
                result = method.invoke(controller, new Object[]{ id });

                // Debug ou traitement du retour
                resp.getWriter().println("200 OK (dynamic) : " + url);
                resp.getWriter().println("Result : " + result);

            } catch (Exception e) {
                throw new ServletException("Erreur dynamique", e);
            }
            return;
        }
    }

    // ====================== ROUTES STATIQUES ======================
    if (handler != null) {
        try {
            Object controller = handler.getClazz().getDeclaredConstructor().newInstance();
            Method method = handler.getMethod();

            Object result;
            if (method.getParameterCount() == 0) {
                result = method.invoke(controller, new Object[0]);  
            } else {
                throw new ServletException("Méthodes avec paramètres non supportées en route statique pour l'instant");
            }

            resp.getWriter().println("200 OK : " + url);
            resp.getWriter().println("Class : " + controller.getClass().getName());
            resp.getWriter().println("Method : " + method.getName());
            resp.getWriter().println("Value : " + result);

            // Gestion du retour
            if (method.getReturnType() == String.class) {
                resp.getWriter().println((String) result);
            }
            else if (method.getReturnType() == ModelView.class) {
                ModelView mv = (ModelView) result;
                mv.getData().forEach(req::setAttribute);
                req.getRequestDispatcher(mv.getView()).forward(req, resp);
            }

        } catch (Exception e) {
            throw new ServletException("Erreur", e);
        }
        return;
    }

    // ====================== 404 ou ressources statiques ======================
    if (!"/".equals(url) && getServletContext().getResource(url) != null) {
        defaultDispatcher.forward(req, resp);
    } else {
        resp.setStatus(404);
        resp.getWriter().println("404 - Not Found : " + url);
    }
}
}