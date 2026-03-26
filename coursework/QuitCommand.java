package coursework;

/**
 * Signals that the client wishes to disconnect.
 * The Handler checks this command type to break the read loop.
 * Design pattern: Command (concrete command).
 */
public class QuitCommand implements ChatCommand {

    @Override
    public void execute(String clientId, ServerState state, MessageLogger logger) {
        // Intentionally empty — the Handler detects QuitCommand and exits the loop.
    }
}
