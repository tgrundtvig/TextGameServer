package textgame.server;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import textgame.protocol.Message;
import textgame.protocol.MessageChannel;
import textgame.protocol.MessageType;

/**
 * One connection, with its own thread for writing.
 *
 * <p>Sending never blocks the caller: messages go on a queue and a writer thread drains it.
 * That matters because the hub sends while holding its lock, and one player on a slow
 * connection must not be able to stop the whole server.
 *
 * <p>The queue is bounded. A peer that stops reading — a client hung, or a machine that has
 * quietly gone away — would otherwise grow it without limit; past the bound the connection is
 * dropped instead, and the reader thread cleans up as for any other disconnect.
 */
final class Endpoint {

    private static final Message STOP = Message.of(MessageType.QUIT);
    /** More than any honest client falls behind by; a chat room at a table is a few a second. */
    private static final int OUTBOX_LIMIT = 5_000;

    private final MessageChannel channel;
    private final BlockingQueue<Message> outbox = new LinkedBlockingQueue<>(OUTBOX_LIMIT);
    private final Thread writer;
    private volatile boolean closed;

    Endpoint(MessageChannel channel) {
        this.channel = channel;
        this.writer = Thread.ofVirtual().name("write-" + channel.peer()).start(this::drain);
    }

    private void drain() {
        try {
            while (true) {
                Message message = outbox.take();
                if (message == STOP) {
                    return;
                }
                channel.send(message);
            }
        } catch (IOException | InterruptedException e) {
            // The reader will notice the same broken connection and clean up.
        } finally {
            channel.close();
        }
    }

    void send(Message message) {
        if (closed) {
            return;
        }
        if (!outbox.offer(message)) {
            // The peer is not reading. Nothing queued can be delivered anyway, so drop
            // the connection now rather than the server's memory later.
            abort();
        }
    }

    void err(String text) {
        send(Message.withText(MessageType.ERR, text));
    }

    void notice(String text) {
        send(Message.withText(MessageType.NOTICE, text));
    }

    /** Says goodbye. The connection closes once the goodbye is actually on the wire. */
    void goodbye(String reason) {
        send(Message.withText(MessageType.BYE, reason));
        close();
    }

    Message receive() throws IOException {
        return channel.receive();
    }

    /**
     * Stops accepting new messages and closes once everything already queued has been sent.
     *
     * <p>Graceful on purpose: the last thing queued is usually a BYE explaining why the
     * connection is ending, and dropping the socket first would throw that away.
     */
    void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (!outbox.offer(STOP)) {
            abort();
        }
    }

    /** Drops the connection now: the server is shutting down, or the peer stopped reading. */
    void abort() {
        closed = true;
        outbox.offer(STOP);
        writer.interrupt();
        channel.close();
    }

    String peer() {
        return channel.peer();
    }
}
