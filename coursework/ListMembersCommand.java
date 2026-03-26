package coursework;

/**
 * Sends the full member list (IDs, IPs, ports, coordinator flag) to the requesting client.
 * Design pattern: Command (concrete command).
 */
public class ListMembersCommand implements ChatCommand {

    @Override
    public void execute(String clientId, ServerState state, MessageLogger logger) {
        String wire = Protocol.MEMBER_LIST + " " + state.getMemberListWire()
                    + " COORDINATOR:" + state.getCoordinatorId();
        state.sendTo(clientId, wire);
    }
}
