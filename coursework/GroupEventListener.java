package coursework;

/**
 * Observer interface for group membership events.
 * Implementations are notified when members join, leave, or the coordinator changes.
 *
 * Design pattern: Observer (subscriber side).
 */
public interface GroupEventListener {

    void onMemberJoined(ClientInfo member);

    void onMemberLeft(String memberId);

    void onCoordinatorChanged(String newCoordinatorId);
}
