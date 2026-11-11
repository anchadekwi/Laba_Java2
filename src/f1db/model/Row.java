package f1db.model;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class Row implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<String, Comparable<?>> values = new LinkedHashMap<>();

    public void setValue(String columnName, Comparable<?> value) {
        values.put(columnName.toLowerCase(), value);
    }

    public Comparable<?> getValue(String columnName) {
        return values.get(columnName.toLowerCase());
    }

    public Map<String, Comparable<?>> getValues() {
        return values;
    }
}