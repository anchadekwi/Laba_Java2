package f1db.index;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class TableIndex implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String indexName;
    private final String columnName;
    @SuppressWarnings("rawtypes")
    private final TreeMap<Comparable, Set<Comparable<?>>> tree = new TreeMap<>();

    public TableIndex(String indexName, String columnName) {
        this.indexName = indexName;
        this.columnName = columnName.toLowerCase();
    }

    public String getIndexName() { return indexName; }
    public String getColumnName() { return columnName; }

    public void put(Comparable<?> columnValue, Comparable<?> primaryKey) {
        if (columnValue == null) return;
        tree.computeIfAbsent(columnValue, k -> new LinkedHashSet<>()).add(primaryKey);
    }


    public void remove(Comparable<?> columnValue, Comparable<?> primaryKey) {
        if (columnValue == null) return;
        Set<Comparable<?>> pks = tree.get(columnValue);
        if (pks != null) {
            pks.remove(primaryKey);
            if (pks.isEmpty()) {
                tree.remove(columnValue);
            }
        }
    }

    @SuppressWarnings("rawtypes")
    public Set<Comparable<?>> search(String operator, Comparable<?> target) {
        Set<Comparable<?>> result = new LinkedHashSet<>();
        if (target == null) return result;

        switch (operator) {
            case "=":
                Set<Comparable<?>> exact = tree.get(target);
                if (exact != null) result.addAll(exact);
                break;
            case ">":
                for (Set<Comparable<?>> set : tree.tailMap(target, false).values()) {
                    result.addAll(set);
                }
                break;
            case ">=":
                for (Set<Comparable<?>> set : tree.tailMap(target, true).values()) {
                    result.addAll(set);
                }
                break;
            case "<":
                for (Set<Comparable<?>> set : tree.headMap(target, false).values()) {
                    result.addAll(set);
                }
                break;
            case "<=":
                for (Set<Comparable<?>> set : tree.headMap(target, true).values()) {
                    result.addAll(set);
                }
                break;
            case "!=":
                for (Map.Entry<Comparable, Set<Comparable<?>>> entry : tree.entrySet()) {
                    if (!entry.getKey().equals(target)) {
                        result.addAll(entry.getValue());
                    }
                }
                break;
            default:
                break;
        }
        return result;
    }
}