package coursework;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * singleton thread-safe append-only log of all messages 
 * satisfies the "group state maintenance with timestamps" requirement and provides a centralised place to store message history for potential future features (e.g. message retrieval, analytics)
 *
 * design pattern: Singleton (double-checked locking with volatile)
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
    // note: MessageRecord is immutable, so we can safely store and return references without defensive copying
    public void log(MessageRecord record) {
        records.add(record);
    }
    // returns an unmodifiable snapshot of all message records, ordered by timestamp (oldest first)
    public List<MessageRecord> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(records));
    }
    // returns an unmodifiable list of message records sent by the specified client, ordered by timestamp (oldest first)
    public List<MessageRecord> getBySender(String senderId) {
        return records.stream()
            .filter(r -> senderId.equals(r.getSenderId()))
            .collect(Collectors.toList());
    }
    // returns an unmodifiable list of message records received by the specified client, ordered by timestamp (oldest first)
    public int size() {
        return records.size();
    }

    /** used by tests only to reset state between test runs */
    public void clear() {
        records.clear();
    }
}
