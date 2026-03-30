package coursework;

import java.time.Instant;

/**
 * immutable value object representing a connected group member
 * used for member-list responses and coordinator election
 */
public class ClientInfo {

    private final String  id;
    private final String  ipAddress;
    private final int     port;
    private final Instant joinedAt;
    private final boolean coordinator;

    public ClientInfo(String id, String ipAddress, int port) {
        this.id          = id;
        this.ipAddress   = ipAddress;
        this.port        = port;
        this.joinedAt    = Instant.now();
        this.coordinator = false;
    }

    private ClientInfo(String id, String ipAddress, int port,
                       Instant joinedAt, boolean coordinator) {
        this.id          = id;
        this.ipAddress   = ipAddress;
        this.port        = port;
        this.joinedAt    = joinedAt;
        this.coordinator = coordinator;
    }

    //** returns a new instance identical to this but with coordinator flag changed. */
    public ClientInfo asCoordinator(boolean value) {
        return new ClientInfo(id, ipAddress, port, joinedAt, value);
    }

    public String  getId()         { return id; }
    public String  getIpAddress()  { return ipAddress; }
    public int     getPort()       { return port; }
    public Instant getJoinedAt()   { return joinedAt; }
    public boolean isCoordinator() { return coordinator; }

    /** wire format used in MEMBER_LIST messages: {@code id|ip|port|isCoordinator} */
    public String toWireString() {
        return id + "|" + ipAddress + "|" + port + "|" + coordinator;
    }

    public static ClientInfo fromWireString(String s) {
        String[] p = s.split("\\|");
        ClientInfo ci = new ClientInfo(p[0], p[1], Integer.parseInt(p[2]));
        return ci.asCoordinator(Boolean.parseBoolean(p[3]));
    }

    @Override
    public String toString() {
        return id + " (" + ipAddress + ":" + port + ")" + (coordinator ? " [COORD]" : "");
    }
}
