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
 * singleton holding all server-side shared state: connected members, writers,
 * coordinator pointer, and ping-acknowledgement flags
 *
 * also acts as the Observable (subject) in the Observer pattern,
 * notifying registered {@link GroupEventListener}s of membership changes and coordinator elections
 *
 * design patterns: Singleton, Observer (subject) 
 */
public class ServerState {

    private static volatile ServerState instance;

    // maps clientId -> clientInfo
    private final ConcurrentHashMap<String, ClientInfo>  clients  = new ConcurrentHashMap<>();
    // maps clientId -> printWriter for the client's socket output stream
    private final ConcurrentHashMap<String, PrintWriter> writers  = new ConcurrentHashMap<>();
    // tracks whether each client has responded to the last PING
    private final ConcurrentHashMap<String, Boolean>     pongAcks = new ConcurrentHashMap<>();
    // ID of the current coordinator, or null if no members are present
    private volatile String coordinatorId = null;
    // registered listeners to be notified of group events
    private final CopyOnWriteArrayList<GroupEventListener> listeners = new CopyOnWriteArrayList<>();
    // private constructor to enforce singleton pattern
    private ServerState() {}
    // double-checked locking for thread-safe lazy initialisation
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

    // registration and deregistration of clients, with automatic coordinator election and listener notifications

    /**
     * registers a new client. If the ID is already taken, returns false and does not register
     * the first client to register automatically becomes coordinator and is notified as such in the returned ClientInfo. Notifies listeners of the new member and coordinator status if applicable
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
     * removes a client. if the removed client was the coordinator,
     * automatically elects a new one and notifies all listeners
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

    // coordinator election algorithm: elect the member who joined earliest (most senior)
    private void electNewCoordinator() {
        if (clients.isEmpty()) {
            coordinatorId = null;
            return;
        }
        // elect the member who joined earliest (most senior)
        ClientInfo next = clients.values().stream()
            .min(Comparator.comparing(ClientInfo::getJoinedAt))
            .orElse(null);

        if (next != null) {
            coordinatorId = next.getId();
            // update stored ClientInfo to reflect new coordinator flag
            clients.put(next.getId(), next.asCoordinator(true));
            notifyCoordinatorChanged(coordinatorId);
        }
    }
    // helper method to check if a given client ID is the current coordinator
    public boolean isCoordinator(String id) {
        return id.equals(coordinatorId);
    }
    // getter for coordinator ID, used in tests and by listeners
    public String getCoordinatorId() {
        return coordinatorId;
    }

    // messaging helpers to send messages to clients based on the current state
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
    // sends a message to a specific client, returning true if successful or false if the target client does not exist
    public boolean sendTo(String targetId, String message) {
        PrintWriter w = writers.get(targetId);
        if (w != null) {
            w.println(message);
            return true;
        }
        return false;
    }

    // ping/pong tracking
    public synchronized void markPingSent() {
        pongAcks.replaceAll((id, v) -> false);
    }

    public void acknowledgePong(String id) {
        pongAcks.put(id, true);
    }

    /** returns IDs of clients that did not respond to the last ping */
    public List<String> getUnresponsiveClients() {
        return pongAcks.entrySet().stream()
            .filter(e -> !e.getValue())
            .map(e -> e.getKey())
            .collect(Collectors.toList());
    }

    // getter methods for client info and member lists, used by commands and listeners
    public ClientInfo getClient(String id) {
        return clients.get(id);
    }

    // checks if a client with the given ID is currently registered
    public boolean hasClient(String id) {
        return clients.containsKey(id);
    }

    // returns the number of currently connected clients
    public int size() {
        return clients.size();
    }
    
    // returns an unmodifiable list of all current members, sorted by join time (oldest first)
    public List<ClientInfo> getMemberList() {
        return Collections.unmodifiableList(new ArrayList<>(clients.values()));
    }

    // returns a comma-separated string of all current members in the format "id:displayName[:coordinator]", used in the LIST command response
    public String getMemberListWire() {
        return clients.values().stream()
            .map(ClientInfo::toWireString)
            .collect(Collectors.joining(","));
    }

    // observer pattern – listener management and notification methods
    public void addListener(GroupEventListener l) {
        listeners.add(l);
    }

    // removes a listener so it will no longer receive notifications of group events
    public void removeListener(GroupEventListener l) {
        listeners.remove(l);
    }

    // notification methods to inform listeners of membership changes and coordinator elections
    private void notifyJoined(ClientInfo member) {
        listeners.forEach(l -> l.onMemberJoined(member));
    }

    // notifies listeners that a member has left, providing the ID of the departed member
    private void notifyLeft(String id) {
        listeners.forEach(l -> l.onMemberLeft(id));
    }

    // notifies listeners that the coordinator has changed, providing the ID of the new coordinator
    private void notifyCoordinatorChanged(String newId) {
        listeners.forEach(l -> l.onCoordinatorChanged(newId));
    }

    // test support method to clear all state, used in unit tests to ensure isolation between test cases

    /** resets all state. for use in unit tests only */
    public synchronized void reset() {
        clients.clear();
        writers.clear();
        pongAcks.clear();
        coordinatorId = null;
        listeners.clear();
    }
}
