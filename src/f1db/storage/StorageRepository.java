package f1db.storage;

import f1db.model.Database;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class StorageRepository {
    private final Path dbFilePath;
    private final Path tmpFilePath;
    private final Path bakFilePath;

    public StorageRepository(String fileName) {
        this.dbFilePath = Paths.get(fileName);
        this.tmpFilePath = Paths.get(fileName + ".tmp");
        this.bakFilePath = Paths.get(fileName + ".bak");
    }

    public synchronized void save(Database db) {
        try {
            try (ObjectOutputStream oos = new ObjectOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(tmpFilePath)))) {
                oos.writeObject(db);
                oos.flush();
            }
            if (Files.exists(dbFilePath)) {
                Files.copy(dbFilePath, bakFilePath, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(tmpFilePath, dbFilePath,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmpFilePath, dbFilePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("[ОШИБКА ДИСКА] Не удалось сохранить БД: " + e.getMessage());
        }
    }

    public synchronized Database load() {
        if (Files.exists(tmpFilePath)) {
            System.out.println("[RECOVERY] Обнаружен временный файл после некорректного завершения. Очистка...");
            try { Files.deleteIfExists(tmpFilePath); } catch (IOException ignored) {}
        }

        if (Files.exists(dbFilePath)) {
            try (ObjectInputStream ois = new ObjectInputStream(
                    new BufferedInputStream(Files.newInputStream(dbFilePath)))) {
                return (Database) ois.readObject();
            } catch (Exception e) {
                System.err.println("[RECOVERY] Основной файл БД повреждён (" + e.getMessage()
                        + "). Попытка восстановления из резервной копии .bak...");
            }
        }

        if (Files.exists(bakFilePath)) {
            try (ObjectInputStream ois = new ObjectInputStream(
                    new BufferedInputStream(Files.newInputStream(bakFilePath)))) {
                Database recovered = (Database) ois.readObject();
                save(recovered);
                System.out.println("[RECOVERY] База данных успешно восстановлена из резервной копии!");
                return recovered;
            } catch (Exception e) {
                System.err.println("[RECOVERY] Не удалось прочитать резервную копию: " + e.getMessage());
            }
        }
        return null;
    }
}