package utiles;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class UrlUtils {

    public static RouteHandler matchDynamicUrl(String url, Map<String, RouteHandler> routes) {
        for (Map.Entry<String, RouteHandler> entry : routes.entrySet()) {
            String routeKey = entry.getKey();
            RouteHandler handler = entry.getValue();

            if (routeKey.contains("{id}")) {
                // String regex = routeKey.replace("{id}", "(\\d+)");
                String regex = routeKey.replace("{id}", "(.+)");
                if (Pattern.matches(regex, url)) {
                    return handler;
                }
            }
        }
        return null;
    }
   
    public static RouteHandler findRoute(String requestUrl, Map<String, RouteHandler> routes) {
        if (routes == null || requestUrl == null) return null;

        String cleanUrl = normalizeUrl(requestUrl);

        RouteHandler handler = routes.get(cleanUrl);
        if (handler != null) {
            return handler;
        }
        return matchDynamicUrl(cleanUrl, routes);
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