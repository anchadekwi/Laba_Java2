package f1db.model;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

public enum DataType {
    INT, FLOAT, STRING, DATE, BOOL;
    public Comparable<?> parseValue(String rawValue) {
        if (rawValue == null) return null;
        String trimmed = rawValue.trim();
        if (trimmed.equalsIgnoreCase("NULL")) return null;

        switch (this) {
            case INT:
                try {
                    return Integer.parseInt(trimmed);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Ожидалось целое число (INT), получено: " + trimmed);
                }
            case FLOAT:
                try {
                    return Double.parseDouble(trimmed);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Ожидалось число с плавающей точкой (FLOAT), получено: " + trimmed);
                }
            case STRING:
                if ((trimmed.startsWith("'") && trimmed.endsWith("'")) ||
                        (trimmed.startsWith("\"") && trimmed.endsWith("\""))) {
                    return trimmed.substring(1, trimmed.length() - 1);
                }
                return trimmed;
            case DATE:
                String dateStr = trimmed;
                if ((dateStr.startsWith("'") && dateStr.endsWith("'")) ||
                        (dateStr.startsWith("\"") && dateStr.endsWith("\""))) {
                    dateStr = dateStr.substring(1, dateStr.length() - 1);
                }
                try {
                    return LocalDate.parse(dateStr);
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("Ожидалась дата в формате 'YYYY-MM-DD', получено: " + trimmed);
                }
            case BOOL:
                if (trimmed.equalsIgnoreCase("TRUE")) return Boolean.TRUE;
                if (trimmed.equalsIgnoreCase("FALSE")) return Boolean.FALSE;
                throw new IllegalArgumentException("Ожидалось логическое значение (TRUE/FALSE), получено: " + trimmed);
            default:
                throw new IllegalStateException("Неизвестный тип данных: " + this);
        }
    }
}