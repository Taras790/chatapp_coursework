package coursework;

/**
 * central repository for all protocol string constants and configuration
 * eliminates magic strings across the codebase and provides a single source of truth for command formats and config values.
 */
public final class Protocol {

    private Protocol() {}

    // server -> client commands
    public static final String SUBMITNAME          = "SUBMITNAME";
    public static final String NAME_TAKEN          = "NAME_TAKEN";
    public static final String NAMEACCEPTED        = "NAMEACCEPTED";
    public static final String COORDINATOR_YOU     = "COORDINATOR_YOU";
    public static final String COORDINATOR_IS      = "COORDINATOR_IS";
    public static final String MESSAGE             = "MESSAGE";
    public static final String PRIVATE             = "PRIVATE";
    public static final String PRIVATE_SENT        = "PRIVATE_SENT";
    public static final String MEMBER_LIST         = "MEMBER_LIST";
    public static final String MEMBER_JOINED       = "MEMBER_JOINED";
    public static final String MEMBER_LEFT         = "MEMBER_LEFT";
    public static final String COORDINATOR_CHANGED = "COORDINATOR_CHANGED";
    public static final String PING                = "PING";
    public static final String ERROR               = "ERROR";

    // client -> server commands
    public static final String PONG      = "PONG";
    public static final String BROADCAST = "BROADCAST";
    public static final String PRIVMSG   = "PRIVMSG";
    public static final String LIST      = "LIST";
    public static final String QUIT      = "QUIT";

    // config constants
    public static final int  DEFAULT_PORT      = 50000;
    public static final long PING_INTERVAL_SEC = 20L;
    public static final long PONG_TIMEOUT_SEC  = 5L;
}
