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

            if(name.isEmpty() || name == null) {
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
            // 3. Objet complexe
            if (!isSimpleType(type) && !type.isArray() && !Map.class.isAssignableFrom(type)) {
                args[i] = buildObjectFromParams(type, request.getParameterMap(), "");
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

    private static boolean isSimpleType(Class<?> type) {
        return type.isPrimitive() ||
               type.equals(String.class) ||
               type.equals(Integer.class) ||
               type.equals(Long.class) ||
               type.equals(Double.class) ||
               type.equals(Float.class) ||
               type.equals(Boolean.class) ||
               ConvertUtils.lookup(type) != null;
    }

    private static Object buildObjectFromParams(Class<?> clazz, Map<String, String[]> params, String prefix) {
        try {
            Object obj = clazz.getDeclaredConstructor().newInstance();

            for (var field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                Class<?> fieldType = field.getType();
                String fieldName = field.getName();
                String fullKey = prefix.isEmpty() ? fieldName : prefix + "." + fieldName;

                // Si Simple → mapping direct
                if (isSimpleType(fieldType)) {
                    String[] value = params.get(fullKey);
                    if (value != null) {
                        field.set(obj, ConvertUtils.convert(value[0], fieldType));
                    }
                }
                // Si c'est une List<...>
                else if (java.util.List.class.isAssignableFrom(fieldType)) {
                    Type genericType = field.getGenericType();

                    if (genericType instanceof ParameterizedType) {
                        ParameterizedType pt = (ParameterizedType) genericType;
                        Class<?> elementType = (Class<?>) pt.getActualTypeArguments()[0];

                        java.util.List<Object> list = new java.util.ArrayList<>();

                        int index = 0;
                        while (true) {
                            String indexPrefix = fullKey + "[" + index + "]";
                            boolean found = false;

                            Object nestedObj = elementType.getDeclaredConstructor().newInstance();

                            for (var subField : elementType.getDeclaredFields()) {
                                subField.setAccessible(true);
                                String subKey = indexPrefix + "." + subField.getName();
                                String[] values = params.get(subKey);

                                if (values != null) {
                                    found = true;
                                    if (isSimpleType(subField.getType())) {
                                        subField.set(nestedObj, ConvertUtils.convert(values[0], subField.getType()));
                                    }
                                }
                            }

                            if (!found) break;
                            list.add(nestedObj);
                            index++;
                        }

                        field.set(obj, list);
                    }
                }
                // Objet imbriqué → récursion
                else {
                    Object nestedObj = buildObjectFromParams(fieldType, params, fullKey);
                    field.set(obj, nestedObj);
                }
            }

            return obj;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to bind object : " + clazz.getSimpleName(), e);
        }
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