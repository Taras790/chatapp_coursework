package coursework;

/**
 * Sends a private message to a specific member.
 * Design pattern: Command (concrete command).
 */
public class PrivateMessageCommand implements ChatCommand {

    private final String targetId;
    private final String text;

    public PrivateMessageCommand(String targetId, String text) {
        this.targetId = targetId;
        this.text     = text;
    }

    @Override
    public void execute(String clientId, ServerState state, MessageLogger logger) {
        if (!state.hasClient(targetId)) {
            state.sendTo(clientId, Protocol.ERROR + " Member '" + targetId + "' not found");
            return;
        }

        MessageRecord record = new MessageRecord(
            clientId, targetId,
            clientId + " -> " + targetId + ": " + text,
            MessageRecord.Type.PRIVATE
        );
        logger.log(record);

        String toTarget = Protocol.PRIVATE      + " " + record.format();
        String toSender = Protocol.PRIVATE_SENT + " " + record.format();

        state.sendTo(targetId, toTarget);
        state.sendTo(clientId, toSender);
        System.out.println("[PRIVATE] " + record.format());
    }
}
