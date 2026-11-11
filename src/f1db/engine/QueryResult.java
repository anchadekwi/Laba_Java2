package f1db.engine;

import f1db.model.Row;

import java.util.Collections;
import java.util.List;

public class QueryResult {
    private final boolean selectQuery;
    private final List<String> columns;
    private final List<Row> rows;
    private final String statusMessage;

    public QueryResult(boolean selectQuery, List<String> columns, List<Row> rows, String statusMessage) {
        this.selectQuery = selectQuery;
        this.columns = columns != null ? columns : Collections.emptyList();
        this.rows = rows != null ? rows : Collections.emptyList();
        this.statusMessage = statusMessage;
    }

    public static QueryResult ofMessage(String message) {
        return new QueryResult(false, Collections.emptyList(), Collections.emptyList(), message);
    }

    public static QueryResult ofSelect(List<String> columns, List<Row> rows, String statusMessage) {
        return new QueryResult(true, columns, rows, statusMessage);
    }

    public boolean isSelectQuery() { return selectQuery; }
    public List<String> getColumns() { return columns; }
    public List<Row> getRows() { return rows; }
    public String getStatusMessage() { return statusMessage; }
}