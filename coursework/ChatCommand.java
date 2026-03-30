package coursework;

/**
 * command interface — the root of the command design pattern for chat commands
 * each client action (broadcast, private message, list, quit, pong)
 * is encapsulated as a concrete implementation of this interface
 *
 * design pattern: Command (command interface)
 */
public interface ChatCommand {

    /**
     * executes the command on behalf of the given client
     *
     * @param clientId the ID of the client issuing the command
     * @param state    shared server state
     * @param logger   shared message log
     */
    void execute(String clientId, ServerState state, MessageLogger logger);
}
