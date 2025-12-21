package utiles;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.*;

public class JsonUtils {

    public static String toJson(Object obj) {
        StringBuilder sb = new StringBuilder();
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        writeValue(obj, sb, seen);
        return sb.toString();
    }

    private static void writeValue(Object obj, StringBuilder sb, IdentityHashMap<Object, Boolean> seen) {
        if (obj == null) {
            sb.append("null");
            return;
        }

        // Primitifs / wrappers / String / Enum
        if (obj instanceof String) {
            sb.append('"').append(escape((String) obj)).append('"');
            return;
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            sb.append(obj.toString());
            return;
        }
        if (obj instanceof Character) {
            sb.append('"').append(escape(obj.toString())).append('"');
            return;
        }
        if (obj.getClass().isEnum()) {
            sb.append('"').append(((Enum<?>) obj).name()).append('"');
            return;
        }

        // Protection contre cycles
        if (seen.containsKey(obj)) {
            sb.append("null"); // ou "\"<circular>\"" ; on met null pour sécurité
            return;
        }
        seen.put(obj, Boolean.TRUE);

        // Map
        if (obj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<Object, Object> map = (Map<Object, Object>) obj;
            sb.append("{");
            boolean first = true;
            for (Map.Entry<Object, Object> e : map.entrySet()) {
                if (!first) sb.append(",");
                first = false;
                // clés converties en string
                String key = String.valueOf(e.getKey());
                sb.append('"').append(escape(key)).append("\":");
                writeValue(e.getValue(), sb, seen);
            }
            sb.append("}");
            return;
        }

        // Collection
        if (obj instanceof Collection) {
            Collection<?> col = (Collection<?>) obj;
            sb.append("[");
            boolean first = true;
            for (Object item : col) {
                if (!first) sb.append(",");
                first = false;
                writeValue(item, sb, seen);
            }
            sb.append("]");
            return;
        }

        // Array
        if (obj.getClass().isArray()) {
            int len = Array.getLength(obj);
            sb.append("[");
            for (int i = 0; i < len; i++) {
                if (i > 0) sb.append(",");
                writeValue(Array.get(obj, i), sb, seen);
            }
            sb.append("]");
            return;
        }

        // POJO via reflection : on parcourt les champs déclarés (non hérités aussi possible)
        sb.append("{");
        boolean first = true;
        Class<?> clazz = obj.getClass();
        // Inclure champs de la hiérarchie (superclasses) pour plus de robustesse
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            Field[] fields = c.getDeclaredFields();
            for (Field f : fields) {
                f.setAccessible(true);
                try {
                    Object val = f.get(obj);
                    if (!first) sb.append(",");
                    first = false;
                    sb.append('"').append(escape(f.getName())).append("\":");
                    writeValue(val, sb, seen);
                } catch (Exception ex) {
                    // en cas d'erreur d'accès, on met null
                    if (!first) sb.append(",");
                    first = false;
                    sb.append('"').append(escape(f.getName())).append("\":null");
                }
            }
        }
        sb.append("}");
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
            }
        }
        return out.toString();
    }
}
