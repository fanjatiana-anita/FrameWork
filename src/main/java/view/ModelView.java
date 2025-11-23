package view;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModelView {
    private String view; 
    private Map<String, Object> data = new HashMap<>();

    public ModelView(String view) { this.view = view; }

    public ModelView() {}

    public String getView() { return view; }
    public void setView(String view) { this.view = view; }
    
    public void setData(String key, Object value) {
        this.data.put(key, value);
    }

    public void setData(String key, List<Object> values) {
        this.data.put(key, values);
    }

    public Map<String, Object> getData() {
        return this.data;
    }
}
