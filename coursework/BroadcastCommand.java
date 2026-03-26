package coursework;

/**
 * Broadcasts a chat message to every connected member.
 * Design pattern: Command (concrete command).
 */
public class BroadcastCommand implements ChatCommand {

    private final String text;

    public BroadcastCommand(String text) {
        this.text = text;
    }

    @Override
    public void execute(String clientId, ServerState state, MessageLogger logger) {
        MessageRecord record = new MessageRecord(
            clientId, null,
            clientId + ": " + text,
            MessageRecord.Type.BROADCAST
        );
        logger.log(record);

        String wire = Protocol.MESSAGE + " " + record.format();
        state.broadcast(wire);
        System.out.println(record.format());
    }
}
