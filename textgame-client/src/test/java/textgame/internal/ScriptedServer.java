package textgame.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import textgame.Game;
import textgame.protocol.Message;
import textgame.protocol.MessageType;

/**
 * A server made of two queues, so a whole match can be played by a test in one JVM.
 *
 * <p>The test plays the part of the server: it reads what the game program sends and answers
 * the way a real player would. Nothing here knows about sockets.
 */
final class ScriptedServer implements Link, AutoCloseable {

    private static final Message EOF = Message.of(MessageType.QUIT);
    private static final long TIMEOUT_SECONDS = 5;

    private final BlockingQueue<Message> toGame = new LinkedBlockingQueue<>();
    private final BlockingQueue<Message> fromGame = new LinkedBlockingQueue<>();
    private Thread runtimeThread;

    // ---- the Link the runtime talks through ---------------------------------

    @Override
    public void send(Message message) {
        fromGame.add(message);
    }

    @Override
    public Message receive() {
        try {
            Message m = toGame.poll(60, TimeUnit.SECONDS);
            return m == null || m == EOF ? null : m;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public void close() {
        toGame.add(EOF);
    }

    // ---- what the test drives ------------------------------------------------

    ScriptedServer start(Game game) {
        HostRuntime runtime = new HostRuntime(this, game);
        runtimeThread = new Thread(runtime::run, "scripted-host");
        runtimeThread.setDaemon(true);
        runtimeThread.start();
        expect(MessageType.REGISTER);
        expect(MessageType.DESCRIBE);
        toGame.add(Message.of(MessageType.REGISTERED, "g1"));
        return this;
    }

    /** Seats players at a table and tells the game program to start playing. */
    void startTable(String tableId, String... playerNames) {
        toGame.add(Message.of(MessageType.TABLE_START, tableId,
                String.valueOf(playerNames.length)));
        for (int i = 0; i < playerNames.length; i++) {
            toGame.add(Message.withText(MessageType.TABLE_SEAT, tableId, "p" + (i + 1),
                    playerNames[i]));
        }
        toGame.add(Message.of(MessageType.TABLE_GO, tableId));
    }

    void playerLeaves(String tableId, String playerId) {
        toGame.add(Message.of(MessageType.PLAYER_GONE, tableId, playerId));
    }

    void answer(String tableId, String playerId, String text) {
        toGame.add(Message.withText(MessageType.INPUT, tableId, playerId, text));
    }

    /** The next message from the game program, whatever it is. */
    Message next() {
        try {
            Message m = fromGame.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (m == null) {
                fail("the game program sent nothing within " + TIMEOUT_SECONDS + " seconds");
            }
            return m;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    Message expect(MessageType type) {
        Message m = next();
        assertEquals(type, m.type(), "expected " + type + " but got: " + m);
        return m;
    }

    /** Answers the next prompt for {@code playerId}, returning what the player was shown. */
    String answerPrompt(String tableId, String playerId, String answer) {
        Message prompt = expect(MessageType.PROMPT_ONE);
        assertEquals(tableId, prompt.arg(0));
        assertEquals(playerId, prompt.arg(1));
        answer(tableId, playerId, answer);
        return prompt.text();
    }

    /** Everything sent up to and including the next ENDMATCH. */
    List<Message> drainUntilEndMatch() {
        List<Message> seen = new ArrayList<>();
        while (true) {
            Message m = next();
            seen.add(m);
            if (m.type() == MessageType.ENDMATCH) {
                return seen;
            }
        }
    }

    void shutdown() {
        toGame.add(EOF);
        if (runtimeThread != null) {
            try {
                runtimeThread.join(TIMEOUT_SECONDS * 1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
