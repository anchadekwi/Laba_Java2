package f1db;

import f1db.engine.SqlEngine;
import f1db.model.Database;
import f1db.model.Row;
import f1db.model.Table;
import f1db.storage.StorageRepository;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Random;
import java.util.Scanner;
import java.util.stream.Collectors;


public class F1DatabaseApp {

    /**
     * Заполняет БД стартовыми таблицами Формулы-1 (teams и drivers), если БД создаётся впервые.
     */
    private static void initializeDefaultF1Data(SqlEngine engine) {
        System.out.println("Инициализация стартовой базы данных 'Formula 1 Championship'...");
        engine.execute("CREATE TABLE teams (id INT PK, name STRING, budget FLOAT, founded DATE, active BOOL);");
        engine.execute("CREATE TABLE drivers (id INT PK, name STRING, team_id INT FK teams.id, points FLOAT, birth_date DATE, champion BOOL);");

        engine.execute("INSERT INTO teams VALUES "
                + "(1, 'Ferrari', 135.0, '1950-05-21', TRUE), "
                + "(2, 'Red Bull Racing', 135.0, '2005-03-06', TRUE), "
                + "(3, 'McLaren', 133.5, '1966-05-22', TRUE), "
                + "(4, 'Mercedes', 134.8, '1954-07-04', TRUE), "
                + "(5, 'Brawn GP', 65.0, '2009-03-29', FALSE);");

        engine.execute("INSERT INTO drivers VALUES "
                + "(1, 'Max Verstappen', 2, 393.5, '1997-09-30', TRUE), "
                + "(16, 'Charles Leclerc', 1, 307.0, '1997-10-16', FALSE), "
                + "(4, 'Lando Norris', 3, 331.0, '1999-11-13', FALSE), "
                + "(44, 'Lewis Hamilton', 1, 4862.5, '1985-01-07', TRUE), "
                + "(81, 'Oscar Piastri', 3, 262.0, '2001-04-06', FALSE), "
                + "(63, 'George Russell', 4, 192.0, '1998-02-15', FALSE);");

        engine.execute("CREATE INDEX idx_teams_name ON teams (name);");
        engine.execute("CREATE INDEX idx_drivers_points ON drivers (points);");
    }


    private static void runBenchmark(SqlEngine engine) {
        System.out.println("\n=== ЗАПУСК ТЕСТИРОВАНИЯ ПРОИЗВОДИТЕЛЬНОСТИ (50 000 ЗАПИСЕЙ) ===");
        engine.setAutoSave(false);
        try {
            if (engine.getDatabase().getTables().containsKey("telemetry")) {
                engine.execute("DROP TABLE telemetry;");
            }
            engine.execute("CREATE TABLE telemetry (id INT PK, driver_code STRING, speed FLOAT, lap_date DATE, drs_open BOOL);");

            int totalRows = 50_000;
            Table telemetry = engine.getDatabase().getTable("telemetry");
            Random rnd = new Random(42);
            String[] codes = {"VER", "LEC", "NOR", "HAM", "PIA", "RUS", "SAI", "ALO"};

            long t0 = System.nanoTime();
            for (int i = 1; i <= totalRows; i++) {
                Row r = new Row();
                r.setValue("id", i);
                r.setValue("driver_code", i == 42_500 ? "TARGET_LAP" : codes[rnd.nextInt(codes.length)]);
                r.setValue("speed", 250.0 + rnd.nextDouble() * 100.0);
                r.setValue("lap_date", LocalDate.of(2026, 5, 24));
                r.setValue("drs_open", rnd.nextBoolean());
                telemetry.insertRow(r);
            }
            double insertMs = (System.nanoTime() - t0) / 1_000_000.0;
            System.out.printf(Locale.US, "1. Сгенерировано %d записей за %.2f мс%n", totalRows, insertMs);

            long startNoIdx = System.nanoTime();
            int iterations = 100;
            for (int i = 0; i < iterations; i++) {
                engine.execute("SELECT * FROM telemetry WHERE driver_code = 'TARGET_LAP';");
            }
            double avgNoIdxMs = ((System.nanoTime() - startNoIdx) / 1_000_000.0) / iterations;

            long idxBuildStart = System.nanoTime();
            engine.execute("CREATE INDEX idx_telemetry_code ON telemetry (driver_code);");
            double idxBuildMs = (System.nanoTime() - idxBuildStart) / 1_000_000.0;

            long startWithIdx = System.nanoTime();
            for (int i = 0; i < iterations; i++) {
                engine.execute("SELECT * FROM telemetry WHERE driver_code = 'TARGET_LAP';");
            }
            double avgWithIdxMs = ((System.nanoTime() - startWithIdx) / 1_000_000.0) / iterations;

            System.out.println("\n+----------------------------------------+-------------------+");
            System.out.println("| Режим выполнения запроса (50 000 строк)| Среднее время, мс |");
            System.out.println("+----------------------------------------+-------------------+");
            System.out.printf(Locale.US, "| БЕЗ индекса (Full Table Scan)          | %14.4f мс |%n", avgNoIdxMs);
            System.out.printf(Locale.US, "| С индексом (TreeMap Index Scan)        | %14.4f мс |%n", avgWithIdxMs);
            System.out.println("+----------------------------------------+-------------------+");
            System.out.printf(Locale.US, "Построение индекса: %.2f мс | Ускорение: в %.1f раз(а)!%n",
                    idxBuildMs, avgNoIdxMs / Math.max(avgWithIdxMs, 0.0001));

            engine.execute("DROP TABLE telemetry;");
        } finally {
            engine.setAutoSave(true);
            engine.persistIfNeeded();
        }
    }

    private static void printHelp() {
        System.out.println("\n=== СПРАВКА ПО КОМАНДАМ FORMULA 1 DBMS ===");
        System.out.println("  TABLES      - Показать список всех таблиц, их структуру и индексы");
        System.out.println("  BENCHMARK   - Запустить тест производительности (Full Scan vs Index)");
        System.out.println("  HELP        - Показать эту справку");
        System.out.println("  EXIT        - Сохранить данные и выйти");
        System.out.println("\nПримеры поддерживаемых SQL-запросов:");
        System.out.println("  SELECT * FROM teams;");
        System.out.println("  SELECT * FROM drivers;");
        System.out.println("  SELECT name, points FROM drivers WHERE points > 300;");
        System.out.println("  INSERT INTO drivers VALUES (55, 'Carlos Sainz', 1, 290.0, '1994-09-01', FALSE);");
        System.out.println("  INSERT INTO teams (id, name) VALUES (6, 'Aston Martin');");
        System.out.println("  UPDATE drivers SET points = 410.5 WHERE id = 1;");
        System.out.println("  DELETE FROM teams WHERE id = 5;");
        System.out.println("  CREATE INDEX idx_drivers_team ON drivers (team_id);");
        System.out.println("  CREATE TABLE cars (id INT PK, brand STRING, price FLOAT, active BOOL);");
        System.out.println("  DROP TABLE cars;\n");
    }

    private static void printSchema(Database db) {
        if (db.getTables().isEmpty()) {
            System.out.println("В базе данных нет таблиц.");
            return;
        }
        for (Table t : db.getTables().values()) {
            String cols = t.getColumns().values().stream()
                    .map(c -> c.getName() + ":" + c.getType()
                            + (c.isPrimaryKey() ? "[PK]" : "")
                            + (c.getForeignKeyTable() != null ? "[FK->" + c.getForeignKeyTable() + "." + c.getForeignKeyColumn() + "]" : ""))
                    .collect(Collectors.joining(", "));
            String idxs = t.getIndexesByColumn().values().stream()
                    .map(i -> i.getIndexName() + "(" + i.getColumnName() + ")")
                    .collect(Collectors.joining(", "));
            System.out.printf("Таблица %-10s (строк: %d) | Столбцы: %s | Индексы: %s%n",
                    t.getName(), t.getRowsByPk().size(), cols, idxs.isEmpty() ? "нет" : idxs);
        }
    }


    public static void main(String[] args) {
        StorageRepository repository = new StorageRepository("f1_database.db");
        Database db = repository.load();
        SqlEngine engine;

        if (db == null) {
            db = new Database();
            engine = new SqlEngine(db, repository);
            initializeDefaultF1Data(engine);
        } else {
            engine = new SqlEngine(db, repository);
            System.out.println("База данных успешно загружена с диска (файл f1_database.db).");
        }

        System.out.println("   Добро пожаловать в СУБД Formula 1 (Java DBMS v1.0)     ");
        System.out.println("   Введите SQL-запрос или команду (HELP, TABLES, BENCHMARK, EXIT)");
        printSchema(db);

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("\nF1-SQL> ");
                if (!scanner.hasNextLine()) break;
                String input = scanner.nextLine().trim();
                if (input.isEmpty()) continue;

                if (input.equalsIgnoreCase("EXIT") || input.equalsIgnoreCase("QUIT")) {
                    repository.save(db);
                    System.out.println("База данных сохранена. До свидания!");
                    break;
                } else if (input.equalsIgnoreCase("HELP")) {
                    printHelp();
                } else if (input.equalsIgnoreCase("TABLES")) {
                    printSchema(db);
                } else if (input.equalsIgnoreCase("BENCHMARK")) {
                    runBenchmark(engine);
                } else {
                    try {
                        String output = engine.execute(input);
                        System.out.println(output);
                    } catch (Exception e) {
                        System.out.println("[ОШИБКА] " + e.getMessage());
                    }
                }
            }
        }
    }
}