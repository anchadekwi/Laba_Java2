package f1db.model;

import f1db.index.TableIndex;

import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Table implements Serializable, Iterable<Row> {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final Map<String, Column> columns = new LinkedHashMap<>();
    private final String primaryKeyColumn;
    private final Map<Comparable<?>, Row> rowsByPk = new LinkedHashMap<>();
    private final Map<String, TableIndex> indexesByColumn = new LinkedHashMap<>();

    public Table(String name, List<Column> columnList) {
        this.name = name.toLowerCase();
        String pk = null;
        for (Column col : columnList) {
            columns.put(col.getName(), col);
            if (col.isPrimaryKey()) {
                pk = col.getName();
            }
        }
        if (pk == null && !columnList.isEmpty()) {
            Column first = columnList.get(0);
            Column promoted = new Column(first.getName(), first.getType(), true,
                    first.getForeignKeyTable(), first.getForeignKeyColumn());
            columns.put(promoted.getName(), promoted);
            pk = promoted.getName();
        }
        this.primaryKeyColumn = pk;
    }

    public String getName() { return name; }
    public Map<String, Column> getColumns() { return columns; }
    public String getPrimaryKeyColumn() { return primaryKeyColumn; }
    public Map<Comparable<?>, Row> getRowsByPk() { return rowsByPk; }
    public Map<String, TableIndex> getIndexesByColumn() { return indexesByColumn; }

    public void createIndex(String indexName, String columnName) {
        String colKey = columnName.toLowerCase();
        if (!columns.containsKey(colKey)) {
            throw new IllegalArgumentException("Столбец '" + columnName + "' не существует в таблице '" + name + "'");
        }
        TableIndex index = new TableIndex(indexName, colKey);
        for (Map.Entry<Comparable<?>, Row> entry : rowsByPk.entrySet()) {
            index.put(entry.getValue().getValue(colKey), entry.getKey());
        }
        indexesByColumn.put(colKey, index);
    }

    public void insertRow(Row row) {
        Comparable<?> pkVal = row.getValue(primaryKeyColumn);
        if (pkVal == null) {
            throw new IllegalArgumentException("Первичный ключ '" + primaryKeyColumn + "' не может быть NULL");
        }
        if (rowsByPk.containsKey(pkVal)) {
            throw new IllegalArgumentException("Нарушение уникальности первичного ключа! Запись с "
                    + primaryKeyColumn + " = " + pkVal + " уже существует в таблице '" + name + "'");
        }
        rowsByPk.put(pkVal, row);
        for (TableIndex index : indexesByColumn.values()) {
            index.put(row.getValue(index.getColumnName()), pkVal);
        }
    }

    public void updateRowValue(Comparable<?> pkVal, String columnName, Comparable<?> newValue) {
        String colKey = columnName.toLowerCase();
        Row row = rowsByPk.get(pkVal);
        if (row == null) return;

        if (colKey.equals(primaryKeyColumn) && !Objects.equals(pkVal, newValue)) {
            if (rowsByPk.containsKey(newValue)) {
                throw new IllegalArgumentException("Нарушение уникальности PK при UPDATE: " + newValue + " уже занят");
            }
            for (TableIndex idx : indexesByColumn.values()) {
                idx.remove(row.getValue(idx.getColumnName()), pkVal);
            }
            rowsByPk.remove(pkVal);
            row.setValue(colKey, newValue);
            rowsByPk.put(newValue, row);
            for (TableIndex idx : indexesByColumn.values()) {
                idx.put(row.getValue(idx.getColumnName()), newValue);
            }
        } else {
            TableIndex idx = indexesByColumn.get(colKey);
            if (idx != null) {
                idx.remove(row.getValue(colKey), pkVal);
                idx.put(newValue, pkVal);
            }
            row.setValue(colKey, newValue);
        }
    }

    public void deleteRow(Comparable<?> pkVal) {
        Row removed = rowsByPk.remove(pkVal);
        if (removed != null) {
            for (TableIndex index : indexesByColumn.values()) {
                index.remove(removed.getValue(index.getColumnName()), pkVal);
            }
        }
    }

    @Override
    public Iterator<Row> iterator() {
        return new TableRowIterator(rowsByPk.values());
    }
}