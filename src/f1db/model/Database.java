package f1db.model;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

public class Database implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<String, Table> tables = new LinkedHashMap<>();

    public Map<String, Table> getTables() {
        return tables;
    }

    public Table getTable(String name) {
        Table t = tables.get(name.toLowerCase());
        if (t == null) {
            throw new IllegalArgumentException("Таблица '" + name + "' не найдена в базе данных");
        }
        return t;
    }
}