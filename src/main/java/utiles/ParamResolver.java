package utiles;

import jakarta.servlet.http.HttpServletRequest;
import method_annotations.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.beanutils.ConvertUtils;

public class ParamResolver {

    public static Object[] resolveArguments(HttpServletRequest request, RouteHandler handler) {
        Method method = handler.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];

        Map<String, Object> allParams = buildAllParamsMap(request);
        try {

            for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            Class<?> type = param.getType();
            String name = getParamName(param);

            if(name == null || name.isEmpty()) {
                throw new IllegalArgumentException(
                    "Missing required parameter name for argument: " + param.getName()
                );      
            }

            // 1. Map<String, Object> → on donne tout (même vide)
            if (Map.class.isAssignableFrom(type)) {
                Type genericType = parameters[i].getParameterizedType();

                // Vérifier si c'est exactement Map<String,Object>
                if (genericType instanceof ParameterizedType) {
                    ParameterizedType paramType = (ParameterizedType) genericType;
                    Type[] typeArgs = paramType.getActualTypeArguments();

                    if (typeArgs.length == 2 
                        && typeArgs[0].equals(String.class) 
                        && typeArgs[1].equals(Object.class)) {
                        args[i] = allParams;
                        continue;
                    } else {
                        // Si c’est une Map mais pas Map<String,Object> → exception
                        throw new IllegalArgumentException(
                            "Unsupported Map type for parameter '" 
                            + param.getName() + "'. Only Map<String,Object> is allowed."
                        );
                    }
                } else {
                    // Si c’est Map sans générique → aussi rejeté
                    throw new IllegalArgumentException(
                        "Unsupported Map type for parameter '" 
                        + param.getName() + "'. Only Map<String,Object> is allowed."
                    );
                }
            }


            // 2. Path variable {id}
            String pathValue = handler.getPathVariable(name);
            if (pathValue != null) {
                args[i] = ConvertUtils.convert(pathValue.trim(), type); 
                continue;
            }
            
            // 3. Paramètre normal (GET ou POST)
            String[] values = request.getParameterValues(name);

            if (values == null || values.length == 0) {
                args[i] = null;  // TU AS TON NULL ICI SI LE PARAMÈTRE N'EST PAS REMPLI
                continue;
            }

            // Checkbox → String[]
            try {
                // Checkbox → liste
                if (type.isArray() && type.getComponentType() == String.class) {
                    args[i] = values;
                } else {
                    args[i] = ConvertUtils.convert(values[0].trim(), type);
                }
            } catch (Exception e) {
                throw new IllegalArgumentException(
                    "Failed to convert parameter '" + name + "' into type " + type.getSimpleName()
                );
            }
        }
            
        }  catch (Exception e) {
            System.err.println("ParamResolver ERROR: " + e.getMessage());
            throw e; // On relance pour que l'appelant puisse gérer correctement
        }

        return args;
    }

    // Récupère le nom du paramètre (@RequestParam("xxx") ou nom de variable)
    private static String getParamName(Parameter param) {
        RequestParam rp = param.getAnnotation(RequestParam.class);
        if (rp != null && !rp.value().isEmpty()) {
            return rp.value();
        }
        return param.getName();
    }

    private static Map<String, Object> buildAllParamsMap(HttpServletRequest req) {
        Map<String, Object> map = new HashMap<>();
        Map<String, String[]> rawMap = req.getParameterMap();

        for (Map.Entry<String, String[]> entry : rawMap.entrySet()) {
            String key = entry.getKey();
            String[] values = entry.getValue();

            map.put(key, values);                      
        }

        return map;
    }

}