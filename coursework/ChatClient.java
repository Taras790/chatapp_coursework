package coursework;

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
 * Swing-based chat client supporting:
 * - Unique client ID entry at startup
 * - Optional server IP and port as command-line arguments
 * - Coordinator status display
 * - Broadcast messaging (plain text or BROADCAST prefix)
 * - Private messaging (@targetId message)
 * - Member list request (/list)
 * - Periodic PING/PONG handling
 * - Quit button for graceful disconnect
 */
public class ChatClient {

    private final String serverAddress;
    private final int    serverPort;

    private String      clientId;
    private PrintWriter out;
    private Scanner     in;

    // --- GUI ---
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

    // -------------------------------------------------------------------------
    // GUI construction
    // -------------------------------------------------------------------------

    private void buildGui() {
        messageArea.setEditable(false);
        messageArea.setLineWrap(true);
        messageArea.setWrapStyleWord(true);
        inputField.setEditable(false);

        // Status bar (top)
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        statusLabel.setForeground(Color.DARK_GRAY);

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttonPanel.add(listButton);
        buttonPanel.add(quitButton);

        // Bottom panel: input + buttons
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

        // Send on Enter or Send button
        inputField.addActionListener(e -> sendMessage());
        sendButton.addActionListener(e -> sendMessage());

        // List members button
        listButton.addActionListener(e -> {
            if (out != null) out.println(Protocol.LIST);
        });

        // Quit button — graceful disconnect
        quitButton.addActionListener(e -> quit());

        // Window close = same as Quit
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                quit();
            }
        });
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || out == null) return;

        if (text.startsWith("@")) {
            // Private message: @targetId some message text
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

    // -------------------------------------------------------------------------
    // ID / connection dialog
    // -------------------------------------------------------------------------

    private String askId() {
        String id = null;
        while (id == null || id.trim().isEmpty()) {
            id = JOptionPane.showInputDialog(
                frame,
                "Enter your unique ID:",
                "Choose an ID",
                JOptionPane.PLAIN_MESSAGE
            );
            if (id == null) System.exit(0); // user cancelled
        }
        return id.trim();
    }

    // -------------------------------------------------------------------------
    // Network read loop
    // -------------------------------------------------------------------------

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

    private void handleServerMessage(String line) {
        if (line.equals(Protocol.SUBMITNAME)) {
            out.println(askId());

        } else if (line.equals(Protocol.NAME_TAKEN)) {
            SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(frame,
                    "That ID is already taken. Please choose another.",
                    "ID Taken", JOptionPane.WARNING_MESSAGE));

        } else if (line.startsWith(Protocol.NAMEACCEPTED + " ")) {
            clientId = line.substring(Protocol.NAMEACCEPTED.length() + 1).trim();
            SwingUtilities.invokeLater(() -> {
                inputField.setEditable(true);
                frame.setTitle("Chat - " + clientId);
                appendLine("--- Connected as " + clientId + " ---");
                appendLine("Tip: use @targetId msg for private messages, /list to see members");
            });

        } else if (line.equals(Protocol.COORDINATOR_YOU)) {
            SwingUtilities.invokeLater(() -> {
                setStatus(clientId + "  [COORDINATOR]", Color.decode("#006400"));
                appendLine("*** You are the group coordinator ***");
            });

        } else if (line.startsWith(Protocol.COORDINATOR_IS + " ")) {
            String coord = line.substring(Protocol.COORDINATOR_IS.length() + 1).trim();
            SwingUtilities.invokeLater(() -> {
                setStatus(clientId, Color.DARK_GRAY);
                appendLine("*** Coordinator: " + coord + " ***");
            });

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

        } else if (line.equals(Protocol.PING)) {
            out.println(Protocol.PONG); // reply immediately

        } else if (line.startsWith(Protocol.MEMBER_LIST + " ")) {
            String data = line.substring(Protocol.MEMBER_LIST.length() + 1);
            SwingUtilities.invokeLater(() -> showMemberList(data));

        } else if (line.startsWith(Protocol.MESSAGE + " ")) {
            String text = line.substring(Protocol.MESSAGE.length() + 1);
            SwingUtilities.invokeLater(() -> appendLine(text));

        } else if (line.startsWith(Protocol.PRIVATE + " ")) {
            String text = line.substring(Protocol.PRIVATE.length() + 1);
            SwingUtilities.invokeLater(() -> appendLine("[PRIVATE] " + text));

        } else if (line.startsWith(Protocol.PRIVATE_SENT + " ")) {
            String text = line.substring(Protocol.PRIVATE_SENT.length() + 1);
            SwingUtilities.invokeLater(() -> appendLine("[PRIVATE SENT] " + text));

        } else if (line.startsWith(Protocol.MEMBER_JOINED + " ")) {
            String info = line.substring(Protocol.MEMBER_JOINED.length() + 1);
            SwingUtilities.invokeLater(() -> appendLine("--- " + info + " joined ---"));

        } else if (line.startsWith(Protocol.MEMBER_LEFT + " ")) {
            String who = line.substring(Protocol.MEMBER_LEFT.length() + 1);
            SwingUtilities.invokeLater(() -> appendLine("--- " + who + " left ---"));

        } else if (line.startsWith(Protocol.ERROR + " ")) {
            String err = line.substring(Protocol.ERROR.length() + 1);
            SwingUtilities.invokeLater(() -> appendLine("[ERROR] " + err));
        }
    }

    private void showMemberList(String data) {
        appendLine("--- Member List ---");
        // Format: id|ip|port|isCoord,...  COORDINATOR:id
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

    private void appendLine(String text) {
        messageArea.append(text + "\n");
        messageArea.setCaretPosition(messageArea.getDocument().getLength());
    }

    private void setStatus(String text, Color color) {
        statusLabel.setText("  " + text);
        statusLabel.setForeground(color);
    }

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

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
