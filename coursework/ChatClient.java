package coursework;

/** swing based chat client for connecting to the chat server */
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 *  swing based chat client that supports: 
 * - unique user client ID entry at startup
 * - optional server IP and port as command-line arguments
 * - coordinator status display
 * - broadcast messaging
 * - private messaging via (@targetId message)
 * - member list request via (/list)
 * - periodic PING/PONG handling
 * - quit button for disconnect
 */
public class ChatClient {

    private final String serverAddress;
    private final int    serverPort;

    private String      clientId;
    private PrintWriter out;
    private Scanner     in;

    // --- graphical user interface setup ---
    private final JFrame    frame        = new JFrame("Chat Client");
    private final JTextArea messageArea  = new JTextArea(18, 55);
    private final JTextField inputField  = new JTextField(40);
    private final JLabel    statusLabel  = new JLabel("Not connected");
    private final JButton   sendButton   = new JButton("Send");
    private final JButton   listButton   = new JButton("List Members");
    private final JButton   quitButton   = new JButton("Quit");

    public ChatClient(String serverAddress, int serverPort) {
        this.serverAddress = serverAddress;
        this.serverPort    = serverPort;
        buildGui();
    }

    // graphical user interface (GUI) construction and event handling
    private void buildGui() {
        messageArea.setEditable(false);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        inputField.setEditable(false);

        // status bar at the (top)
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        statusLabel.setForeground(Color.DARK_GRAY);

        // button panel released to the right of the input field
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttonPanel.add(listButton);
        buttonPanel.add(quitButton);

        // bottom panel that defines input + buttons
        JPanel bottomPanel = new JPanel(new BorderLayout(4, 0));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        bottomPanel.add(inputField,   BorderLayout.CENTER);
        bottomPanel.add(sendButton,   BorderLayout.EAST);
        bottomPanel.add(buttonPanel,  BorderLayout.SOUTH);

        frame.getContentPane().add(statusLabel,              BorderLayout.NORTH);
        frame.getContentPane().add(new JScrollPane(messageArea), BorderLayout.CENTER);
        frame.getContentPane().add(bottomPanel,              BorderLayout.SOUTH);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        // enter or send button functionality
        inputField.addActionListener(e -> sendMessage());
        sendButton.addActionListener(e -> sendMessage());

        // list members button functionality
        listButton.addActionListener(e -> {
            if (out != null) out.println(Protocol.LIST);
        });

        // quit button or disconnect from server functionality
        quitButton.addActionListener(e -> quit());

        // window close as same as quit functionality
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                quit();
            }
        });
    }

    // send message to server based on input field content and clear it
    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || out == null) return;

        if (text.startsWith("@")) {
            // private message (direct) @targetId message text with conditions
            int spaceIdx = text.indexOf(' ');
            if (spaceIdx > 1) {
                String targetId = text.substring(1, spaceIdx);
                String msg      = text.substring(spaceIdx + 1).trim();
                out.println(Protocol.PRIVMSG + " " + targetId + " " + msg);
            } else {
                appendLine("Usage: @targetId message");
            }
        } else if (text.equalsIgnoreCase("/list")) {
            out.println(Protocol.LIST);
        } else {
            out.println(Protocol.BROADCAST + " " + text);
        }
        inputField.setText("");
    }

    private void quit() {
        if (out != null) out.println(Protocol.QUIT);
        frame.dispose();
        System.exit(0);
    }


    // id entry dialog and validation
    private String askId() {
        String id = null;
        while (id == null || id.trim().isEmpty()) {
            id = JOptionPane.showInputDialog(
                frame,
                "Enter your unique ID:",
                "Choose an ID",
                JOptionPane.PLAIN_MESSAGE
            );
            if (id == null) System.exit(0); // user cancelled the dialog
        }
        return id.trim();
    }

    // network read loop and server message handling

    private void run() throws IOException {
        try (Socket socket = new Socket(serverAddress, serverPort)) {
            in  = new Scanner(socket.getInputStream());
            out = new PrintWriter(socket.getOutputStream(), true);

            while (in.hasNextLine()) {
                String line = in.nextLine();
                handleServerMessage(line);
            }
        } finally {
            SwingUtilities.invokeLater(() -> {
                appendLine("--- Disconnected from server ---");
                inputField.setEditable(false);
            });
        }
    }

    // handle messages from the server based on the defined protocol
    private void handleServerMessage(String line) {
        if (line.equals(Protocol.SUBMITNAME)) {
            out.println(askId());

        // server response to name submission: either NAME_TAKEN or NAMEACCEPTED id
        } else if (line.equals(Protocol.NAME_TAKEN)) {
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(frame,
                    "That ID is already taken. Please choose another.",
                    "ID Taken", JOptionPane.WARNING_MESSAGE));

        // successful name acceptance with assigned client ID
        } else if (line.startsWith(Protocol.NAMEACCEPTED + " ")) {
            clientId = line.substring(Protocol.NAMEACCEPTED.length() + 1).trim();
            SwingUtilities.invokeLater(() -> {
                inputField.setEditable(true);
                frame.setTitle("Chat - " + clientId);
                appendLine("--- Connected as " + clientId + " ---");
                appendLine("Tip: use @targetId msg for private messages, /list to see members");
            });

        // coordinator status messages from server
        } else if (line.equals(Protocol.COORDINATOR_YOU)) {
            SwingUtilities.invokeLater(() -> {
                setStatus(clientId + "  [COORDINATOR]", Color.decode("#006400"));
                appendLine("*** You are the group coordinator ***");
            });

        // coordinator announcement from server with format: COORDINATOR_IS coordId
        } else if (line.startsWith(Protocol.COORDINATOR_IS + " ")) {
            String coord = line.substring(Protocol.COORDINATOR_IS.length() + 1).trim();
            SwingUtilities.invokeLater(() -> {
                setStatus(clientId, Color.DARK_GRAY);
                appendLine("*** Coordinator: " + coord + " ***");
            });

        // coordinator change notification from server with format: COORDINATOR_CHANGED newCoordId
        } else if (line.startsWith(Protocol.COORDINATOR_CHANGED + " ")) {
            String newCoord = line.substring(Protocol.COORDINATOR_CHANGED.length() + 1).trim();
            SwingUtilities.invokeLater(() -> {
                if (newCoord.equals(clientId)) {
                    setStatus(clientId + "  [COORDINATOR]", Color.decode("#006400"));
                    appendLine("*** You are now the coordinator ***");
                } else {
                    setStatus(clientId, Color.DARK_GRAY);
                    appendLine("*** New coordinator: " + newCoord + " ***");
                }
            });

        // PING/PONG handling for connection health check
        } else if (line.equals(Protocol.PING)) {
            out.println(Protocol.PONG); // reply immediately

        // other message types with content
        } else if (line.startsWith(Protocol.MEMBER_LIST + " ")) {
            String data = line.substring(Protocol.MEMBER_LIST.length() + 1);
            SwingUtilities.invokeLater(() -> showMemberList(data));

        // broadcast message from server (from another client or self) with format: MESSAGE senderId: text
        } else if (line.startsWith(Protocol.MESSAGE + " ")) {
            String text = line.substring(Protocol.MESSAGE.length() + 1);
            SwingUtilities.invokeLater(() -> appendLine(text));


        // private message received from server with format: PRIVATE senderId: text
        } else if (line.startsWith(Protocol.PRIVATE + " ")) {
            String text = line.substring(Protocol.PRIVATE.length() + 1);
            SwingUtilities.invokeLater(() -> appendLine("[PRIVATE] " + text));

        // private message sent confirmation from server with format: PRIVATE_SENT targetId: text
        } else if (line.startsWith(Protocol.PRIVATE_SENT + " ")) {
            String text = line.substring(Protocol.PRIVATE_SENT.length() + 1);
            SwingUtilities.invokeLater(() -> appendLine("[PRIVATE SENT] " + text));


        // member joined or left notifications with format: MEMBER_JOINED info or MEMBER_LEFT who
        } else if (line.startsWith(Protocol.MEMBER_JOINED + " ")) {
            String info = line.substring(Protocol.MEMBER_JOINED.length() + 1);
            SwingUtilities.invokeLater(() -> appendLine("--- " + info + " joined ---"));


        // member left notification with format: MEMBER_LEFT who
        } else if (line.startsWith(Protocol.MEMBER_LEFT + " ")) {
            String who = line.substring(Protocol.MEMBER_LEFT.length() + 1);
            SwingUtilities.invokeLater(() -> appendLine("--- " + who + " left ---"));


        // error message from server with format: ERROR message
        } else if (line.startsWith(Protocol.ERROR + " ")) {
            String err = line.substring(Protocol.ERROR.length() + 1);
            SwingUtilities.invokeLater(() -> appendLine("[ERROR] " + err));
        }
    }

    // display the member list in the message area based on the server's MEMBER_LIST response
    private void showMemberList(String data) {
        appendLine("--- Member List ---");
        // format: id|ip|port|isCoord,...  COORDINATOR:id etc (coordinator part is optional if no coordinator)
        String[] parts = data.split(" COORDINATOR:");
        String coordinator = parts.length == 2 ? parts[1] : "?";
        appendLine("Coordinator: " + coordinator);
        if (!parts[0].isEmpty()) {
            for (String entry : parts[0].split(",")) {
                if (!entry.isEmpty()) {
                    ClientInfo ci = ClientInfo.fromWireString(entry);
                    appendLine("  " + ci);
                }
            }
        }
        appendLine("-------------------");
    }

    // helper method to append a line of text to the message area and scroll to the bottom
    private void appendLine(String text) {
        messageArea.append(text + "\n");
        messageArea.setCaretPosition(messageArea.getDocument().getLength());
    }

    // helper method to set the status label text and color
    private void setStatus(String text, Color color) {
        statusLabel.setText("  " + text);
        statusLabel.setForeground(color);
    }

    // entry point with optional command-line arguments for server host and port
    public static void main(String[] args) throws Exception {
        String host = "localhost";
        int    port = Protocol.DEFAULT_PORT;

        if (args.length >= 1) host = args[0];
        if (args.length >= 2) {
            try { port = Integer.parseInt(args[1]); }
            catch (NumberFormatException e) { /* use default */ }
        }

        final String finalHost = host;
        final int    finalPort = port;

        // start the client GUI and network thread
        SwingUtilities.invokeLater(() -> {
            ChatClient client = new ChatClient(finalHost, finalPort);
            client.frame.setVisible(true);
            new Thread(() -> {
                try {
                    client.run();
                } catch (IOException e) {
                    SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(client.frame,
                            "Cannot connect to " + finalHost + ":" + finalPort
                            + "\n" + e.getMessage(),
                            "Connection Error", JOptionPane.ERROR_MESSAGE));
                }
            }, "client-reader").start();
        });
    }
}
