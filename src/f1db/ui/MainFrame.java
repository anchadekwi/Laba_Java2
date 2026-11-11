package f1db.ui;

import f1db.engine.QueryResult;
import f1db.engine.SqlEngine;
import f1db.index.TableIndex;
import f1db.model.Column;
import f1db.model.Row;
import f1db.model.Table;
import f1db.storage.StorageRepository;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Random;


public class MainFrame extends JFrame {
    private static final Color BG_MAIN = new Color(248, 250, 252);
    private static final Color BG_PANEL = Color.WHITE;
    private static final Color BORDER_COLOR = new Color(226, 232, 240);
    private static final Color ACCENT_BLUE = new Color(37, 99, 235);
    private static final Color TEXT_DARK = new Color(30, 41, 59);
    private static final Color STATUS_OK = new Color(22, 101, 52);
    private static final Color STATUS_ERR = new Color(185, 28, 28);

    private static final Font FONT_UI = new Font("SansSerif", Font.PLAIN, 13);
    private static final Font FONT_BOLD = new Font("SansSerif", Font.BOLD, 13);
    private static final Font FONT_MONO = new Font("Monospaced", Font.PLAIN, 13);

    private final SqlEngine engine;
    private final StorageRepository repository;

    private final DefaultListModel<String> tableListModel = new DefaultListModel<>();
    private final JList<String> tableList = new JList<>(tableListModel);
    private final JTextArea schemaInfoArea = new JTextArea();

    private final JTextArea sqlEditor = new JTextArea(3, 60);
    private final DefaultTableModel resultTableModel = new DefaultTableModel() {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable resultTable = new JTable(resultTableModel);
    private final JLabel statusLabel = new JLabel(" Готово к работе");
    private final JTextArea logArea = new JTextArea(4, 60);
    private JButton benchmarkBtn;

    public MainFrame(SqlEngine engine, StorageRepository repository) {
        super("Formula 1 DBMS — Графическая консоль управления БД");
        this.engine = engine;
        this.repository = repository;

        configureWindow();
        initComponents();
        refreshSidebar(null);

        if (!tableListModel.isEmpty()) {
            tableList.setSelectedIndex(0);
        }
    }

    private void configureWindow() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1120, 720);
        setMinimumSize(new Dimension(900, 580));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_MAIN);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                repository.save(engine.getDatabase());
                dispose();
                System.exit(0);
            }
        });
    }

    private void initComponents() {
        setLayout(new BorderLayout(0, 0));

        JPanel sidebarPanel = buildSidebarPanel();

        JPanel mainWorkspace = buildWorkspacePanel();

        JSplitPane horizontalSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebarPanel, mainWorkspace);
        horizontalSplit.setDividerLocation(270);
        horizontalSplit.setDividerSize(5);
        horizontalSplit.setBorder(null);

        add(horizontalSplit, BorderLayout.CENTER);
    }

    private JPanel buildSidebarPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(BG_MAIN);
        panel.setBorder(new EmptyBorder(10, 10, 10, 6));

        JPanel listContainer = new JPanel(new BorderLayout(0, 6));
        listContainer.setBackground(BG_PANEL);
        listContainer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                new EmptyBorder(8, 8, 8, 8)
        ));

        JLabel tablesTitle = new JLabel("ТАБЛИЦЫ БАЗЫ ДАННЫХ");
        tablesTitle.setFont(FONT_BOLD);
        tablesTitle.setForeground(TEXT_DARK);

        tableList.setFont(FONT_UI);
        tableList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tableList.setFixedCellHeight(28);
        tableList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = tableList.getSelectedValue();
                if (selected != null) {
                    updateSchemaDetails(selected);
                    sqlEditor.setText("SELECT * FROM " + selected + ";");
                    executeSqlQuery(sqlEditor.getText(), false);
                }
            }
        });

        JScrollPane listScroll = new JScrollPane(tableList);
        listScroll.setBorder(BorderFactory.createEmptyBorder());
        listContainer.add(tablesTitle, BorderLayout.NORTH);
        listContainer.add(listScroll, BorderLayout.CENTER);

        JPanel schemaContainer = new JPanel(new BorderLayout(0, 6));
        schemaContainer.setBackground(BG_PANEL);
        schemaContainer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                new EmptyBorder(8, 8, 8, 8)
        ));

        JLabel schemaTitle = new JLabel("СТРУКТУРА И ИНДЕКСЫ");
        schemaTitle.setFont(FONT_BOLD);
        schemaTitle.setForeground(TEXT_DARK);

        schemaInfoArea.setEditable(false);
        schemaInfoArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        schemaInfoArea.setBackground(new Color(248, 250, 252));
        schemaInfoArea.setBorder(new EmptyBorder(6, 6, 6, 6));

        JScrollPane schemaScroll = new JScrollPane(schemaInfoArea);
        schemaScroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));

        schemaContainer.add(schemaTitle, BorderLayout.NORTH);
        schemaContainer.add(schemaScroll, BorderLayout.CENTER);

        JSplitPane sidebarSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, listContainer, schemaContainer);
        sidebarSplit.setDividerLocation(240);
        sidebarSplit.setDividerSize(5);
        sidebarSplit.setBorder(null);

        panel.add(sidebarSplit, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildWorkspacePanel() {
        JPanel workspace = new JPanel(new BorderLayout(0, 8));
        workspace.setBackground(BG_MAIN);
        workspace.setBorder(new EmptyBorder(10, 6, 10, 10));

        JPanel topSqlPanel = buildSqlEditorPanel();

        JPanel centerTablePanel = buildResultTablePanel();

        JPanel bottomStatusPanel = buildBottomLogPanel();

        workspace.add(topSqlPanel, BorderLayout.NORTH);
        workspace.add(centerTablePanel, BorderLayout.CENTER);
        workspace.add(bottomStatusPanel, BorderLayout.SOUTH);

        return workspace;
    }

    private JPanel buildSqlEditorPanel() {
        JPanel container = new JPanel(new BorderLayout(0, 6));
        container.setBackground(BG_PANEL);
        container.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                new EmptyBorder(8, 10, 10, 10)
        ));

        // Панель инструментов
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        toolbar.setBackground(BG_PANEL);

        JButton runBtn = createButton("▶ Выполнить (Ctrl+Enter)", ACCENT_BLUE, Color.WHITE);
        runBtn.addActionListener(e -> executeSqlQuery(sqlEditor.getText(), true));

        benchmarkBtn = createButton("⚡ Бенчмарк (50 000 строк)", new Color(241, 245, 249), TEXT_DARK);
        benchmarkBtn.addActionListener(e -> runAsyncBenchmark());

        String[] presets = {
                "— Шаблоны SQL-запросов —",
                "SELECT * FROM drivers;",
                "SELECT * FROM teams;",
                "SELECT name, points FROM drivers WHERE points >= 300;",
                "INSERT INTO drivers VALUES (55, 'Carlos Sainz', 1, 290.0, '1994-09-01', FALSE);",
                "UPDATE drivers SET points = 415.0 WHERE id = 1;",
                "DELETE FROM drivers WHERE id = 55;",
                "CREATE INDEX idx_drivers_team ON drivers (team_id);",
                "CREATE TABLE circuits (id INT PK, title STRING, length FLOAT, first_gp DATE, street BOOL);",
                "INSERT INTO circuits VALUES (1, 'Monaco', 3.337, '1950-05-21', TRUE);",
                "DROP TABLE circuits;",
                "INSERT INTO drivers VALUES (99, 'Ghost Driver', 999, 0.0, '2000-01-01', FALSE);"
        };

        JComboBox<String> presetCombo = new JComboBox<>(presets);
        presetCombo.setFont(FONT_UI);
        presetCombo.addActionListener(e -> {
            int idx = presetCombo.getSelectedIndex();
            if (idx > 0) {
                sqlEditor.setText((String) presetCombo.getSelectedItem());
            }
        });

        toolbar.add(runBtn);
        toolbar.add(benchmarkBtn);
        toolbar.add(new JLabel("  Быстрый выбор:"));
        toolbar.add(presetCombo);

        sqlEditor.setFont(FONT_MONO);
        sqlEditor.setLineWrap(true);
        sqlEditor.setWrapStyleWord(true);
        sqlEditor.setBorder(new EmptyBorder(6, 8, 6, 8));

        sqlEditor.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "runSql");
        sqlEditor.getActionMap().put("runSql", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                executeSqlQuery(sqlEditor.getText(), true);
            }
        });

        JScrollPane editorScroll = new JScrollPane(sqlEditor);
        editorScroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));

        container.add(toolbar, BorderLayout.NORTH);
        container.add(editorScroll, BorderLayout.CENTER);
        return container;
    }

    private JPanel buildResultTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(BG_PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                new EmptyBorder(8, 10, 8, 10)
        ));

        resultTable.setFont(FONT_UI);
        resultTable.setRowHeight(26);
        resultTable.setGridColor(new Color(241, 245, 249));
        resultTable.setSelectionBackground(new Color(219, 234, 254));
        resultTable.setSelectionForeground(TEXT_DARK);
        resultTable.setFillsViewportHeight(true);

        JTableHeader header = resultTable.getTableHeader();
        header.setFont(FONT_BOLD);
        header.setBackground(new Color(241, 245, 249));
        header.setForeground(TEXT_DARK);
        header.setPreferredSize(new Dimension(100, 30));

        // Чередование цвета строк (зебра)
        resultTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(248, 250, 252));
                }
                setBorder(new EmptyBorder(0, 8, 0, 8));
                return c;
            }
        });

        JScrollPane tableScroll = new JScrollPane(resultTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));

        panel.add(tableScroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildBottomLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBackground(BG_PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                new EmptyBorder(6, 10, 8, 10)
        ));

        statusLabel.setFont(FONT_BOLD);
        statusLabel.setForeground(STATUS_OK);

        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        logArea.setBackground(new Color(248, 250, 252));
        logArea.setBorder(new EmptyBorder(4, 6, 4, 6));

        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));

        panel.add(statusLabel, BorderLayout.NORTH);
        panel.add(logScroll, BorderLayout.CENTER);
        return panel;
    }

    private JButton createButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BOLD);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(bg.darker()),
                new EmptyBorder(6, 12, 6, 12)
        ));
        return btn;
    }

    private void executeSqlQuery(String rawSql, boolean refreshTablesOnMutate) {
        String trimmed = rawSql.trim();
        if (trimmed.isEmpty()) return;

        try {
            QueryResult result = engine.executeForGui(trimmed);

            if (result.isSelectQuery()) {
                renderTable(result.getColumns(), result.getRows());
            } else if (refreshTablesOnMutate) {
                String currentSelected = tableList.getSelectedValue();
                refreshSidebar(currentSelected);
                String targetTable = tableList.getSelectedValue();
                if (targetTable != null && engine.getDatabase().getTables().containsKey(targetTable)) {
                    QueryResult refreshed = engine.executeForGui("SELECT * FROM " + targetTable);
                    renderTable(refreshed.getColumns(), refreshed.getRows());
                } else {
                    resultTableModel.setRowCount(0);
                    resultTableModel.setColumnCount(0);
                }
            }

            statusLabel.setForeground(STATUS_OK);
            statusLabel.setText(" ✔ " + result.getStatusMessage());
            appendLog("[OK] " + trimmed + "  -->  " + result.getStatusMessage());

        } catch (Exception ex) {
            statusLabel.setForeground(STATUS_ERR);
            statusLabel.setText(" ✖ Ошибка: " + ex.getMessage());
            appendLog("[ОШИБКА] " + ex.getMessage());
        }
    }

    private void renderTable(List<String> columns, List<Row> rows) {
        resultTableModel.setRowCount(0);
        resultTableModel.setColumnCount(0);

        for (String col : columns) {
            resultTableModel.addColumn(col.toUpperCase());
        }

        for (Row row : rows) {
            Object[] rowData = new Object[columns.size()];
            for (int i = 0; i < columns.size(); i++) {
                Comparable<?> val = row.getValue(columns.get(i));
                rowData[i] = val != null ? val : "NULL";
            }
            resultTableModel.addRow(rowData);
        }
    }

    private void refreshSidebar(String preserveSelection) {
        tableListModel.clear();
        for (String tableName : engine.getDatabase().getTables().keySet()) {
            tableListModel.addElement(tableName);
        }

        if (preserveSelection != null && tableListModel.contains(preserveSelection)) {
            tableList.setSelectedValue(preserveSelection, true);
            updateSchemaDetails(preserveSelection);
        } else if (!tableListModel.isEmpty()) {
            tableList.setSelectedIndex(0);
            updateSchemaDetails(tableList.getSelectedValue());
        } else {
            schemaInfoArea.setText("Нет таблиц в БД");
        }
    }

    private void updateSchemaDetails(String tableName) {
        if (tableName == null || !engine.getDatabase().getTables().containsKey(tableName)) {
            schemaInfoArea.setText("");
            return;
        }
        Table t = engine.getDatabase().getTable(tableName);
        StringBuilder sb = new StringBuilder();
        sb.append("Таблица: ").append(t.getName()).append("\n");
        sb.append("Записей: ").append(t.getRowsByPk().size()).append("\n\n");
        sb.append("Столбцы:\n");
        for (Column c : t.getColumns().values()) {
            sb.append(" • ").append(c.getName()).append(" (").append(c.getType()).append(")");
            if (c.isPrimaryKey()) sb.append(" [PK]");
            if (c.getForeignKeyTable() != null) {
                sb.append(" [FK->").append(c.getForeignKeyTable())
                        .append(".").append(c.getForeignKeyColumn()).append("]");
            }
            sb.append("\n");
        }

        sb.append("\nИндексы (TreeMap):\n");
        if (t.getIndexesByColumn().isEmpty()) {
            sb.append(" (нет индексов)\n");
        } else {
            for (TableIndex idx : t.getIndexesByColumn().values()) {
                sb.append(" • ").append(idx.getIndexName())
                        .append(" по (").append(idx.getColumnName()).append(")\n");
            }
        }
        schemaInfoArea.setText(sb.toString());
    }

    private void appendLog(String line) {
        logArea.append(line + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    private void runAsyncBenchmark() {
        benchmarkBtn.setEnabled(false);
        statusLabel.setForeground(ACCENT_BLUE);
        statusLabel.setText(" ⏳ Выполняется бенчмарк на 50 000 записей...");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
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

                    int iterations = 100;
                    long startNoIdx = System.nanoTime();
                    for (int i = 0; i < iterations; i++) {
                        engine.executeForGui("SELECT * FROM telemetry WHERE driver_code = 'TARGET_LAP';");
                    }
                    double avgNoIdxMs = ((System.nanoTime() - startNoIdx) / 1_000_000.0) / iterations;

                    long idxBuildStart = System.nanoTime();
                    engine.execute("CREATE INDEX idx_telemetry_code ON telemetry (driver_code);");
                    double idxBuildMs = (System.nanoTime() - idxBuildStart) / 1_000_000.0;

                    long startWithIdx = System.nanoTime();
                    for (int i = 0; i < iterations; i++) {
                        engine.executeForGui("SELECT * FROM telemetry WHERE driver_code = 'TARGET_LAP';");
                    }
                    double avgWithIdxMs = ((System.nanoTime() - startWithIdx) / 1_000_000.0) / iterations;

                    engine.execute("DROP TABLE telemetry;");

                    double speedup = avgNoIdxMs / Math.max(avgWithIdxMs, 0.0001);
                    return String.format(Locale.US,
                            "Объём выборки: %d строк (100 поисковых запросов)\n\n"
                                    + "• Генерация и вставка 50 000 строк: %.2f мс\n"
                                    + "• Поиск БЕЗ индекса (Full Table Scan): %.4f мс\n"
                                    + "• Построение TreeMap-индекса: %.2f мс\n"
                                    + "• Поиск С ИНДЕКСОМ (Index Scan): %.4f мс\n\n"
                                    + "ИТОГОВОЕ УСКОРЕНИЕ: в %.1f раз(а)!",
                            totalRows, insertMs, avgNoIdxMs, idxBuildMs, avgWithIdxMs, speedup);
                } finally {
                    engine.setAutoSave(true);
                    engine.persistIfNeeded();
                }
            }

            @Override
            protected void done() {
                benchmarkBtn.setEnabled(true);
                try {
                    String report = get();
                    statusLabel.setForeground(STATUS_OK);
                    statusLabel.setText(" ✔ Бенчмарк успешно завершён");
                    appendLog("[BENCHMARK] " + report.replace("\n", " | "));
                    JOptionPane.showMessageDialog(MainFrame.this, report,
                            "Результаты тестирования производительности", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    statusLabel.setForeground(STATUS_ERR);
                    statusLabel.setText(" ✖ Ошибка бенчмарка: " + ex.getMessage());
                }
            }
        }.execute();
    }
}