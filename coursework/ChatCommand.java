package coursework;

/**
 * Command interface — the root of the Command design pattern.
 * Each client action (broadcast, private message, list, quit, pong)
 * is encapsulated as a concrete implementation of this interface.
 *
 * Design pattern: Command.
 */
public interface ChatCommand {

    /**
     * Executes the command on behalf of the given client.
     *
     * @param clientId the ID of the client issuing the command
     * @param state    shared server state
     * @param logger   shared message log
     */
    void execute(String clientId, ServerState state, MessageLogger logger);
}
