package utiles;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.apache.commons.beanutils.ConvertUtils;

public class ParamResolver {

    public static Object[] resolveArguments(Method method, HttpServletRequest request) throws IllegalArgumentException {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            String paramName = param.getName();
            String stringValue = request.getParameter(paramName);

            if (stringValue == null || stringValue.isBlank()) {
                throw new IllegalArgumentException(
                    "Undefined parameter : '" + paramName + "' (method: " + method.getName() + ")"
                );
            } else {
                try {
                    args[i] = ConvertUtils.convert(stringValue.trim(), param.getType());
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                        "Conversion failed'" + paramName + "' with the value : '" + stringValue + "' at " + param.getType().getSimpleName()
                    );
                }
            }
        }
        return args;
    }

}