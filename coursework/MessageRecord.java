package coursework;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * immutable, timestamped record of a single message event
 * stored in {@link MessageLogger} for group state maintenance and server console output
 */
public class MessageRecord {

    public enum Type { BROADCAST, PRIVATE, SYSTEM }

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String    timestamp;
    private final String    senderId;
    private final String    targetId;   // null = broadcast / system
    private final String    content;
    private final Type      type;

    // constructs a new message record with the current timestamp
    public MessageRecord(String senderId, String targetId, String content, Type type) {
        this.timestamp = LocalDateTime.now().format(FMT);
        this.senderId  = senderId;
        this.targetId  = targetId;
        this.content   = content;
        this.type      = type;
    }

    // getters for all fields (immutable, so no setters)
    public String getTimestamp() { return timestamp; }
    public String getSenderId()  { return senderId; }
    public String getTargetId()  { return targetId; }
    public String getContent()   { return content; }
    public Type   getType()      { return type; }

    // returns a human-readable line used in chat display and server console
    public String format() {
        return "[" + timestamp + "] " + content;
    }

    @Override
    public String toString() {
        return "MessageRecord{ts=" + timestamp + ", from=" + senderId
             + ", to=" + targetId + ", type=" + type + "}";
    }
}
