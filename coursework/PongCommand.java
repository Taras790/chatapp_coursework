package coursework;

/**
 * acknowledges a PING from the server, marking the client as responsive
 * design pattern: Command (concrete command)
 */
public class PongCommand implements ChatCommand {

    @Override
    public void execute(String clientId, ServerState state, MessageLogger logger) {
        state.acknowledgePong(clientId);
    }
}
