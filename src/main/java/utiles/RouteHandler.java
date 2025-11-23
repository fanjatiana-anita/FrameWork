package utiles;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class RouteHandler {

    private final Class<?> clazz;
    private final Method method;
    private final Map<String, String> pathVariables = new HashMap<>();

    public RouteHandler(Class<?> clazz, Method method) {
        this.clazz = clazz;
        this.method = method;
    }

    public Class<?> getClazz() {
        return this.clazz;
    }

    public Method getMethod() {
        return this.method;
    }

    public void setPathVariable(String name, String value) {
        pathVariables.put(name, value);
    }

    public String getPathVariable(String name) {
        return pathVariables.get(name);
    }
}
