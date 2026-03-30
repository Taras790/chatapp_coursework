package coursework;

/**
 * parses raw client input strings into {@link ChatCommand} objects 
 * centralises all parsing logic and eliminates if-else chains in the Handler
 *
 * design pattern: command (invoker-side factory) 
 */
public class CommandFactory {

    private CommandFactory() {}

    public static ChatCommand parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new QuitCommand();
        }

        String line = raw.trim();
        // check for known command patterns in order of precedence
        if (line.equals(Protocol.QUIT)) {
            return new QuitCommand();
        }
        // ping and pong are handled as commands for simplicity, even though they don't have clientId or logging implications
        if (line.equals(Protocol.PONG)) {
            return new PongCommand();
        }
        // list and who have the same syntax but different semantics, so we check them separately
        if (line.equals(Protocol.LIST)) {
            return new ListMembersCommand();
        }
        // note: who command is not implemented in the server, but we can still parse it for completeness
        if (line.startsWith(Protocol.BROADCAST + " ")) {
            return new BroadcastCommand(line.substring(Protocol.BROADCAST.length() + 1));
        }
        // private message format: "PRIVMSG <targetId> <text>"
        if (line.startsWith(Protocol.PRIVMSG + " ")) {
            // format: PRIVMSG <targetId> <text> 
            String rest  = line.substring(Protocol.PRIVMSG.length() + 1);
            int    space = rest.indexOf(' ');
            if (space > 0) {
                return new PrivateMessageCommand(
                    rest.substring(0, space),
                    rest.substring(space + 1)
                );
            }
            return new PrivateMessageCommand(rest, "");
        }

        // treat any unrecognised input as a broadcast (backwards compatibility)
        return new BroadcastCommand(line);
    }
}
