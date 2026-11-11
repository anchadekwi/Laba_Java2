package f1db.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

public class TableRowIterator implements Iterator<Row> {
    private final List<Row> snapshot;
    private int currentIndex = 0;

    public TableRowIterator(Collection<Row> rows) {
        this.snapshot = new ArrayList<>(rows);
    }

    @Override
    public boolean hasNext() {
        return currentIndex < snapshot.size();
    }

    @Override
    public Row next() {
        if (!hasNext()) {
            throw new NoSuchElementException("Больше нет записей в таблице");
        }
        return snapshot.get(currentIndex++);
    }
}