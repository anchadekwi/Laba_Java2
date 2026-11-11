package f1db.model;

import java.io.Serializable;

public class Column implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String name;
    private final DataType type;
    private final boolean primaryKey;
    private final String foreignKeyTable;
    private final String foreignKeyColumn;

    public Column(String name, DataType type, boolean primaryKey, String foreignKeyTable, String foreignKeyColumn) {
        this.name = name.toLowerCase();
        this.type = type;
        this.primaryKey = primaryKey;
        this.foreignKeyTable = foreignKeyTable != null ? foreignKeyTable.toLowerCase() : null;
        this.foreignKeyColumn = foreignKeyColumn != null ? foreignKeyColumn.toLowerCase() : null;
    }

    public String getName() { return name; }
    public DataType getType() { return type; }
    public boolean isPrimaryKey() { return primaryKey; }
    public String getForeignKeyTable() { return foreignKeyTable; }
    public String getForeignKeyColumn() { return foreignKeyColumn; }
}