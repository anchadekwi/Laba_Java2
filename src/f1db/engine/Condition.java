package f1db.engine;

import f1db.model.Row;


public class Condition {
    private final String columnName;
    private final String operator;
    private final Comparable<?> targetValue;

    public Condition(String columnName, String operator, Comparable<?> targetValue) {
        this.columnName = columnName;
        this.operator = operator;
        this.targetValue = targetValue;
    }

    public String getColumnName() { return columnName; }
    public String getOperator() { return operator; }
    public Comparable<?> getTargetValue() { return targetValue; }


    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean matches(Row row) {
        Comparable rowVal = row.getValue(columnName);
        if (rowVal == null || targetValue == null) {
            if (operator.equals("=")) return rowVal == targetValue;
            if (operator.equals("!=")) return rowVal != targetValue;
            return false;
        }
        int cmp = rowVal.compareTo(targetValue);
        switch (operator) {
            case "=":  return cmp == 0;
            case "!=": return cmp != 0;
            case ">":  return cmp > 0;
            case "<":  return cmp < 0;
            case ">=": return cmp >= 0;
            case "<=": return cmp <= 0;
            default:   return false;
        }
    }
}