package utiles;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class RouteHandler {

    private final Class<?> clazz;
    private final Method method;
    private final Map<String, String> pathVariables = new HashMap<>();
    private String httpMethod;


    public RouteHandler(Class<?> clazz, Method method,String httpMethod) {
        this.clazz = clazz;
        this.method = method;
        this.httpMethod = httpMethod;
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

    public String getHttpMethod () {return  this.httpMethod;}
    public void setHttpMethod(String method) { this.httpMethod = method;}
}
