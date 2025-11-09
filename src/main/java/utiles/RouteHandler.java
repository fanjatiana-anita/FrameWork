package utiles;

import java.lang.reflect.Method;

public class RouteHandler {

    private final Class<?> clazz;
    private final Method method;

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
}
