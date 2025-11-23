package utiles;

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

}