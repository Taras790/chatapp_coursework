package coursework;

/**
 * signals that the client wishes to disconnect
 * the handler checks this command type to break the read loop
 * design pattern: Command (concrete command)
 */
public class QuitCommand implements ChatCommand {

    @Override
    public void execute(String clientId, ServerState state, MessageLogger logger) {
        // intentionally empty — the Handler detects QuitCommand and exits the loop
    }
}
