package coursework;

/**
 * Parses raw client input strings into {@link ChatCommand} objects.
 * Centralises all parsing logic and eliminates if-else chains in the Handler.
 *
 * Design pattern: Command (invoker-side factory).
 */
public class CommandFactory {

    private CommandFactory() {}

    public static ChatCommand parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new QuitCommand();
        }

        String line = raw.trim();

        if (line.equals(Protocol.QUIT)) {
            return new QuitCommand();
        }

        if (line.equals(Protocol.PONG)) {
            return new PongCommand();
        }

        if (line.equals(Protocol.LIST)) {
            return new ListMembersCommand();
        }

        if (line.startsWith(Protocol.BROADCAST + " ")) {
            return new BroadcastCommand(line.substring(Protocol.BROADCAST.length() + 1));
        }

        if (line.startsWith(Protocol.PRIVMSG + " ")) {
            // Format: PRIVMSG <targetId> <text>
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

        // Treat any unrecognised input as a broadcast (backwards compatibility)
        return new BroadcastCommand(line);
    }
}
