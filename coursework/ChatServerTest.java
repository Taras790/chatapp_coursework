package coursework;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JUnit 5 tests covering the core requirements of chosen task (1):
 * group formation, coordinator election, fault tolerance, and messaging.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ChatServerTest {

    private ServerState   state;
    private MessageLogger logger;

    @BeforeEach
    void setUp() {
        ServerState.getInstance().reset();
        MessageLogger.getInstance().clear();
        state  = ServerState.getInstance();
        logger = MessageLogger.getInstance();
    }

    @AfterEach
    void tearDown() {
        state.reset();
        logger.clear();
    }

    // helper method to create a dummy PrintWriter for testing registration without actual sockets
    private PrintWriter dummyWriter() {
        return new PrintWriter(new StringWriter(), true);
    }
    // helper method to add a member with a given ID, returns true if successful, false if ID is duplicate
    private boolean addMember(String id) {
        return state.register(new ClientInfo(id, "127.0.0.1", 9000), dummyWriter());
    }

    // group formation and membership management tests
    @Test
    @Order(1)
    @DisplayName("First member to join becomes coordinator")
    void firstMemberIsCoordinator() {
        addMember("alice");
        assertEquals("alice", state.getCoordinatorId());
        assertTrue(state.isCoordinator("alice"));
    }

    @Test
    @Order(2)
    @DisplayName("Second member is NOT the coordinator")
    void secondMemberIsNotCoordinator() {
        addMember("alice");
        addMember("bob");
        assertFalse(state.isCoordinator("bob"));
    }

    @Test
    @Order(3)
    @DisplayName("Duplicate ID is rejected")
    void duplicateIdRejected() {
        assertTrue(addMember("alice"));
        assertFalse(addMember("alice"));
        assertEquals(1, state.size());
    }

    @Test
    @Order(4)
    @DisplayName("Member count is accurate after joins and leaves")
    void memberCount() {
        addMember("alice");
        addMember("bob");
        addMember("charlie");
        assertEquals(3, state.size());
        state.deregister("bob");
        assertEquals(2, state.size());
    }

    // coordinator election (fault tolerance) tests
    @Test
    @Order(5)
    @DisplayName("New coordinator elected when coordinator leaves")
    void coordinatorElectedOnLeave() {
        addMember("alice");
        addMember("bob");
        assertEquals("alice", state.getCoordinatorId());

        state.deregister("alice");

        assertNotNull(state.getCoordinatorId());
        assertEquals("bob", state.getCoordinatorId());
    }

    @Test
    @Order(6)
    @DisplayName("No coordinator when last member leaves")
    void noCoordinatorWhenEmpty() {
        addMember("alice");
        state.deregister("alice");
        assertNull(state.getCoordinatorId());
        assertEquals(0, state.size());
    }

    @Test
    @Order(7)
    @DisplayName("Coordinator election continues after multiple leaves")
    void multipleLeaves() {
        addMember("alice");
        addMember("bob");
        addMember("charlie");

        state.deregister("alice");  // bob or charlie elected
        String first = state.getCoordinatorId();
        assertNotNull(first);

        state.deregister(first);    // remaining member elected
        String second = state.getCoordinatorId();
        assertNotNull(second);
        assertNotEquals(first, second);
    }

    // messaging tests
    @Test
    @Order(8)
    @DisplayName("Broadcast reaches all members")
    void broadcastReachesAll() {
        StringWriter sw1 = new StringWriter();
        StringWriter sw2 = new StringWriter();
        state.register(new ClientInfo("alice", "127.0.0.1", 9001), new PrintWriter(sw1, true));
        state.register(new ClientInfo("bob",   "127.0.0.1", 9002), new PrintWriter(sw2, true));

        state.broadcast("Hello everyone");

        assertTrue(sw1.toString().contains("Hello everyone"));
        assertTrue(sw2.toString().contains("Hello everyone"));
    }

    @Test
    @Order(9)
    @DisplayName("sendTo reaches only the target")
    void sendToOnlyTarget() {
        StringWriter sw1 = new StringWriter();
        StringWriter sw2 = new StringWriter();
        state.register(new ClientInfo("alice", "127.0.0.1", 9001), new PrintWriter(sw1, true));
        state.register(new ClientInfo("bob",   "127.0.0.1", 9002), new PrintWriter(sw2, true));

        state.sendTo("bob", "secret");

        assertFalse(sw1.toString().contains("secret"));
        assertTrue(sw2.toString().contains("secret"));
    }

    @Test
    @Order(10)
    @DisplayName("BroadcastCommand logs a message record")
    void broadcastCommandLogs() {
        addMember("alice");
        new BroadcastCommand("hello world").execute("alice", state, logger);
        assertEquals(1, logger.size());
        assertEquals("alice", logger.getAll().get(0).getSenderId());
    }

    @Test
    @Order(11)
    @DisplayName("PrivateMessageCommand delivers to target only")
    void privateMessageDelivery() {
        StringWriter swAlice = new StringWriter();
        StringWriter swBob   = new StringWriter();
        state.register(new ClientInfo("alice", "127.0.0.1", 9001), new PrintWriter(swAlice, true));
        state.register(new ClientInfo("bob",   "127.0.0.1", 9002), new PrintWriter(swBob,   true));

        new PrivateMessageCommand("bob", "hi bob").execute("alice", state, logger);

        assertTrue(swBob.toString().contains("hi bob"));
        assertFalse(swAlice.toString().contains(Protocol.PRIVATE + " "));
    }

    @Test
    @Order(12)
    @DisplayName("PrivateMessageCommand returns error for unknown target")
    void privateMessageUnknownTarget() {
        StringWriter swAlice = new StringWriter();
        state.register(new ClientInfo("alice", "127.0.0.1", 9001), new PrintWriter(swAlice, true));

        new PrivateMessageCommand("nobody", "hey").execute("alice", state, logger);

        assertTrue(swAlice.toString().contains(Protocol.ERROR));
    }

    // ping/pong (fault tolerance) tests
    @Test
    @Order(13)
    @DisplayName("Unresponsive client detected after ping")
    void pingDetectsUnresponsiveClient() {
        addMember("alice");
        state.markPingSent();

        assertTrue(state.getUnresponsiveClients().contains("alice"));
    }

    @Test
    @Order(14)
    @DisplayName("Responsive client cleared after pong")
    void pongClearsUnresponsive() {
        addMember("alice");
        state.markPingSent();
        state.acknowledgePong("alice");

        assertFalse(state.getUnresponsiveClients().contains("alice"));
    }

    // observer pattern tests
    @Test
    @Order(15)
    @DisplayName("GroupEventListener notified on member join")
    void observerNotifiedOnJoin() {
        boolean[] notified = {false};
        state.addListener(new GroupEventListener() {
            @Override public void onMemberJoined(ClientInfo m)         { notified[0] = true; }
            @Override public void onMemberLeft(String id)              {}
            @Override public void onCoordinatorChanged(String newId)   {}
        });
        addMember("alice");
        assertTrue(notified[0]);
    }

    @Test
    @Order(16)
    @DisplayName("GroupEventListener notified on coordinator change")
    void observerNotifiedOnCoordinatorChange() {
        String[] newCoord = {null};
        state.addListener(new GroupEventListener() {
            @Override public void onMemberJoined(ClientInfo m)        {}
            @Override public void onMemberLeft(String id)             {}
            @Override public void onCoordinatorChanged(String newId)  { newCoord[0] = newId; }
        });
        addMember("alice");
        addMember("bob");
        state.deregister("alice");

        assertEquals("bob", newCoord[0]);
    }

    // commandfactory tests
    @Test
    @Order(17)
    @DisplayName("CommandFactory parses BROADCAST command")
    void parsesBroadcast() {
        ChatCommand cmd = CommandFactory.parse("BROADCAST hello");
        assertInstanceOf(BroadcastCommand.class, cmd);
    }

    @Test
    @Order(18)
    @DisplayName("CommandFactory parses PRIVMSG command")
    void parsesPrivMsg() {
        ChatCommand cmd = CommandFactory.parse("PRIVMSG bob hi there");
        assertInstanceOf(PrivateMessageCommand.class, cmd);
    }

    @Test
    @Order(19)
    @DisplayName("CommandFactory parses LIST command")
    void parsesList() {
        ChatCommand cmd = CommandFactory.parse("LIST");
        assertInstanceOf(ListMembersCommand.class, cmd);
    }

    @Test
    @Order(20)
    @DisplayName("CommandFactory parses QUIT command")
    void parsesQuit() {
        ChatCommand cmd = CommandFactory.parse("QUIT");
        assertInstanceOf(QuitCommand.class, cmd);
    }

    // messagelogger (singleton) tests
    @Test
    @Order(21)
    @DisplayName("MessageLogger singleton returns same instance")
    void loggerSingleton() {
        assertSame(MessageLogger.getInstance(), MessageLogger.getInstance());
    }

    @Test
    @Order(22)
    @DisplayName("MessageLogger records and retrieves entries")
    void loggerRecords() {
        logger.log(new MessageRecord("alice", null, "hello", MessageRecord.Type.BROADCAST));
        logger.log(new MessageRecord("bob",   null, "world", MessageRecord.Type.BROADCAST));

        assertEquals(2, logger.size());
        assertEquals(1, logger.getBySender("alice").size());
    }
}
