package f1db.index;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Индекс таблицы на основе красно-чёрного дерева (TreeMap).
 * Обеспечивает поиск за O(log N) как по точному совпадению (=), так и по диапазонам.
 */
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

    /**
     * Добавляет запись в индекс.
     *
     * @param columnValue значение индексируемого поля
     * @param primaryKey  первичный ключ строки
     */
    public void put(Comparable<?> columnValue, Comparable<?> primaryKey) {
        if (columnValue == null) return;
        tree.computeIfAbsent(columnValue, k -> new LinkedHashSet<>()).add(primaryKey);
    }

    /**
     * Удаляет запись из индекса.
     *
     * @param columnValue значение индексируемого поля
     * @param primaryKey  первичный ключ строки
     */
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

    /**
     * Выполняет поиск первичных ключей по условию с использованием дерева индекса.
     *
     * @param operator оператор сравнения (=, !=, >, <, >=, <=)
     * @param target   целевое значение для сравнения
     * @return множество найденных первичных ключей
     */
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