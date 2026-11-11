package f1db;

import f1db.engine.SqlEngine;
import f1db.model.Database;
import f1db.storage.StorageRepository;
import f1db.ui.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Главный класс запуска СУБД Formula 1 с графическим интерфейсом Swing.
 *
 * @author Anna Fedorova
 * @version 1.1
 */
public class F1DatabaseApp {

    private static void initializeDefaultF1Data(SqlEngine engine) {
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

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        StorageRepository repository = new StorageRepository("f1_database.db");
        Database db = repository.load();
        SqlEngine engine;

        if (db == null) {
            db = new Database();
            engine = new SqlEngine(db, repository);
            initializeDefaultF1Data(engine);
        } else {
            engine = new SqlEngine(db, repository);
        }

        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame(engine, repository);
            frame.setVisible(true);
        });
    }
}