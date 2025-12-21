package utiles;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class JsonResponse {

    private final HttpServletResponse resp;

    public JsonResponse(HttpServletResponse resp) {
        this.resp = resp;
    }

    public void sendJsonError(int code, String message) throws IOException {
        resp.setStatus(code);
        resp.setContentType("application/json;charset=UTF-8");

        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("status", "error");
        wrapper.put("code", code);
        wrapper.put("message", message);

        resp.getWriter().print(JsonUtils.toJson(wrapper));
        resp.getWriter().flush();
    }

    public void sendJsonSuccess(Object data) throws IOException {
        resp.setStatus(200);
        resp.setContentType("application/json;charset=UTF-8");

        Map<String, Object> wrapper = new LinkedHashMap<>();
        wrapper.put("status", "success");
        wrapper.put("code", 200);
        wrapper.put("data", data);

        resp.getWriter().print(JsonUtils.toJson(wrapper));
        resp.getWriter().flush();
    }
}
