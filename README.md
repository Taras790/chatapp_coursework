# Chat Application (COMP1549 — Task 1)

A multithreaded Java chat application using sockets and Swing.

## Features

- Group chat (broadcast messages)
- Private messages between members
- Automatic coordinator election
- Membership tracking (join/leave/list)
- Periodic heartbeat (`PING`/`PONG`) and stale-client cleanup
- Thread-safe server state and message logging

## Requirements

- Java 21 (JDK)
- Terminal (PowerShell, bash, or zsh)

Run all commands from the project root:

```text
chatapp_coursework/
```

## Quick Start

### 1) Compile

**Windows (PowerShell)**

```powershell
$sources = Get-ChildItem coursework\*.java | Where-Object { $_.Name -ne "ChatServerTest.java" } | ForEach-Object { $_.FullName }
javac -d out module-info.java $sources
```

**macOS / Linux (bash or zsh)**

```bash
sources=$(ls coursework/*.java | grep -v ChatServerTest.java)
javac -d out module-info.java $sources
```

### 2) Start the Server

```bash
java --module-path out -m coursework/coursework.ChatServer
```

Expected output:

```text
Chat server running on port 50000 ...
```

Keep this terminal running.

### 3) Start a Client

In a new terminal:

```bash
java --module-path out -m coursework/coursework.ChatClient localhost
```

Enter a unique ID when prompted.

### 4) Start More Clients

Repeat step 3 in additional terminals. Every client must use a unique ID.

## Client Connection Variants

Connect to a server on another machine:

```bash
java --module-path out -m coursework/coursework.ChatClient <server-ip>
```

Connect using a custom port:

```bash
java --module-path out -m coursework/coursework.ChatClient localhost <port>
```

## Usage

| Action | Command / UI |
|---|---|
| Send broadcast | Type message and press **Enter** or click **Send** |
| Send private message | `@targetId your message` |
| List members | Click **List Members** or type `/list` |
| Quit | Click **Quit** or close the window |

## Coordinator Behavior

- The first connected client becomes coordinator.
- Coordinator status appears in green with `[COORDINATOR]` in the client title bar.
- If the coordinator disconnects, the oldest remaining member is promoted automatically.
- All clients are notified when coordinator changes.

## Protocol Summary

### Server → Client

| Message | Meaning |
|---|---|
| `SUBMITNAME` | Request a unique ID |
| `NAMEACCEPTED <id>` | ID accepted |
| `COORDINATOR_YOU` | You are coordinator |
| `COORDINATOR_IS <id>` | Current coordinator |
| `COORDINATOR_CHANGED <id>` | Coordinator updated |
| `MESSAGE <timestamp> <id>: <text>` | Broadcast message |
| `PRIVATE <timestamp> <id>: <text>` | Private message |
| `MEMBER_LIST <data>` | Full member list |
| `MEMBER_JOINED <id> ip:port` | Member joined |
| `MEMBER_LEFT <id>` | Member left |
| `PING` | Heartbeat request |

### Client → Server

| Message | Meaning |
|---|---|
| `<id>` | Response to `SUBMITNAME` |
| `BROADCAST <text>` | Send to all members |
| `PRIVMSG <targetId> <text>` | Send private message |
| `LIST` | Request member list |
| `PONG` | Heartbeat response |
| `QUIT` | Graceful disconnect |

## Internal Design

| Pattern | Used In |
|---|---|
| Singleton | `ServerState`, `MessageLogger` |
| Observer | `GroupEventListener`, `ServerState`, `ChatServer` |
| Command | `ChatCommand`, command implementations, `CommandFactory` |

## Fault Tolerance

- Server sends `PING` every 20 seconds.
- Clients that miss `PONG` for 5 seconds are removed.
- Coordinator loss triggers automatic re-election.

## Tests

JUnit 5 jars are stored in `lib/`.

Compile tests:

```bash
javac --module-path "out;lib/junit-jupiter-api_5.14.1.jar;lib/junit-platform-commons_1.14.1.jar" --add-reads coursework=org.junit.jupiter.api --add-modules org.junit.jupiter.api -d out coursework/ChatServerTest.java
```

Run tests using your Java test runner or by executing tests from `ChatServerTest.java` in your editor.

## Project Structure

```text
chatapp_coursework/
├── coursework/
│   ├── Protocol.java
│   ├── ClientInfo.java
│   ├── MessageRecord.java
│   ├── MessageLogger.java
│   ├── ServerState.java
│   ├── GroupEventListener.java
│   ├── ChatCommand.java
│   ├── BroadcastCommand.java
│   ├── PrivateMessageCommand.java
│   ├── ListMembersCommand.java
│   ├── PongCommand.java
│   ├── QuitCommand.java
│   ├── CommandFactory.java
│   ├── PingScheduler.java
│   ├── ChatServer.java
│   ├── ChatClient.java
│   └── ChatServerTest.java
├── lib/
├── out/
└── README.md
```
