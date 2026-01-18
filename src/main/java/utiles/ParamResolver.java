package utiles;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;  // ← AJOUTÉ ICI
import jakarta.servlet.http.Part;
import jakarta.servlet.ServletException;
import method_annotations.RequestParam;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.io.IOException;
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

                if (name == null || name.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Missing required parameter name for argument: " + param.getName());
                }

                // === Gestion des Map ===
                if (Map.class.isAssignableFrom(type)) {
                    Type genericType = parameters[i].getParameterizedType();
                    if (genericType instanceof ParameterizedType) {
                        ParameterizedType paramType = (ParameterizedType) genericType;
                        Type[] typeArgs = paramType.getActualTypeArguments();
                        if (typeArgs.length == 2 && typeArgs[0].equals(String.class)) {
                            Type valueType = typeArgs[1];

                            if (valueType.equals(Object.class)) {
                                args[i] = allParams;
                                continue;
                            }

                            if (isFileMapType(valueType)) {
                                args[i] = buildFilesMap(request, valueType);
                                continue;
                            }
                        }
                    }
                    throw new IllegalArgumentException(
                            "Unsupported Map type for parameter '" + param.getName()
                                    + "'. Only Map<String,Object>, Map<String,byte[]>, or Map<String,List<byte[]>> are allowed.");
                }

                // === SUPPORT POUR HttpServletRequest (INJECTION AUTOMATIQUE) ===
                if (HttpServletRequest.class.isAssignableFrom(type)) {
                    args[i] = request;
                    continue;
                }

                // Path variable {id}
                String pathValue = handler.getPathVariable(name);
                if (pathValue != null) {
                    args[i] = ConvertUtils.convert(pathValue.trim(), type);
                    continue;
                }

                // Objet complexe (exclure les types servlet spéciaux)
                if (!isSimpleType(type)
                        && !type.isArray()
                        && !Map.class.isAssignableFrom(type)
                        && !HttpServletRequest.class.isAssignableFrom(type)) {
                    args[i] = buildObjectFromParams(type, request.getParameterMap(), "");
                    continue;
                }

                // Paramètre simple
                String[] values = request.getParameterValues(name);
                if (values == null || values.length == 0) {
                    args[i] = null;
                    continue;
                }

                try {
                    if (type.isArray() && type.getComponentType() == String.class) {
                        args[i] = values;
                    } else {
                        args[i] = ConvertUtils.convert(values[0].trim(), type);
                    }
                } catch (Exception e) {
                    throw new IllegalArgumentException(
                            "Failed to convert parameter '" + name + "' to " + type.getSimpleName());
                }
            }
        } catch (IOException | ServletException e) {
            throw new IllegalArgumentException("Erreur lors de la lecture des fichiers uploadés : " + e.getMessage(), e);
        } catch (Exception e) {
            System.err.println("ParamResolver ERROR: " + e.getMessage());
            throw new IllegalArgumentException("Erreur lors de la résolution des paramètres : " + e.getMessage(), e);
        }

        return args;
    }
    
    private static boolean isSpecialServletType(Class<?> type) {
        return HttpServletRequest.class.isAssignableFrom(type)
                || HttpServletResponse.class.isAssignableFrom(type);
    }

    private static boolean isFileMapType(Type valueType) {
        if (valueType.equals(byte[].class)) {
            return true;
        }
        if (valueType instanceof ParameterizedType pt) {
            return pt.getRawType().equals(List.class)
                    && pt.getActualTypeArguments().length == 1
                    && pt.getActualTypeArguments()[0].equals(byte[].class);
        }
        return false;
    }

    private static Map<String, ?> buildFilesMap(HttpServletRequest request, Type valueType)
            throws IOException, ServletException {
        Map<String, List<byte[]>> multiMap = new HashMap<>();

        if (request.getContentType() != null && request.getContentType().startsWith("multipart/form-data")) {
            for (Part part : request.getParts()) {
                String fileName = part.getSubmittedFileName();
                if (fileName != null && !fileName.trim().isEmpty() && part.getSize() > 0) {
                    byte[] bytes = part.getInputStream().readAllBytes();
                    if (bytes.length > 0) {
                        String fieldName = part.getName();
                        multiMap.computeIfAbsent(fieldName, k -> new ArrayList<>()).add(bytes);
                    }
                }
            }
        }

        if (valueType.equals(byte[].class)) {
            Map<String, byte[]> singleMap = new HashMap<>();
            for (Map.Entry<String, List<byte[]>> entry : multiMap.entrySet()) {
                List<byte[]> list = entry.getValue();
                if (list.size() > 1) {
                    throw new IllegalArgumentException(
                            "Multiple files for field '" + entry.getKey() + "', but single byte[] expected.");
                }
                if (!list.isEmpty()) {
                    singleMap.put(entry.getKey(), list.get(0));
                }
            }
            return singleMap;
        } else {
            return multiMap;
        }
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

                if (isSimpleType(fieldType)) {
                    String[] value = params.get(fullKey);
                    if (value != null && value.length > 0) {
                        field.set(obj, ConvertUtils.convert(value[0], fieldType));
                    }
                } else if (java.util.List.class.isAssignableFrom(fieldType)) {
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
                                if (values != null && values.length > 0) {
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
                } else {
                    Object nestedObj = buildObjectFromParams(fieldType, params, fullKey);
                    field.set(obj, nestedObj);
                }
            }
            return obj;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to bind object : " + clazz.getSimpleName(), e);
        }
    }

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
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }
}