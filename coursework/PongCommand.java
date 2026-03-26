package coursework;

/**
 * Acknowledges a PING from the server, marking the client as responsive.
 * Design pattern: Command (concrete command).
 */
public class PongCommand implements ChatCommand {

    @Override
    public void execute(String clientId, ServerState state, MessageLogger logger) {
        state.acknowledgePong(clientId);
    }
}
