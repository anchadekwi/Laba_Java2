package f1db.ui;

import f1db.engine.QueryResult;
import f1db.engine.SqlEngine;
import f1db.index.TableIndex;
import f1db.model.Column;
import f1db.model.DataType;
import f1db.model.Row;
import f1db.model.Table;
import f1db.storage.StorageRepository;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class MainFrame extends JFrame {
    private static final Color BG_MAIN = new Color(241, 245, 249);
    private static final Color BG_PANEL = Color.WHITE;
    private static final Color BORDER_COLOR = new Color(203, 213, 225);

    private static final Color BTN_PRIMARY_BG = new Color(37, 99, 235);
    private static final Color BTN_GREEN_BG = new Color(22, 163, 74);
    private static final Color BTN_NEUTRAL_BG = new Color(226, 232, 240);
    private static final Color BTN_DANGER_BG = new Color(254, 226, 226);

    private static final Color TEXT_DARK = new Color(15, 23, 42);
    private static final Color TEXT_DANGER = new Color(185, 28, 28);
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
    private final JLabel tableHeaderLabel = new JLabel("ДАННЫЕ ТАБЛИЦЫ");

    // Компоненты быстрого фильтра WHERE
    private final DefaultComboBoxModel<String> filterColModel = new DefaultComboBoxModel<>();
    private final JComboBox<String> filterColCombo = new JComboBox<>(filterColModel);
    private final JComboBox<String> filterOpCombo = new JComboBox<>(new String[]{"=", "!=", ">", "<", ">=", "<="});
    private final JTextField filterValField = new JTextField(10);

    private final JLabel statusLabel = new JLabel(" Готово к работе");
    private final JTextArea logArea = new JTextArea(4, 60);

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
        setSize(1180, 750);
        setMinimumSize(new Dimension(960, 620));
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
        horizontalSplit.setDividerLocation(285);
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
                    updateFilterColumns(selected);
                    tableHeaderLabel.setText("ТАБЛИЦА: " + selected.toUpperCase());
                    sqlEditor.setText("SELECT * FROM " + selected + ";");
                    executeSqlQuery(sqlEditor.getText(), false);
                }
            }
        });

        JScrollPane listScroll = new JScrollPane(tableList);
        listScroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));

        JButton dropTableBtn = createButton("Удалить таблицу", BTN_DANGER_BG, TEXT_DANGER);
        dropTableBtn.addActionListener(e -> dropSelectedTable());

        listContainer.add(tablesTitle, BorderLayout.NORTH);
        listContainer.add(listScroll, BorderLayout.CENTER);
        listContainer.add(dropTableBtn, BorderLayout.SOUTH);

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

        JButton addIndexBtn = createButton("Создать индекс по столбцу", BTN_NEUTRAL_BG, TEXT_DARK);
        addIndexBtn.addActionListener(e -> showCreateIndexDialog());

        schemaContainer.add(schemaTitle, BorderLayout.NORTH);
        schemaContainer.add(schemaScroll, BorderLayout.CENTER);
        schemaContainer.add(addIndexBtn, BorderLayout.SOUTH);

        JSplitPane sidebarSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, listContainer, schemaContainer);
        sidebarSplit.setDividerLocation(230);
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

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        toolbar.setBackground(BG_PANEL);

        JButton runBtn = createButton("Выполнить запрос", BTN_PRIMARY_BG, Color.WHITE);
        runBtn.addActionListener(e -> executeSqlQuery(sqlEditor.getText(), true));

        JButton clearBtn = createButton("Очистить", BTN_NEUTRAL_BG, TEXT_DARK);
        clearBtn.addActionListener(e -> sqlEditor.setText(""));

        String[] presets = {
                "Готовые шаблоны SQL-запросов",
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
        toolbar.add(clearBtn);
        toolbar.add(new JLabel("   Шаблон:"));
        toolbar.add(presetCombo);

        sqlEditor.setFont(FONT_MONO);
        sqlEditor.setForeground(TEXT_DARK);
        sqlEditor.setLineWrap(true);
        sqlEditor.setWrapStyleWord(true);
        sqlEditor.setBorder(new EmptyBorder(6, 8, 6, 8));

        sqlEditor.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "runSql");
        sqlEditor.getActionMap().put("runSql", new AbstractAction() {
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

        JPanel topBar = new JPanel(new BorderLayout(8, 6));
        topBar.setBackground(BG_PANEL);

        tableHeaderLabel.setFont(FONT_BOLD);
        tableHeaderLabel.setForeground(TEXT_DARK);

        JPanel crudButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        crudButtonsPanel.setBackground(BG_PANEL);

        JButton addRowBtn = createButton("+ Добавить строку", BTN_GREEN_BG, Color.WHITE);
        addRowBtn.addActionListener(e -> showAddRowDialog());

        JButton editCellBtn = createButton("✎ Изменить ячейку", BTN_NEUTRAL_BG, TEXT_DARK);
        editCellBtn.addActionListener(e -> editSelectedTableCell());

        JButton deleteRowBtn = createButton("✕ Удалить строку", BTN_DANGER_BG, TEXT_DANGER);
        deleteRowBtn.addActionListener(e -> deleteSelectedTableRow());

        crudButtonsPanel.add(addRowBtn);
        crudButtonsPanel.add(editCellBtn);
        crudButtonsPanel.add(deleteRowBtn);

        JPanel filterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        filterBar.setBackground(new Color(248, 250, 252));
        filterBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR),
                new EmptyBorder(4, 6, 4, 6)
        ));

        JLabel filterLbl = new JLabel("Быстрый фильтр WHERE:");
        filterLbl.setFont(FONT_BOLD);
        filterColCombo.setFont(FONT_UI);
        filterOpCombo.setFont(FONT_UI);
        filterValField.setFont(FONT_MONO);

        JButton applyFilterBtn = createButton("Найти", BTN_PRIMARY_BG, Color.WHITE);
        applyFilterBtn.addActionListener(e -> applyQuickFilter());

        JButton resetFilterBtn = createButton("Все строки", BTN_NEUTRAL_BG, TEXT_DARK);
        resetFilterBtn.addActionListener(e -> {
            String table = tableList.getSelectedValue();
            if (table != null) {
                filterValField.setText("");
                sqlEditor.setText("SELECT * FROM " + table + ";");
                executeSqlQuery(sqlEditor.getText(), false);
            }
        });

        filterBar.add(filterLbl);
        filterBar.add(filterColCombo);
        filterBar.add(filterOpCombo);
        filterBar.add(filterValField);
        filterBar.add(applyFilterBtn);
        filterBar.add(resetFilterBtn);

        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setBackground(BG_PANEL);
        headerRow.add(tableHeaderLabel, BorderLayout.WEST);
        headerRow.add(crudButtonsPanel, BorderLayout.EAST);

        topBar.add(headerRow, BorderLayout.NORTH);
        topBar.add(filterBar, BorderLayout.SOUTH);

        resultTable.setFont(FONT_UI);
        resultTable.setRowHeight(26);
        resultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resultTable.setAutoCreateRowSorter(true);
        resultTable.setGridColor(new Color(226, 232, 240));
        resultTable.setSelectionBackground(new Color(219, 234, 254));
        resultTable.setSelectionForeground(TEXT_DARK);
        resultTable.setFillsViewportHeight(true);

        JTableHeader header = resultTable.getTableHeader();
        header.setFont(FONT_BOLD);
        header.setBackground(new Color(241, 245, 249));
        header.setForeground(TEXT_DARK);
        header.setPreferredSize(new Dimension(100, 30));

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

        // Контекстное меню и двойной клик для UPDATE
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem addMenuItem = new JMenuItem("Добавить новую строку");
        addMenuItem.setFont(FONT_UI);
        addMenuItem.addActionListener(e -> showAddRowDialog());

        JMenuItem editMenuItem = new JMenuItem("Изменить значение ячейки");
        editMenuItem.setFont(FONT_UI);
        editMenuItem.addActionListener(e -> editSelectedTableCell());

        JMenuItem deleteMenuItem = new JMenuItem("Удалить выбранную строку");
        deleteMenuItem.setFont(FONT_UI);
        deleteMenuItem.setForeground(TEXT_DANGER);
        deleteMenuItem.addActionListener(e -> deleteSelectedTableRow());

        popupMenu.add(addMenuItem);
        popupMenu.add(editMenuItem);
        popupMenu.addSeparator();
        popupMenu.add(deleteMenuItem);

        resultTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    editSelectedTableCell();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                handlePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handlePopup(e);
            }

            private void handlePopup(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    int r = resultTable.rowAtPoint(e.getPoint());
                    int c = resultTable.columnAtPoint(e.getPoint());
                    if (r >= 0 && r < resultTable.getRowCount()) {
                        resultTable.setRowSelectionInterval(r, r);
                    }
                    if (c >= 0 && c < resultTable.getColumnCount()) {
                        resultTable.setColumnSelectionInterval(c, c);
                    }
                    if (e.isPopupTrigger()) {
                        popupMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });

        resultTable.getInputMap(JComponent.WHEN_FOCUSED).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "deleteSelectedRow");
        resultTable.getActionMap().put("deleteSelectedRow", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteSelectedTableRow();
            }
        });

        JScrollPane tableScroll = new JScrollPane(resultTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));

        panel.add(topBar, BorderLayout.NORTH);
        panel.add(tableScroll, BorderLayout.CENTER);
        return panel;
    }

    private JButton createButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setUI(new BasicButtonUI());
        btn.setFont(FONT_BOLD);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setOpaque(true);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(bg.darker()),
                new EmptyBorder(6, 12, 6, 12)
        ));

        Color hoverBg = bg.equals(BTN_PRIMARY_BG) ? new Color(29, 78, 216)
                : bg.equals(BTN_GREEN_BG) ? new Color(21, 128, 61)
                : bg.darker();

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (btn.isEnabled()) btn.setBackground(hoverBg);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (btn.isEnabled()) btn.setBackground(bg);
            }
        });
        return btn;
    }

    private void applyQuickFilter() {
        String tableName = tableList.getSelectedValue();
        if (tableName == null || !engine.getDatabase().getTables().containsKey(tableName)) return;

        String colName = (String) filterColCombo.getSelectedItem();
        String op = (String) filterOpCombo.getSelectedItem();
        String rawVal = filterValField.getText().trim();

        if (colName == null || rawVal.isEmpty()) {
            statusLabel.setForeground(STATUS_ERR);
            statusLabel.setText(" ✖ Введите значение для фильтрации");
            return;
        }

        Table table = engine.getDatabase().getTable(tableName);
        Column colMeta = table.getColumns().get(colName);
        String formattedVal = formatSqlLiteral(colMeta, rawVal);

        String sql = "SELECT * FROM " + tableName + " WHERE " + colName + " " + op + " " + formattedVal + ";";
        sqlEditor.setText(sql);
        executeSqlQuery(sql, false);
    }

    private void showAddRowDialog() {
        String tableName = tableList.getSelectedValue();
        if (tableName == null || !engine.getDatabase().getTables().containsKey(tableName)) {
            JOptionPane.showMessageDialog(this, "Сначала выберите таблицу слева.",
                    "Таблица не выбрана", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Table table = engine.getDatabase().getTable(tableName);
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 6, 5, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        Map<Column, JComponent> inputs = new LinkedHashMap<>();
        int rowIdx = 0;

        for (Column col : table.getColumns().values()) {
            gbc.gridx = 0;
            gbc.gridy = rowIdx;
            gbc.weightx = 0.0;

            StringBuilder labelText = new StringBuilder(col.getName()).append(" (").append(col.getType()).append(")");
            if (col.isPrimaryKey()) labelText.append(" [PK]");
            if (col.getForeignKeyTable() != null) {
                labelText.append(" [FK -> ").append(col.getForeignKeyTable()).append("]");
            }

            JLabel lbl = new JLabel(labelText.toString() + ":");
            lbl.setFont(FONT_BOLD);
            formPanel.add(lbl, gbc);

            gbc.gridx = 1;
            gbc.weightx = 1.0;

            if (col.getType() == DataType.BOOL) {
                JCheckBox checkBox = new JCheckBox("TRUE (да / активно)");
                checkBox.setFont(FONT_UI);
                checkBox.setSelected(true);
                inputs.put(col, checkBox);
                formPanel.add(checkBox, gbc);
            } else {
                JTextField textField = new JTextField(18);
                textField.setFont(FONT_MONO);
                if (col.getType() == DataType.DATE) {
                    textField.setText(LocalDate.now().toString());
                }
                inputs.put(col, textField);
                formPanel.add(textField, gbc);
            }
            rowIdx++;
        }

        int choice = JOptionPane.showConfirmDialog(this, formPanel,
                "Добавить запись в '" + tableName + "'",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (choice != JOptionPane.OK_OPTION) return;

        List<String> formattedValues = new ArrayList<>();
        for (Map.Entry<Column, JComponent> entry : inputs.entrySet()) {
            Column col = entry.getKey();
            JComponent comp = entry.getValue();

            if (comp instanceof JCheckBox) {
                formattedValues.add(((JCheckBox) comp).isSelected() ? "TRUE" : "FALSE");
            } else if (comp instanceof JTextField) {
                formattedValues.add(formatSqlLiteral(col, ((JTextField) comp).getText().trim()));
            }
        }

        String insertSql = "INSERT INTO " + tableName + " VALUES (" + String.join(", ", formattedValues) + ");";
        sqlEditor.setText(insertSql);
        executeSqlQuery(insertSql, true);
    }

    private void editSelectedTableCell() {
        String tableName = tableList.getSelectedValue();
        if (tableName == null || !engine.getDatabase().getTables().containsKey(tableName)) return;

        int viewRow = resultTable.getSelectedRow();
        int viewCol = resultTable.getSelectedColumn();
        if (viewRow < 0 || viewCol < 0) {
            statusLabel.setForeground(STATUS_ERR);
            statusLabel.setText(" Кликните по ячейке, которую хотите изменить");
            return;
        }

        int modelRow = resultTable.convertRowIndexToModel(viewRow);
        int modelCol = resultTable.convertColumnIndexToModel(viewCol);

        Table table = engine.getDatabase().getTable(tableName);
        String pkColName = table.getPrimaryKeyColumn();
        Column pkMeta = table.getColumns().get(pkColName);

        int pkColIndex = findColumnIndex(pkColName);
        if (pkColIndex == -1) {
            JOptionPane.showMessageDialog(this,
                    "Для редактирования в выборке должен присутствовать столбец PK '" + pkColName + "'.",
                    "Нет первичного ключа", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String targetColName = resultTableModel.getColumnName(modelCol).toLowerCase();
        Column targetColMeta = table.getColumns().get(targetColName);
        Object currentVal = resultTableModel.getValueAt(modelRow, modelCol);
        Object pkVal = resultTableModel.getValueAt(modelRow, pkColIndex);

        String newRawValue = (String) JOptionPane.showInputDialog(
                this,
                "Новое значение для столбца '" + targetColName + "' (" + targetColMeta.getType() + "):",
                "Обновление записи (UPDATE)",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                currentVal != null ? currentVal.toString() : ""
        );

        if (newRawValue == null) return;

        String formattedNewVal = formatSqlLiteral(targetColMeta, newRawValue.trim());
        String formattedPkVal = formatSqlLiteral(pkMeta, pkVal.toString());

        String updateSql = "UPDATE " + tableName + " SET " + targetColName + " = " + formattedNewVal
                + " WHERE " + pkColName + " = " + formattedPkVal + ";";
        sqlEditor.setText(updateSql);
        executeSqlQuery(updateSql, true);
    }

    private void deleteSelectedTableRow() {
        String tableName = tableList.getSelectedValue();
        if (tableName == null || !engine.getDatabase().getTables().containsKey(tableName)) return;

        int viewRow = resultTable.getSelectedRow();
        if (viewRow < 0) {
            statusLabel.setForeground(STATUS_ERR);
            statusLabel.setText(" ✖ Выделите строку в таблице для удаления");
            return;
        }

        int modelRow = resultTable.convertRowIndexToModel(viewRow);
        Table table = engine.getDatabase().getTable(tableName);
        String pkColName = table.getPrimaryKeyColumn();
        Column pkMeta = table.getColumns().get(pkColName);

        int pkColIndex = findColumnIndex(pkColName);
        if (pkColIndex == -1) {
            JOptionPane.showMessageDialog(this,
                    "В текущей выборке отсутствует столбец первичного ключа '" + pkColName + "'.",
                    "Нет первичного ключа", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Object pkValueObj = resultTableModel.getValueAt(modelRow, pkColIndex);
        String pkLiteral = formatSqlLiteral(pkMeta, pkValueObj.toString());

        String deleteSql = "DELETE FROM " + tableName + " WHERE " + pkColName + " = " + pkLiteral + ";";
        sqlEditor.setText(deleteSql);
        executeSqlQuery(deleteSql, true);
    }

    private void showCreateIndexDialog() {
        String tableName = tableList.getSelectedValue();
        if (tableName == null || !engine.getDatabase().getTables().containsKey(tableName)) return;

        Table table = engine.getDatabase().getTable(tableName);
        String[] cols = table.getColumns().keySet().toArray(new String[0]);

        String chosenCol = (String) JOptionPane.showInputDialog(
                this,
                "Выберите столбец таблицы '" + tableName + "' для построения TreeMap-индекса:",
                "Создание индекса (CREATE INDEX)",
                JOptionPane.PLAIN_MESSAGE,
                null,
                cols,
                cols[0]
        );

        if (chosenCol == null) return;

        String indexName = "idx_" + tableName + "_" + chosenCol;
        String sql = "CREATE INDEX " + indexName + " ON " + tableName + " (" + chosenCol + ");";
        sqlEditor.setText(sql);
        executeSqlQuery(sql, true);
    }

    private void dropSelectedTable() {
        String tableName = tableList.getSelectedValue();
        if (tableName == null) return;

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Удалить таблицу '" + tableName + "' со всеми её данными?",
                "Подтверждение DROP TABLE",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (confirm == JOptionPane.YES_OPTION) {
            String sql = "DROP TABLE " + tableName + ";";
            sqlEditor.setText(sql);
            executeSqlQuery(sql, true);
        }
    }

    private int findColumnIndex(String colName) {
        for (int i = 0; i < resultTableModel.getColumnCount(); i++) {
            if (resultTableModel.getColumnName(i).equalsIgnoreCase(colName)) {
                return i;
            }
        }
        return -1;
    }

    private String formatSqlLiteral(Column col, String raw) {
        if (raw == null || raw.isEmpty() || raw.equalsIgnoreCase("NULL")) {
            return "NULL";
        }
        if (col != null && (col.getType() == DataType.STRING || col.getType() == DataType.DATE)) {
            String clean = raw.replace("'", "");
            return "'" + clean + "'";
        }
        return raw;
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
            updateFilterColumns(preserveSelection);
        } else if (!tableListModel.isEmpty()) {
            tableList.setSelectedIndex(0);
            String first = tableList.getSelectedValue();
            updateSchemaDetails(first);
            updateFilterColumns(first);
        } else {
            schemaInfoArea.setText("Нет таблиц в БД");
            filterColModel.removeAllElements();
        }
    }

    private void updateFilterColumns(String tableName) {
        filterColModel.removeAllElements();
        if (tableName == null || !engine.getDatabase().getTables().containsKey(tableName)) return;
        Table t = engine.getDatabase().getTable(tableName);
        for (String colName : t.getColumns().keySet()) {
            filterColModel.addElement(colName);
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
}