package textgame.example;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import textgame.protocol.Message;
import textgame.protocol.MessageChannel;
import textgame.protocol.MessageType;

/**
 * A player, played by the test: a real socket speaking the real protocol, with a keyboard
 * made of method calls.
 */
final class ScriptedPlayer implements AutoCloseable {

    private static final long TIMEOUT_SECONDS = 5;

    private final String name;
    private final MessageChannel channel;
    private final BlockingQueue<Message> inbox = new LinkedBlockingQueue<>();
    /** Everything that has arrived, recorded as it arrives rather than as it is awaited. */
    private final List<Message> received = java.util.Collections.synchronizedList(
            new ArrayList<>());

    ScriptedPlayer(int port, String name) throws IOException {
        this(port, name, null);
    }

    /** {@code password} null means send none; {@code name} null means do not introduce yourself. */
    ScriptedPlayer(int port, String name, String password) throws IOException {
        this.name = name == null ? "(anonymous)" : name;
        this.channel = MessageChannel.connect("localhost", port);
        Thread.ofVirtual().name("player-" + name).start(() -> {
            try {
                Message m;
                while ((m = channel.receive()) != null) {
                    received.add(m);
                    inbox.add(m);
                }
            } catch (IOException e) {
                // The connection ended; awaiting anything more will time out and say so.
            }
        });
        if (password != null) {
            send(Message.withText(MessageType.PASSWORD, password));
        }
        if (name != null) {
            send(Message.withText(MessageType.NAME, name));
        }
    }

    String playerName() {
        return name;
    }

    void send(Message message) {
        try {
            channel.send(message);
        } catch (IOException e) {
            throw new AssertionError(name + " could not send " + message, e);
        }
    }

    void say(MessageType type) {
        send(Message.of(type));
    }

    void answer(String text) {
        send(Message.withText(MessageType.ANSWER, text));
    }

    /** The next message of one of these types, skipping (but remembering) anything else. */
    Message await(MessageType... types) {
        List<MessageType> wanted = List.of(types);
        long deadline = System.nanoTime() + TIMEOUT_SECONDS * 1_000_000_000L;
        while (System.nanoTime() < deadline) {
            Message m;
            try {
                m = inbox.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
            if (m == null) {
                break;
            }
            if (wanted.contains(m.type())) {
                return m;
            }
        }
        return fail(name + " waited for " + wanted + " but only got: " + received);
    }

    /** Everything this player has been shown so far, as one blob to assert against. */
    String transcript() {
        StringBuilder sb = new StringBuilder();
        synchronized (received) {
            for (Message m : received) {
                sb.append(m.type()).append(' ').append(m.text() == null ? "" : m.text())
                        .append('\n');
            }
        }
        return sb.toString();
    }

    void drain() {
        inbox.drainTo(new ArrayList<>());
    }

    @Override
    public void close() {
        channel.close();
    }
}
