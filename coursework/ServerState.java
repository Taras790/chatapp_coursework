package coursework;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Singleton holding all server-side shared state: connected members, writers,
 * coordinator pointer, and ping-acknowledgement flags.
 *
 * Also acts as the Observable (subject) in the Observer pattern,
 * notifying registered {@link GroupEventListener}s of membership changes.
 *
 * Design patterns: Singleton, Observer (subject).
 */
public class ServerState {

    private static volatile ServerState instance;

    /** Maps clientId -> ClientInfo */
    private final ConcurrentHashMap<String, ClientInfo>  clients  = new ConcurrentHashMap<>();
    /** Maps clientId -> PrintWriter for the client's socket output stream */
    private final ConcurrentHashMap<String, PrintWriter> writers  = new ConcurrentHashMap<>();
    /** Tracks whether each client has responded to the last PING */
    private final ConcurrentHashMap<String, Boolean>     pongAcks = new ConcurrentHashMap<>();

    private volatile String coordinatorId = null;

    private final CopyOnWriteArrayList<GroupEventListener> listeners = new CopyOnWriteArrayList<>();

    private ServerState() {}

    public static ServerState getInstance() {
        if (instance == null) {
            synchronized (ServerState.class) {
                if (instance == null) {
                    instance = new ServerState();
                }
            }
        }
        return instance;
    }

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    /**
     * Registers a new client. If the ID is already taken, returns false.
     * The first client to register automatically becomes coordinator.
     */
    public synchronized boolean register(ClientInfo info, PrintWriter writer) {
        if (clients.containsKey(info.getId())) {
            return false;
        }
        boolean isFirst = clients.isEmpty();
        ClientInfo stored = isFirst ? info.asCoordinator(true) : info;
        clients.put(stored.getId(), stored);
        writers.put(stored.getId(), writer);
        pongAcks.put(stored.getId(), true);

        if (isFirst) {
            coordinatorId = stored.getId();
        }

        notifyJoined(stored);
        return true;
    }

    /**
     * Removes a client. If the removed client was the coordinator,
     * automatically elects a new one and notifies all listeners.
     */
    public synchronized void deregister(String id) {
        ClientInfo removed = clients.remove(id);
        writers.remove(id);
        pongAcks.remove(id);

        if (removed == null) return;

        notifyLeft(id);

        if (id.equals(coordinatorId)) {
            electNewCoordinator();
        }
    }

    // -------------------------------------------------------------------------
    // Coordinator election
    // -------------------------------------------------------------------------

    private void electNewCoordinator() {
        if (clients.isEmpty()) {
            coordinatorId = null;
            return;
        }
        // Elect the member who joined earliest (most senior)
        ClientInfo next = clients.values().stream()
            .min(Comparator.comparing(ClientInfo::getJoinedAt))
            .orElse(null);

        if (next != null) {
            coordinatorId = next.getId();
            // Update stored ClientInfo to reflect new coordinator flag
            clients.put(next.getId(), next.asCoordinator(true));
            notifyCoordinatorChanged(coordinatorId);
        }
    }

    public boolean isCoordinator(String id) {
        return id.equals(coordinatorId);
    }

    public String getCoordinatorId() {
        return coordinatorId;
    }

    // -------------------------------------------------------------------------
    // Messaging helpers
    // -------------------------------------------------------------------------

    public void broadcast(String message) {
        writers.values().forEach(w -> w.println(message));
    }

    public void broadcastExcept(String message, String excludeId) {
        clients.keySet().stream()
            .filter(id -> !id.equals(excludeId))
            .map(writers::get)
            .filter(w -> w != null)
            .forEach(w -> w.println(message));
    }

    public boolean sendTo(String targetId, String message) {
        PrintWriter w = writers.get(targetId);
        if (w != null) {
            w.println(message);
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Ping / Pong tracking
    // -------------------------------------------------------------------------

    public synchronized void markPingSent() {
        pongAcks.replaceAll((id, v) -> false);
    }

    public void acknowledgePong(String id) {
        pongAcks.put(id, true);
    }

    /** Returns IDs of clients that did not respond to the last ping. */
    public List<String> getUnresponsiveClients() {
        return pongAcks.entrySet().stream()
            .filter(e -> !e.getValue())
            .map(e -> e.getKey())
            .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    public ClientInfo getClient(String id) {
        return clients.get(id);
    }

    public boolean hasClient(String id) {
        return clients.containsKey(id);
    }

    public int size() {
        return clients.size();
    }

    public List<ClientInfo> getMemberList() {
        return Collections.unmodifiableList(new ArrayList<>(clients.values()));
    }

    public String getMemberListWire() {
        return clients.values().stream()
            .map(ClientInfo::toWireString)
            .collect(Collectors.joining(","));
    }

    // -------------------------------------------------------------------------
    // Observer pattern – listener management
    // -------------------------------------------------------------------------

    public void addListener(GroupEventListener l) {
        listeners.add(l);
    }

    public void removeListener(GroupEventListener l) {
        listeners.remove(l);
    }

    private void notifyJoined(ClientInfo member) {
        listeners.forEach(l -> l.onMemberJoined(member));
    }

    private void notifyLeft(String id) {
        listeners.forEach(l -> l.onMemberLeft(id));
    }

    private void notifyCoordinatorChanged(String newId) {
        listeners.forEach(l -> l.onCoordinatorChanged(newId));
    }

    // -------------------------------------------------------------------------
    // Test support
    // -------------------------------------------------------------------------

    /** Resets all state. For use in unit tests only. */
    public synchronized void reset() {
        clients.clear();
        writers.clear();
        pongAcks.clear();
        coordinatorId = null;
        listeners.clear();
    }
}
