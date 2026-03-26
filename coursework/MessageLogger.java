package coursework;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Singleton thread-safe append-only log of all messages.
 * Satisfies the "group state maintenance with timestamps" requirement.
 *
 * Design pattern: Singleton (double-checked locking with volatile).
 */
public class MessageLogger {

    private static volatile MessageLogger instance;

    private final CopyOnWriteArrayList<MessageRecord> records = new CopyOnWriteArrayList<>();

    private MessageLogger() {}

    public static MessageLogger getInstance() {
        if (instance == null) {
            synchronized (MessageLogger.class) {
                if (instance == null) {
                    instance = new MessageLogger();
                }
            }
        }
        return instance;
    }

    public void log(MessageRecord record) {
        records.add(record);
    }

    public List<MessageRecord> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }

    public List<MessageRecord> getBySender(String senderId) {
        return records.stream()
            .filter(r -> senderId.equals(r.getSenderId()))
            .collect(Collectors.toList());
    }

    public int size() {
        return records.size();
    }

    /** Used by tests only to reset state between test runs. */
    public void clear() {
        records.clear();
    }
}
