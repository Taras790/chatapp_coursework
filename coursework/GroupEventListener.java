package coursework;

/**
 * observer interface for group membership events
 * implementations are notified when members join, leave, or the coordinator changes
 *
 * design pattern: Observer (subscriber side)
 */
public interface GroupEventListener {

    void onMemberJoined(ClientInfo member);

    void onMemberLeft(String memberId);

    void onCoordinatorChanged(String newCoordinatorId);
}
