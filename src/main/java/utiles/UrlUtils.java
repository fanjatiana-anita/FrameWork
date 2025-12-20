package utiles;

import java.util.List;
import java.util.Map;

public class UrlUtils {

    public static RouteHandler matchDynamicUrl(String url, String httpMethod, Map<String, List<RouteHandler>> routes) {
        for (Map.Entry<String,List <RouteHandler>> entry : routes.entrySet()) {
            String routePattern = entry.getKey();  // ex: "/user/{id}", "/article/{slug}"
            List<RouteHandler> handlers = entry.getValue();

            // Construire le regex : {xxx} → ([^/]+)
            String regex = routePattern.replaceAll("\\{[^}]+\\}", "([^/]+)");
            regex = "^" + regex + "$";

            java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(url);
            if (m.matches()) {
                // Trouver le bon handler selon la methode HTTP
                RouteHandler handler = null;
                for (RouteHandler h : handlers) {
                    if ("*".equals(h.getHttpMethod()) || h.getHttpMethod().equalsIgnoreCase(httpMethod)) {
                        handler = h;
                        break;
                    }
                }
                if (handler == null) continue;

                java.util.regex.Matcher nameMatcher = java.util.regex.Pattern.compile("\\{([^}]+)\\}").matcher(routePattern);
                int groupIndex = 1;
                while (nameMatcher.find()) {
                    String varName = nameMatcher.group(1);
                    String value = m.group(groupIndex++);
                    handler.setPathVariable(varName, value);
                }
                return handler;
            }
        }
        return null;
    }
   
    public static RouteHandler findRoute(String requestUrl, String httpMethod ,Map<String, List<RouteHandler>> routes) {
        if (routes == null || requestUrl == null) return null;

        String cleanUrl = normalizeUrl(requestUrl);

    List<RouteHandler> handlers = routes.get(cleanUrl);
        if (handlers != null) {
            for (RouteHandler h : handlers) {
                if ("*".equals(h.getHttpMethod()) || h.getHttpMethod().equalsIgnoreCase(httpMethod)) {
                    return h;
                }
            }
        }
        return matchDynamicUrl(cleanUrl, httpMethod,routes);
    }


    public static String normalizeUrl(String fullUrl) {
        if (fullUrl == null) return "/";

        String contextPath = ""; 
        if (!contextPath.isEmpty() && fullUrl.startsWith(contextPath)) {
            fullUrl = fullUrl.substring(contextPath.length());
        }

        int questionIndex = fullUrl.indexOf('?');
        if (questionIndex != -1) {
            fullUrl = fullUrl.substring(0, questionIndex);
        }

        if (fullUrl.length() > 1 && fullUrl.endsWith("/")) {
            fullUrl = fullUrl.substring(0, fullUrl.length() - 1);
        }

        if (!fullUrl.startsWith("/")) {
            fullUrl = "/" + fullUrl;
        }

        return fullUrl.isEmpty() ? "/" : fullUrl;
    }

}