package textgame.internal;

import textgame.protocol.Message;

/**
 * The connection to the server, as the game runtime sees it.
 *
 * <p>An interface rather than a socket so that a whole match can be played by a scripted test
 * in one JVM, with no server anywhere.
 */
public interface Link extends AutoCloseable {

    /** Sends one message. Safe to call from several threads. */
    void send(Message message);

    /** Blocks for the next message, or returns {@code null} once the far end is finished. */
    Message receive();

    @Override
    void close();
}
