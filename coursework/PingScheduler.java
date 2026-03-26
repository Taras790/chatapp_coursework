package coursework;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Sends a PING to every connected client every 20 seconds.
 * Clients that do not reply with PONG within 5 seconds are considered
 * unresponsive and removed — fulfilling the fault-tolerance requirement
 * for abnormal disconnects.
 */
public class PingScheduler {

    private final ServerState             state;
    private final ScheduledExecutorService scheduler;

    public PingScheduler(ServerState state) {
        this.state     = state;
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    public void start() {
        // Send PING to all clients every 20 seconds
        scheduler.scheduleAtFixedRate(
            this::sendPings,
            Protocol.PING_INTERVAL_SEC,
            Protocol.PING_INTERVAL_SEC,
            TimeUnit.SECONDS
        );

        // 5 seconds after each ping wave, check for non-responders
        scheduler.scheduleAtFixedRate(
            this::checkResponses,
            Protocol.PING_INTERVAL_SEC + Protocol.PONG_TIMEOUT_SEC,
            Protocol.PING_INTERVAL_SEC,
            TimeUnit.SECONDS
        );
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    private void sendPings() {
        if (state.size() == 0) return;
        state.markPingSent();
        state.broadcast(Protocol.PING);
        System.out.println("[PingScheduler] PING sent to " + state.size() + " member(s)");
    }

    private void checkResponses() {
        List<String> unresponsive = state.getUnresponsiveClients();
        for (String id : unresponsive) {
            System.out.println("[PingScheduler] No PONG from '" + id + "' — removing");
            state.deregister(id);
        }
    }
}
