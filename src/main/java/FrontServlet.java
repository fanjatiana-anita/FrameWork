package servlet;

import class_annotations.*;
import method_annotations.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import view.*;
import utiles.*;
import jakarta.servlet.annotation.MultipartConfig;  
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

@MultipartConfig  
public class FrontServlet extends HttpServlet {
//
    private static final String ROUTES_KEY = "app.routes";
    private RequestDispatcher defaultDispatcher;

    @Override
    public void init() throws ServletException {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");

        ServletContext context = getServletContext();
        Map<String, List<RouteHandler>> routes = (Map<String, List<RouteHandler>>) context.getAttribute(ROUTES_KEY);

        if (routes == null) {
            routes =ClasspathScanner.scanRoutes();
            context.setAttribute(ROUTES_KEY, routes); 
            System.out.println("Routes stockées dans ServletContext");
        }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        JsonResponse jsonResponse = new JsonResponse(resp);
        String url = req.getRequestURI().substring(req.getContextPath().length());
        if (url.isEmpty()) url = "/";

        Map<String, List<RouteHandler>> routes = 
                (Map<String, List<RouteHandler>>) getServletContext().getAttribute(ROUTES_KEY);

        String httpMethod = req.getMethod();

        RouteHandler handler = UrlUtils.findRoute(url, httpMethod, routes);


        if(handler != null) {
            Method method = handler.getMethod();
            try {

                Object controller = handler.getClazz().getDeclaredConstructor().newInstance();
                Object[] args = ParamResolver.resolveArguments(req, handler);
                Object result = method.invoke(controller, args);

                if (method.isAnnotationPresent(Json.class)) {
                    resp.setContentType("application/json;charset=UTF-8");

                    if (result instanceof ModelView mv) {
                        if (getServletContext().getResource(mv.getView()) == null) {
                            jsonResponse.sendJsonError(404, "View Not Found: " + mv.getView());
                            return;
                        }
                        jsonResponse.sendJsonSuccess(mv.getData());
                        return;
                    }

                    jsonResponse.sendJsonSuccess(result);
                    return;
                }
                
                if (result instanceof String str) {
                    resp.getWriter().println("String Value : " + str);
                } else if (result instanceof ModelView mv) {

                    // Transférer les données
                    mv.getData().forEach(req::setAttribute);

                    // Vérifier si la vue existe
                     String viewPath = mv.getView();
                        if (getServletContext().getResource(viewPath) == null) {
                            resp.setStatus(404);
                            resp.getWriter().println("404 - View Not Found: " + viewPath);
                            return;
                        }
                    RequestDispatcher disp = req.getRequestDispatcher(mv.getView());

                    // SI LA VUE EST INTROUVABLE → gestion JSON ou non JSON
                    if (disp == null) {
                        if (method.isAnnotationPresent(Json.class)) {
                            jsonResponse.sendJsonError(404, "View Not Found: " + mv.getView());
                            return;
                        }
                        resp.setStatus(404);
                        resp.getWriter().println("404 - View Not Found: " + mv.getView());
                        return;
                    }

                    // Si tout est bon → afficher la vue
                    disp.forward(req, resp);
                    return;
                }
            } catch (IllegalArgumentException e) {
                if (method.isAnnotationPresent(Json.class)) {
                    jsonResponse.sendJsonError(400,"Invalid argument: " + e.getMessage());
                    return;
                }

                resp.setStatus(400);
                resp.setContentType("text/html; charset=UTF-8");
                resp.getWriter().println("<h2 style='color:red'>400 - Invalid Request</h2>");
                resp.getWriter().println("<p><strong>" + e.getMessage() + "</strong></p>");
                resp.getWriter().println("<p>URL : " + req.getRequestURI() + "</p>");
                return;
            }
            catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                if (method != null && method.isAnnotationPresent(method_annotations.Json.class)) {
                    jsonResponse.sendJsonError(500,"Internal error: " + cause.getMessage());
                    return;
                }

                cause.printStackTrace();
                throw new ServletException("Erreur serveur interne", cause);
            }
        }
        
        String cleanUrl = UrlUtils.normalizeUrl(url);

        // 405 si l'URL existe mais pas la méthode HTTP
        if (routes.containsKey(cleanUrl)) {
            resp.setStatus(405);
            resp.getWriter().println("405 Method Not Allowed");
            resp.getWriter().println("Required method : " +
                routes.get(cleanUrl).stream()
                    .map(RouteHandler::getHttpMethod)
                    .distinct()
                    .collect(java.util.stream.Collectors.joining(", ")));
            return;
        }
        // Ressources statiques (css, js, images...)
        if (!"/".equals(url) && getServletContext().getResource(url) != null) {
            defaultDispatcher.forward(req, resp);
            return;
        }

        // 404
        resp.setStatus(404);
        resp.getWriter().println("404 - Not Found : " + url);
    }


}