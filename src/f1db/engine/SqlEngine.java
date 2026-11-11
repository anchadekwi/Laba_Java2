package f1db.engine;

import f1db.index.TableIndex;
import f1db.model.Column;
import f1db.model.DataType;
import f1db.model.Database;
import f1db.model.Row;
import f1db.model.Table;
import f1db.storage.StorageRepository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class SqlEngine {
    private final Database db;
    private final StorageRepository repository;
    private boolean autoSave = true;

    public SqlEngine(Database db, StorageRepository repository) {
        this.db = db;
        this.repository = repository;
    }

    public Database getDatabase() {
        return db;
    }

    public void setAutoSave(boolean autoSave) {
        this.autoSave = autoSave;
    }

    public void persistIfNeeded() {
        if (autoSave && repository != null) {
            repository.save(db);
        }
    }

    public String execute(String rawSql) {
        String sql = rawSql.trim();
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }
        if (sql.isEmpty()) return "";

        String upper = sql.toUpperCase();
        if (upper.startsWith("CREATE TABLE")) {
            return handleCreateTable(sql);
        } else if (upper.startsWith("DROP TABLE")) {
            return handleDropTable(sql);
        } else if (upper.startsWith("CREATE INDEX")) {
            return handleCreateIndex(sql);
        } else if (upper.startsWith("INSERT INTO")) {
            return handleInsert(sql);
        } else if (upper.startsWith("SELECT")) {
            return handleSelect(sql);
        } else if (upper.startsWith("UPDATE")) {
            return handleUpdate(sql);
        } else if (upper.startsWith("DELETE FROM")) {
            return handleDelete(sql);
        } else {
            throw new IllegalArgumentException("Неподдерживаемая команда SQL: " + rawSql);
        }
    }

    private String handleCreateTable(String sql) {
        Pattern p = Pattern.compile("(?i)^CREATE\\s+TABLE\\s+(\\w+)\\s*\\((.+)\\)$");
        Matcher m = p.matcher(sql);
        if (!m.matches()) {
            throw new IllegalArgumentException("Синтаксис: CREATE TABLE name (col1 TYPE [PK], col2 TYPE [FK refTable.refCol], ...)");
        }
        String tableName = m.group(1).toLowerCase();
        if (db.getTables().containsKey(tableName)) {
            throw new IllegalArgumentException("Таблица '" + tableName + "' уже существует");
        }

        String[] colDefs = splitOutsideQuotes(m.group(2), ',');
        List<Column> cols = new ArrayList<>();
        for (String def : colDefs) {
            String[] tokens = def.trim().split("\\s+");
            if (tokens.length < 2) {
                throw new IllegalArgumentException("Некорректное описание столбца: " + def);
            }
            String colName = tokens[0].toLowerCase();
            DataType type = DataType.valueOf(tokens[1].toUpperCase());
            boolean isPk = false;
            String fkTable = null;
            String fkCol = null;

            for (int i = 2; i < tokens.length; i++) {
                if (tokens[i].equalsIgnoreCase("PK") || tokens[i].equalsIgnoreCase("PRIMARY")) {
                    isPk = true;
                } else if (tokens[i].equalsIgnoreCase("FK") && i + 1 < tokens.length) {
                    String[] ref = tokens[++i].split("\\.");
                    if (ref.length != 2) {
                        throw new IllegalArgumentException("Внешний ключ должен иметь формат FK table.column");
                    }
                    fkTable = ref[0].toLowerCase();
                    fkCol = ref[1].toLowerCase();
                }
            }
            cols.add(new Column(colName, type, isPk, fkTable, fkCol));
        }

        Table table = new Table(tableName, cols);
        db.getTables().put(tableName, table);
        persistIfNeeded();
        return "Таблица '" + tableName + "' успешно создана (" + cols.size() + " столбцов).";
    }

    private String handleDropTable(String sql) {
        Pattern p = Pattern.compile("(?i)^DROP\\s+TABLE\\s+(\\w+)$");
        Matcher m = p.matcher(sql);
        if (!m.matches()) {
            throw new IllegalArgumentException("Синтаксис: DROP TABLE table_name");
        }
        String tableName = m.group(1).toLowerCase();
        if (!db.getTables().containsKey(tableName)) {
            throw new IllegalArgumentException("Таблица '" + tableName + "' не существует");
        }
        for (Table other : db.getTables().values()) {
            if (other.getName().equals(tableName)) continue;
            for (Column col : other.getColumns().values()) {
                if (tableName.equals(col.getForeignKeyTable())) {
                    throw new IllegalArgumentException("Невозможно удалить таблицу '" + tableName
                            + "', так как на неё ссылается таблица '" + other.getName() + "'");
                }
            }
        }
        db.getTables().remove(tableName);
        persistIfNeeded();
        return "Таблица '" + tableName + "' успешно удалена.";
    }

    private String handleCreateIndex(String sql) {
        Pattern p = Pattern.compile("(?i)^CREATE\\s+INDEX\\s+(\\w+)\\s+ON\\s+(\\w+)\\s*\\(\\s*(\\w+)\\s*\\)$");
        Matcher m = p.matcher(sql);
        if (!m.matches()) {
            throw new IllegalArgumentException("Синтаксис: CREATE INDEX index_name ON table_name (column_name)");
        }
        String indexName = m.group(1);
        String tableName = m.group(2).toLowerCase();
        String columnName = m.group(3).toLowerCase();

        Table table = db.getTable(tableName);
        table.createIndex(indexName, columnName);
        persistIfNeeded();
        return "Индекс '" + indexName + "' создан для " + tableName + "(" + columnName + ") и сохранён на диск.";
    }

    private String handleInsert(String sql) {
        Pattern p = Pattern.compile("(?i)^INSERT\\s+INTO\\s+(\\w+)\\s*(?:\\(([^)]+)\\))?\\s*VALUES\\s*(.+)$");
        Matcher m = p.matcher(sql);
        if (!m.matches()) {
            throw new IllegalArgumentException("Некорректный синтаксис INSERT INTO");
        }

        String tableName = m.group(1).toLowerCase();
        String colsPart = m.group(2);
        String valuesPart = m.group(3).trim();

        Table table = db.getTable(tableName);
        List<String> targetColumns = new ArrayList<>();
        if (colsPart != null && !colsPart.trim().isEmpty()) {
            for (String c : colsPart.split(",")) {
                String colName = c.trim().toLowerCase();
                if (!table.getColumns().containsKey(colName)) {
                    throw new IllegalArgumentException("Неизвестный столбец '" + colName + "' в таблице '" + tableName + "'");
                }
                targetColumns.add(colName);
            }
        } else {
            targetColumns.addAll(table.getColumns().keySet());
        }

        List<String> tupleStrings = extractValueTuples(valuesPart);
        if (tupleStrings.isEmpty()) {
            throw new IllegalArgumentException("Не найдены кортежи значений в секции VALUES");
        }

        List<Row> rowsToInsert = new ArrayList<>();
        Set<Comparable<?>> batchPks = new HashSet<>();

        for (String tuple : tupleStrings) {
            String[] rawValues = splitOutsideQuotes(tuple, ',');
            if (rawValues.length != targetColumns.size()) {
                throw new IllegalArgumentException("Количество значений (" + rawValues.length
                        + ") не совпадает с количеством столбцов (" + targetColumns.size() + ")");
            }

            Row row = new Row();
            for (String colName : table.getColumns().keySet()) {
                row.setValue(colName, null);
            }

            for (int i = 0; i < targetColumns.size(); i++) {
                String colName = targetColumns.get(i);
                Column colMeta = table.getColumns().get(colName);
                Comparable<?> parsedVal = colMeta.getType().parseValue(rawValues[i]);

                if (parsedVal != null && colMeta.getForeignKeyTable() != null) {
                    Table refTable = db.getTable(colMeta.getForeignKeyTable());
                    if (!refTable.getRowsByPk().containsKey(parsedVal)) {
                        throw new IllegalArgumentException("Нарушение ссылочной целостности (FK): значение "
                                + colName + " = " + parsedVal + " отсутствует в таблице '"
                                + refTable.getName() + "'");
                    }
                }
                row.setValue(colName, parsedVal);
            }

            Comparable<?> pkVal = row.getValue(table.getPrimaryKeyColumn());
            if (pkVal == null) {
                throw new IllegalArgumentException("Первичный ключ '" + table.getPrimaryKeyColumn() + "' должен быть задан");
            }
            if (table.getRowsByPk().containsKey(pkVal) || !batchPks.add(pkVal)) {
                throw new IllegalArgumentException("Нарушение уникальности первичного ключа: запись с "
                        + table.getPrimaryKeyColumn() + " = " + pkVal + " уже существует!");
            }
            rowsToInsert.add(row);
        }

        for (Row row : rowsToInsert) {
            table.insertRow(row);
        }
        persistIfNeeded();
        return "Вставлено записей в '" + tableName + "': " + rowsToInsert.size();
    }

    private String handleSelect(String sql) {
        Pattern p = Pattern.compile("(?i)^SELECT\\s+(.+?)\\s+FROM\\s+(\\w+)(?:\\s+WHERE\\s+(.+))?$");
        Matcher m = p.matcher(sql);
        if (!m.matches()) {
            throw new IllegalArgumentException("Синтаксис: SELECT * | col1, col2 FROM table [WHERE col op val]");
        }

        String selectPart = m.group(1).trim();
        String tableName = m.group(2).toLowerCase();
        String wherePart = m.group(3);

        Table table = db.getTable(tableName);
        List<String> displayColumns = new ArrayList<>();
        if (selectPart.equals("*")) {
            displayColumns.addAll(table.getColumns().keySet());
        } else {
            for (String c : selectPart.split(",")) {
                String colName = c.trim().toLowerCase();
                if (!table.getColumns().containsKey(colName)) {
                    throw new IllegalArgumentException("Столбец '" + colName + "' не найден в таблице '" + tableName + "'");
                }
                displayColumns.add(colName);
            }
        }

        long startNano = System.nanoTime();
        List<Row> matchedRows;
        String executionPlan;

        if (wherePart == null || wherePart.trim().isEmpty()) {
            matchedRows = new ArrayList<>();
            for (Row r : table) {
                matchedRows.add(r);
            }
            executionPlan = "FULL SCAN (без WHERE)";
        } else {
            Condition cond = parseCondition(table, wherePart);
            TableIndex idx = table.getIndexesByColumn().get(cond.getColumnName());
            if (idx != null) {
                Set<Comparable<?>> pks = idx.search(cond.getOperator(), cond.getTargetValue());
                matchedRows = new ArrayList<>(pks.size());
                for (Comparable<?> pk : pks) {
                    Row r = table.getRowsByPk().get(pk);
                    if (r != null) matchedRows.add(r);
                }
                executionPlan = "INDEX SCAN (индекс '" + idx.getIndexName() + "' по столбцу '" + cond.getColumnName() + "')";
            } else if (cond.getColumnName().equals(table.getPrimaryKeyColumn()) && cond.getOperator().equals("=")) {
                matchedRows = new ArrayList<>();
                Row r = table.getRowsByPk().get(cond.getTargetValue());
                if (r != null) matchedRows.add(r);
                executionPlan = "PRIMARY KEY LOOKUP";
            } else {
                matchedRows = new ArrayList<>();
                for (Row r : table) {
                    if (cond.matches(r)) {
                        matchedRows.add(r);
                    }
                }
                executionPlan = "FULL TABLE SCAN (последовательный обход)";
            }
        }
        double elapsedMs = (System.nanoTime() - startNano) / 1_000_000.0;

        return formatAsciiTable(displayColumns, matchedRows)
                + String.format(Locale.US, "\nНайдено строк: %d | План: %s | Время: %.3f мс",
                matchedRows.size(), executionPlan, elapsedMs);
    }

    private String handleUpdate(String sql) {
        Pattern p = Pattern.compile("(?i)^UPDATE\\s+(\\w+)\\s+SET\\s+(.+?)(?:\\s+WHERE\\s+(.+))?$");
        Matcher m = p.matcher(sql);
        if (!m.matches()) {
            throw new IllegalArgumentException("Синтаксис: UPDATE table SET col = val [WHERE col op val]");
        }

        String tableName = m.group(1).toLowerCase();
        String setPart = m.group(2).trim();
        String wherePart = m.group(3);

        Table table = db.getTable(tableName);
        Condition cond = (wherePart != null && !wherePart.trim().isEmpty())
                ? parseCondition(table, wherePart) : null;

        String[] assignments = splitOutsideQuotes(setPart, ',');
        Map<String, Comparable<?>> newValues = new LinkedHashMap<>();
        for (String assign : assignments) {
            String[] kv = assign.split("=", 2);
            if (kv.length != 2) {
                throw new IllegalArgumentException("Некорректное выражение в SET: " + assign);
            }
            String colName = kv[0].trim().toLowerCase();
            Column colMeta = table.getColumns().get(colName);
            if (colMeta == null) {
                throw new IllegalArgumentException("Столбец '" + colName + "' не существует в '" + tableName + "'");
            }
            Comparable<?> parsedVal = colMeta.getType().parseValue(kv[1]);
            if (parsedVal != null && colMeta.getForeignKeyTable() != null) {
                Table refTable = db.getTable(colMeta.getForeignKeyTable());
                if (!refTable.getRowsByPk().containsKey(parsedVal)) {
                    throw new IllegalArgumentException("Нарушение FK: значение " + parsedVal
                            + " отсутствует в таблице '" + refTable.getName() + "'");
                }
            }
            newValues.put(colName, parsedVal);
        }

        List<Comparable<?>> targetPks = new ArrayList<>();
        for (Row row : table) {
            if (cond == null || cond.matches(row)) {
                targetPks.add(row.getValue(table.getPrimaryKeyColumn()));
            }
        }

        for (Comparable<?> pk : targetPks) {
            for (Map.Entry<String, Comparable<?>> entry : newValues.entrySet()) {
                table.updateRowValue(pk, entry.getKey(), entry.getValue());
            }
        }

        persistIfNeeded();
        return "Обновлено записей в '" + tableName + "': " + targetPks.size();
    }

    private String handleDelete(String sql) {
        Pattern p = Pattern.compile("(?i)^DELETE\\s+FROM\\s+(\\w+)(?:\\s+WHERE\\s+(.+))?$");
        Matcher m = p.matcher(sql);
        if (!m.matches()) {
            throw new IllegalArgumentException("Синтаксис: DELETE FROM table [WHERE col op val]");
        }

        String tableName = m.group(1).toLowerCase();
        String wherePart = m.group(2);

        Table table = db.getTable(tableName);
        Condition cond = (wherePart != null && !wherePart.trim().isEmpty())
                ? parseCondition(table, wherePart) : null;

        List<Comparable<?>> pksToDelete = new ArrayList<>();
        for (Row row : table) {
            if (cond == null || cond.matches(row)) {
                pksToDelete.add(row.getValue(table.getPrimaryKeyColumn()));
            }
        }

        for (Table other : db.getTables().values()) {
            if (other.getName().equals(tableName)) continue;
            for (Column col : other.getColumns().values()) {
                if (tableName.equals(col.getForeignKeyTable())) {
                    for (Row childRow : other) {
                        Comparable<?> fkVal = childRow.getValue(col.getName());
                        if (fkVal != null && pksToDelete.contains(fkVal)) {
                            throw new IllegalArgumentException("Нарушение ссылочной целостности (FK)! "
                                    + "Нельзя удалить запись с ID = " + fkVal + " из '" + tableName
                                    + "', так как на неё ссылается таблица '" + other.getName() + "'");
                        }
                    }
                }
            }
        }

        for (Comparable<?> pk : pksToDelete) {
            table.deleteRow(pk);
        }
        persistIfNeeded();
        return "Удалено записей из '" + tableName + "': " + pksToDelete.size();
    }

    private Condition parseCondition(Table table, String whereClause) {
        Pattern p = Pattern.compile("^\\s*(\\w+)\\s*(>=|<=|!=|=|>|<)\\s*(.+?)\\s*$");
        Matcher m = p.matcher(whereClause);
        if (!m.matches()) {
            throw new IllegalArgumentException("Некорректное условие WHERE: " + whereClause);
        }
        String colName = m.group(1).toLowerCase();
        String op = m.group(2);
        String rawVal = m.group(3);

        Column colMeta = table.getColumns().get(colName);
        if (colMeta == null) {
            throw new IllegalArgumentException("Столбец '" + colName + "' не найден в таблице '" + table.getName() + "'");
        }
        Comparable<?> targetVal = colMeta.getType().parseValue(rawVal);
        return new Condition(colName, op, targetVal);
    }

    private static List<String> extractValueTuples(String valuesPart) {
        List<String> tuples = new ArrayList<>();
        boolean inQuotes = false;
        char quoteChar = 0;
        int depth = 0;
        int start = -1;

        for (int i = 0; i < valuesPart.length(); i++) {
            char c = valuesPart.charAt(i);
            if ((c == '\'' || c == '"')) {
                if (!inQuotes) {
                    inQuotes = true;
                    quoteChar = c;
                } else if (quoteChar == c) {
                    inQuotes = false;
                }
            } else if (!inQuotes) {
                if (c == '(') {
                    if (depth == 0) start = i + 1;
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0 && start != -1) {
                        tuples.add(valuesPart.substring(start, i));
                        start = -1;
                    }
                }
            }
        }
        return tuples;
    }

    private static String[] splitOutsideQuotes(String input, char delimiter) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        char quoteChar = 0;

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '\'' || c == '"') {
                if (!inQuotes) {
                    inQuotes = true;
                    quoteChar = c;
                } else if (quoteChar == c) {
                    inQuotes = false;
                }
                current.append(c);
            } else if (c == delimiter && !inQuotes) {
                tokens.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString().trim());
        }
        return tokens.toArray(new String[0]);
    }

    private static String formatAsciiTable(List<String> columns, List<Row> rows) {
        int[] widths = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            widths[i] = columns.get(i).length();
        }
        for (Row row : rows) {
            for (int i = 0; i < columns.size(); i++) {
                Comparable<?> val = row.getValue(columns.get(i));
                String str = val == null ? "NULL" : val.toString();
                widths[i] = Math.max(widths[i], str.length());
            }
        }

        StringBuilder sb = new StringBuilder();
        String border = buildBorder(widths);
        sb.append(border).append("\n|");
        for (int i = 0; i < columns.size(); i++) {
            sb.append(" ").append(padRight(columns.get(i).toUpperCase(), widths[i])).append(" |");
        }
        sb.append("\n").append(border);

        for (Row row : rows) {
            sb.append("\n|");
            for (int i = 0; i < columns.size(); i++) {
                Comparable<?> val = row.getValue(columns.get(i));
                String str = val == null ? "NULL" : val.toString();
                sb.append(" ").append(padRight(str, widths[i])).append(" |");
            }
        }
        sb.append("\n").append(border);
        return sb.toString();
    }

    private static String buildBorder(int[] widths) {
        StringBuilder sb = new StringBuilder("+");
        for (int w : widths) {
            for (int i = 0; i < w + 2; i++) sb.append("-");
            sb.append("+");
        }
        return sb.toString();
    }

    private static String padRight(String s, int n) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < n) sb.append(' ');
        return sb.toString();
    }
    /**
     * Выполняет SQL-запрос и возвращает структурированный объект QueryResult для GUI.
     *
     * @param rawSql строка SQL-запроса
     * @return результат с табличными данными (для SELECT) или статусом выполнения
     */
    public QueryResult executeForGui(String rawSql) {
        String sql = rawSql.trim();
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }
        if (sql.isEmpty()) {
            return QueryResult.ofMessage("Пустой запрос.");
        }

        if (sql.toUpperCase().startsWith("SELECT")) {
            return handleSelectStructured(sql);
        } else {
            String message = execute(rawSql);
            return QueryResult.ofMessage(message);
        }
    }

    private QueryResult handleSelectStructured(String sql) {
        Pattern p = Pattern.compile("(?i)^SELECT\\s+(.+?)\\s+FROM\\s+(\\w+)(?:\\s+WHERE\\s+(.+))?$");
        Matcher m = p.matcher(sql);
        if (!m.matches()) {
            throw new IllegalArgumentException("Синтаксис: SELECT * | col1, col2 FROM table [WHERE col op val]");
        }

        String selectPart = m.group(1).trim();
        String tableName = m.group(2).toLowerCase();
        String wherePart = m.group(3);

        Table table = db.getTable(tableName);
        List<String> displayColumns = new ArrayList<>();
        if (selectPart.equals("*")) {
            displayColumns.addAll(table.getColumns().keySet());
        } else {
            for (String c : selectPart.split(",")) {
                String colName = c.trim().toLowerCase();
                if (!table.getColumns().containsKey(colName)) {
                    throw new IllegalArgumentException("Столбец '" + colName + "' не найден в таблице '" + tableName + "'");
                }
                displayColumns.add(colName);
            }
        }

        long startNano = System.nanoTime();
        List<Row> matchedRows;
        String executionPlan;

        if (wherePart == null || wherePart.trim().isEmpty()) {
            matchedRows = new ArrayList<>();
            for (Row r : table) {
                matchedRows.add(r);
            }
            executionPlan = "FULL SCAN (без WHERE)";
        } else {
            Condition cond = parseCondition(table, wherePart);
            TableIndex idx = table.getIndexesByColumn().get(cond.getColumnName());
            if (idx != null) {
                Set<Comparable<?>> pks = idx.search(cond.getOperator(), cond.getTargetValue());
                matchedRows = new ArrayList<>(pks.size());
                for (Comparable<?> pk : pks) {
                    Row r = table.getRowsByPk().get(pk);
                    if (r != null) matchedRows.add(r);
                }
                executionPlan = "INDEX SCAN (" + idx.getIndexName() + ")";
            } else if (cond.getColumnName().equals(table.getPrimaryKeyColumn()) && cond.getOperator().equals("=")) {
                matchedRows = new ArrayList<>();
                Row r = table.getRowsByPk().get(cond.getTargetValue());
                if (r != null) matchedRows.add(r);
                executionPlan = "PRIMARY KEY LOOKUP";
            } else {
                matchedRows = new ArrayList<>();
                for (Row r : table) {
                    if (cond.matches(r)) {
                        matchedRows.add(r);
                    }
                }
                executionPlan = "FULL TABLE SCAN (последовательный обход)";
            }
        }
        double elapsedMs = (System.nanoTime() - startNano) / 1_000_000.0;
        String status = String.format(Locale.US,
                "Таблица: %s | Найдено строк: %d | План: %s | Время: %.3f мс",
                tableName, matchedRows.size(), executionPlan, elapsedMs);

        return QueryResult.ofSelect(displayColumns, matchedRows, status);
    }
    /**
     * Выполняет SQL-запрос и возвращает структурированный объект QueryResult для GUI.
     *
     * @param rawSql строка SQL-запроса
     * @return результат с табличными данными (для SELECT) или статусом выполнения
     */
    public QueryResult executeForGui(String rawSql) {
        String sql = rawSql.trim();
        if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length() - 1).trim();
        }
        if (sql.isEmpty()) {
            return QueryResult.ofMessage("Пустой запрос.");
        }

        if (sql.toUpperCase().startsWith("SELECT")) {
            return handleSelectStructured(sql);
        } else {
            String message = execute(rawSql);
            return QueryResult.ofMessage(message);
        }
    }

    private QueryResult handleSelectStructured(String sql) {
        Pattern p = Pattern.compile("(?i)^SELECT\\s+(.+?)\\s+FROM\\s+(\\w+)(?:\\s+WHERE\\s+(.+))?$");
        Matcher m = p.matcher(sql);
        if (!m.matches()) {
            throw new IllegalArgumentException("Синтаксис: SELECT * | col1, col2 FROM table [WHERE col op val]");
        }

        String selectPart = m.group(1).trim();
        String tableName = m.group(2).toLowerCase();
        String wherePart = m.group(3);

        Table table = db.getTable(tableName);
        List<String> displayColumns = new ArrayList<>();
        if (selectPart.equals("*")) {
            displayColumns.addAll(table.getColumns().keySet());
        } else {
            for (String c : selectPart.split(",")) {
                String colName = c.trim().toLowerCase();
                if (!table.getColumns().containsKey(colName)) {
                    throw new IllegalArgumentException("Столбец '" + colName + "' не найден в таблице '" + tableName + "'");
                }
                displayColumns.add(colName);
            }
        }

        long startNano = System.nanoTime();
        List<Row> matchedRows;
        String executionPlan;

        if (wherePart == null || wherePart.trim().isEmpty()) {
            matchedRows = new ArrayList<>();
            for (Row r : table) {
                matchedRows.add(r);
            }
            executionPlan = "FULL SCAN (без WHERE)";
        } else {
            Condition cond = parseCondition(table, wherePart);
            TableIndex idx = table.getIndexesByColumn().get(cond.getColumnName());
            if (idx != null) {
                Set<Comparable<?>> pks = idx.search(cond.getOperator(), cond.getTargetValue());
                matchedRows = new ArrayList<>(pks.size());
                for (Comparable<?> pk : pks) {
                    Row r = table.getRowsByPk().get(pk);
                    if (r != null) matchedRows.add(r);
                }
                executionPlan = "INDEX SCAN (" + idx.getIndexName() + ")";
            } else if (cond.getColumnName().equals(table.getPrimaryKeyColumn()) && cond.getOperator().equals("=")) {
                matchedRows = new ArrayList<>();
                Row r = table.getRowsByPk().get(cond.getTargetValue());
                if (r != null) matchedRows.add(r);
                executionPlan = "PRIMARY KEY LOOKUP";
            } else {
                matchedRows = new ArrayList<>();
                for (Row r : table) {
                    if (cond.matches(r)) {
                        matchedRows.add(r);
                    }
                }
                executionPlan = "FULL TABLE SCAN (последовательный обход)";
            }
        }
        double elapsedMs = (System.nanoTime() - startNano) / 1_000_000.0;
        String status = String.format(Locale.US,
                "Таблица: %s | Найдено строк: %d | План: %s | Время: %.3f мс",
                tableName, matchedRows.size(), executionPlan, elapsedMs);

        return QueryResult.ofSelect(displayColumns, matchedRows, status);
    }
}