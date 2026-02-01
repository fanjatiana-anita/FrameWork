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
import java.util.*;

@MultipartConfig  
public class FrontServlet extends HttpServlet {

    private static final String ROUTES_KEY = "app.routes";
    private RequestDispatcher defaultDispatcher;

    @Override
    public void init() throws ServletException {
        defaultDispatcher = getServletContext().getNamedDispatcher("default");
        
        // Charger la configuration d'authentification
        AuthConfig.loadConfig(getServletContext());
        
        ServletContext context = getServletContext();
        Map<String, List<RouteHandler>> routes = (Map<String, List<RouteHandler>>) context.getAttribute(ROUTES_KEY);

        if (routes == null) {
            routes = ClasspathScanner.scanRoutes();
            context.setAttribute(ROUTES_KEY, routes); 
            System.out.println("Routes stockees dans ServletContext");
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

                // ========================================
                // eTAPE 1 : CHARGER HttpSession → Map
                // ========================================
                HttpSession httpSession = req.getSession(true); 
                Map<String, Object> sessionMap = new HashMap<>();
                
                Enumeration<String> names = httpSession.getAttributeNames();
                while (names.hasMoreElements()) {
                    String attrName = names.nextElement();
                    sessionMap.put(attrName, httpSession.getAttribute(attrName));
                }
                
                req.setAttribute("sessionMap", sessionMap);
                
                System.out.println("=== SESSION CHARGeE ===");
                System.out.println("Nombre d'attributs : " + sessionMap.size());
                sessionMap.forEach((k, v) -> System.out.println("  " + k + " = " + v));

                // ========================================
                // eTAPE 1.5 : VeRIFICATION AUTHENTIFICATION ET RÔLES
                // ========================================
                AuthConfig authConfig = AuthConfig.getInstance();
                
                // Verifier @Authentified
                if (method.isAnnotationPresent(Authentified.class)) {
                    if (!authConfig.isAuthenticated(sessionMap)) {
                        System.out.println("Acces refuse : utilisateur non Authentified");
                        handleUnauthorized(req, resp, method, jsonResponse, 401, 
                            "Vous devez etre connecte pour acceder a cette ressource");
                        return;
                    }
                }
                
                // Verifier @Role
                if (method.isAnnotationPresent(Role.class)) {
                    Role roleAnnotation = method.getAnnotation(Role.class);
                    String[] allowedRoles = roleAnnotation.value();
                    
                    // @Role implique automatiquement @Authentified
                    if (!authConfig.isAuthenticated(sessionMap)) {
                        System.out.println("Acces refuse : utilisateur non Authentified");
                        handleUnauthorized(req, resp, method, jsonResponse, 401,
                            "Vous devez etre connecte pour acceder a cette ressource");
                        return;
                    }
                    
                    // Verifier les rôles
                    if (!authConfig.hasRole(sessionMap, allowedRoles)) {
                        String userRole = authConfig.getUserRole(sessionMap);
                        System.out.println("Acces refuse : rôle insuffisant");
                        System.out.println("   Rôle utilisateur : " + userRole);
                        System.out.println("   Rôles requis : " + Arrays.toString(allowedRoles));
                        handleUnauthorized(req, resp, method, jsonResponse, 403,
                            "Vous n'avez pas les permissions necessaires. Rôles requis : " + Arrays.toString(allowedRoles));
                        return;
                    }
                    
                    System.out.println("Acces autorise : rôle valide");
                }

                // ========================================
                // eTAPE 2 : Appeler la methode du contrôleur
                // ========================================
                Object[] args = ParamResolver.resolveArguments(req, resp, handler);
                Object result = method.invoke(controller, args);

                // ========================================
                // eTAPE 3 : SYNCHRONISER Map → HttpSession
                // ========================================
                sessionMap = (Map<String, Object>) req.getAttribute("sessionMap");
                if (sessionMap != null) {
                    // Supprimer les cles qui ont ete retirees de la map
                    Set<String> toRemove = new HashSet<>();
                    Enumeration<String> sessionKeys = httpSession.getAttributeNames();
                    while (sessionKeys.hasMoreElements()) {
                        String key = sessionKeys.nextElement();
                        if (!sessionMap.containsKey(key)) {
                            toRemove.add(key);
                        }
                    }
                    toRemove.forEach(httpSession::removeAttribute);
                    
                    // Mettre a jour / ajouter les valeurs
                    for (Map.Entry<String, Object> entry : sessionMap.entrySet()) {
                        httpSession.setAttribute(entry.getKey(), entry.getValue());
                    }
                    
                    System.out.println("=== SESSION SYNCHRONISeE ===");
                    System.out.println("Cles supprimees : " + toRemove);
                    System.out.println("Cles mises a jour : " + sessionMap.keySet());
                }

                // ========================================
                // eTAPE 4 : Gerer le resultat
                // ========================================
                
                // Cas special : null (redirection dejà effectuee dans le contrôleur)
                if (result == null) {
                    System.out.println("Resultat null - la reponse a dejà ete traitee");
                    return;
                }

                // Cas JSON
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
                
                // Cas String simple
                if (result instanceof String str) {
                    resp.getWriter().println("String Value : " + str);
                    return;
                }
                
                // Cas ModelView
                if (result instanceof ModelView mv) {
                    
                    // === GESTION DES REDIRECTIONS ===
                    String viewPath = mv.getView();
                    
                    if (viewPath.startsWith("redirect:")) {
                        String redirectPath = viewPath.substring("redirect:".length());
                        String fullUrl = req.getContextPath() + redirectPath;
                        System.out.println("🔄 Redirection vers : " + fullUrl);
                        resp.sendRedirect(fullUrl);
                        return;
                    }
                    
                    // === GESTION NORMALE DES VUES JSP ===
                    mv.getData().forEach(req::setAttribute);

                    if (getServletContext().getResource(viewPath) == null) {
                        resp.setStatus(404);
                        resp.getWriter().println("404 - View Not Found: " + viewPath);
                        return;
                    }
                    
                    RequestDispatcher disp = req.getRequestDispatcher(viewPath);
                    if (disp == null) {
                        resp.setStatus(404);
                        resp.getWriter().println("404 - View Not Found: " + viewPath);
                        return;
                    }

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
        
        if (!"/".equals(url) && getServletContext().getResource(url) != null) {
            defaultDispatcher.forward(req, resp);
            return;
        }

        resp.setStatus(404);
        resp.getWriter().println("404 - Not Found : " + url);
    }
    
    /**
     * Gere les erreurs d'autorisation (401 Unauthorized ou 403 Forbidden)
     * AFFICHE DIRECTEMENT la page d'erreur (pas de redirection)
     */
    private void handleUnauthorized(HttpServletRequest req, HttpServletResponse resp, Method method,
                                   JsonResponse jsonResponse, int statusCode, String message)
            throws IOException, ServletException {
        
        // Si la methode retourne du JSON, repondre en JSON
        if (method.isAnnotationPresent(Json.class)) {
            jsonResponse.sendJsonError(statusCode, message);
            return;
        }
        
        // Sinon, afficher la page d'erreur DIRECTEMENT (pas de redirection)
        resp.setStatus(statusCode);
        
        // Mettre les infos d'erreur dans les attributs de la requete
        req.setAttribute("errorCode", statusCode);
        req.setAttribute("errorMessage", message);
        
        // Afficher la page d'erreur appropriee
        String viewPath = statusCode == 401 ? "/error401.jsp" : "/error403.jsp";
        
        RequestDispatcher disp = req.getRequestDispatcher(viewPath);
        if (disp != null) {
            disp.forward(req, resp);
        } else {
            // Fallback si les JSP n'existent pas
            resp.setContentType("text/html; charset=UTF-8");
            resp.getWriter().println("<h1>" + statusCode + " - " + 
                (statusCode == 401 ? "Non Authentified" : "Acces Interdit") + "</h1>");
            resp.getWriter().println("<p>" + message + "</p>");
            resp.getWriter().println("<p><a href='" + req.getContextPath() + "/login'>Se connecter</a></p>");
        }
    }
}