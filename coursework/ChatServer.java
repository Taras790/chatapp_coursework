package coursework;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * multithreaded chat room server supporting coordinator election,
 * periodic pings, private messaging, member lists, and fault tolerance
 *
 * implements {@link GroupEventListener} (Observer pattern) to react to
 * membership changes and broadcast the appropriate protocol messages
 *
 * uses {@link ServerState} (Singleton) for shared state and
 * {@link CommandFactory} + {@link ChatCommand} (Command pattern)
 * to dispatch client messages without a long if-else chain
 */
public class ChatServer implements GroupEventListener {

    private final ServerState   state;
    private final MessageLogger logger;
    private final PingScheduler pinger;

    // constructor initialises shared state, logger, pinger, and registers as listener for group events
    public ChatServer() {
        this.state  = ServerState.getInstance();
        this.logger = MessageLogger.getInstance();
        this.pinger = new PingScheduler(state);
        state.addListener(this);
    }

    // starts the server on the given port, accepting clients in a loop
    public void start(int port) throws IOException {
        pinger.start();
        ExecutorService pool = Executors.newFixedThreadPool(500);
        System.out.println("Chat server running on port " + port + " ...");
        try (ServerSocket listener = new ServerSocket(port)) {
            while (true) {
                pool.execute(new Handler(listener.accept(), state, logger));
            }
        }
    }


    // groupEventListener — observer callbacks fired by ServerState
    @Override
    public void onMemberJoined(ClientInfo member) {
        String msg = Protocol.MEMBER_JOINED + " " + member.getId()
                   + " " + member.getIpAddress() + ":" + member.getPort();
        state.broadcastExcept(msg, member.getId());
        System.out.println("[Server] " + member.getId() + " joined ("
                + member.getIpAddress() + ":" + member.getPort() + ")");
    }

    // member left event: broadcast MEMBER_LEFT clientId to all remaining clients
    @Override
    public void onMemberLeft(String memberId) {
        state.broadcast(Protocol.MEMBER_LEFT + " " + memberId);
        System.out.println("[Server] " + memberId + " left");
    }

    // coordinator changed event: broadcast COORDINATOR_CHANGED newCoordId to all clients,
    // and send COORDINATOR_YOU to the new coordinator
    @Override
    public void onCoordinatorChanged(String newCoordinatorId) {
        state.broadcast(Protocol.COORDINATOR_CHANGED + " " + newCoordinatorId);
        state.sendTo(newCoordinatorId, Protocol.COORDINATOR_YOU);
        System.out.println("[Server] New coordinator: " + newCoordinatorId);
    }

    // entry point and main loop
    public static void main(String[] args) throws Exception {
        int port = Protocol.DEFAULT_PORT;
        if (args.length > 0) {
            try { port = Integer.parseInt(args[0]); }
            catch (NumberFormatException e) { /* use default */ }
        }
        new ChatServer().start(port);
    }

    // inner class: Handler

    /**
     * this is handles one connected client on its own thread.
     * negotiates the client ID, informs about the coordinator,
     * then drives the Command-pattern dispatch loop
     */
    static class Handler implements Runnable {

        private final Socket        socket;
        private final ServerState   state;
        private final MessageLogger logger;

        private String      clientId;
        private Scanner     in;
        private PrintWriter out;

        Handler(Socket socket, ServerState state, MessageLogger logger) {
            this.socket = socket;
            this.state  = state;
            this.logger = logger;
        }
        // main loop for this client connection: negotiate ID, inform about coordinator, then dispatch commands
        @Override
        public void run() {
            try {
                in  = new Scanner(socket.getInputStream());
                out = new PrintWriter(socket.getOutputStream(), true);

                // --- Phase 1: ID negotiation ---
                while (true) {
                    out.println(Protocol.SUBMITNAME);
                    if (!in.hasNextLine()) return;
                    String candidate = in.nextLine().trim();
                    if (candidate.isEmpty()) continue;

                    if (state.hasClient(candidate)) {
                        out.println(Protocol.NAME_TAKEN);
                        continue;
                    }

                    String     ip   = socket.getInetAddress().getHostAddress();
                    int        port = socket.getPort();
                    ClientInfo info = new ClientInfo(candidate, ip, port);

                    if (state.register(info, out)) {
                        clientId = candidate;
                        break;
                    }
                    out.println(Protocol.NAME_TAKEN);
                }

                // the next phase (2) confirm and inform about coordinator
                out.println(Protocol.NAMEACCEPTED + " " + clientId);
                if (state.isCoordinator(clientId)) {
                    out.println(Protocol.COORDINATOR_YOU);
                } else {
                    out.println(Protocol.COORDINATOR_IS + " " + state.getCoordinatorId());
                }

                logger.log(new MessageRecord(clientId, null,
                        clientId + " joined the group", MessageRecord.Type.SYSTEM));

                // lastly phase (3) command dispatch loop
                while (in.hasNextLine()) {
                    String     line = in.nextLine();
                    ChatCommand cmd  = CommandFactory.parse(line);
                    if (cmd instanceof QuitCommand) break;
                    cmd.execute(clientId, state, logger);
                }

            } catch (Exception e) {
                System.out.println("[Handler] Error for " + clientId + ": " + e.getMessage());
            } finally {
                cleanup();
            }
        }

        // helper method to clean up on client disconnect: deregister from state, log the event, and close the socket
        private void cleanup() {
            if (clientId != null) {
                logger.log(new MessageRecord(clientId, null,
                        clientId + " left the group", MessageRecord.Type.SYSTEM));
                state.deregister(clientId);
            }
            try { socket.close(); } catch (IOException e) { /* ignore */ }
        }
    }
}
