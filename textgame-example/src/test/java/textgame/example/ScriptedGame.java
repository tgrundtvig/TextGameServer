package textgame.example;

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
 * A game program, played by the test.
 *
 * <p>The real one is {@code GameServer.host}, which never stops on purpose — so when a test
 * needs to watch a student close their laptop mid-lesson, it uses this instead.
 */
final class ScriptedGame implements AutoCloseable {

    private final MessageChannel channel;
    private final BlockingQueue<Message> inbox = new LinkedBlockingQueue<>();

    ScriptedGame(int port, String name, int min, int max, String description)
            throws IOException {
        this.channel = MessageChannel.connect("localhost", port);
        Thread.ofVirtual().name("game-" + name).start(() -> {
            try {
                Message m;
                while ((m = channel.receive()) != null) {
                    inbox.add(m);
                }
            } catch (IOException e) {
                // The connection ended; the test will notice when it waits for something.
            }
        });
        send(Message.withText(MessageType.REGISTER, String.valueOf(min), String.valueOf(max),
                name));
        send(Message.withText(MessageType.DESCRIBE, description));
    }

    void send(Message message) {
        try {
            channel.send(message);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    Message await(MessageType type) {
        List<Message> skipped = new ArrayList<>();
        while (true) {
            Message m;
            try {
                m = inbox.poll(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
            if (m == null) {
                throw new AssertionError("waited for " + type + ", saw only " + skipped);
            }
            if (m.type() == type) {
                return m;
            }
            skipped.add(m);
        }
    }

    @Override
    public void close() {
        channel.close();
    }
}
