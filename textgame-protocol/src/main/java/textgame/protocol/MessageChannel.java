package textgame.protocol;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * A socket carrying one {@link Message} per line, in UTF-8.
 *
 * <p>Sending is synchronized, so several threads may send on the same channel.
 * Receiving is not: exactly one thread should own the read loop.
 */
public final class MessageChannel implements AutoCloseable {

    private final Socket socket;
    private final BufferedReader in;
    private final BufferedWriter out;

    public MessageChannel(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    /** Opens a channel to a server. */
    public static MessageChannel connect(String host, int port) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port));
        socket.setTcpNoDelay(true);
        try {
            return new MessageChannel(socket);
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    /** Writes and flushes one message. */
    public synchronized void send(Message message) throws IOException {
        out.write(message.encode());
        out.write('\n');
        out.flush();
    }

    /**
     * Reads the next message, blocking until one arrives.
     *
     * @return the message, or {@code null} once the peer has closed the connection
     * @throws ProtocolException if the line arrives but cannot be parsed
     */
    public Message receive() throws IOException {
        String line = in.readLine();
        if (line == null) {
            return null;
        }
        return Message.decode(line);
    }

    /** A short description of the peer, for logging. */
    public String peer() {
        return String.valueOf(socket.getRemoteSocketAddress());
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // Closing is best effort; the peer will see the stream end either way.
        }
    }
}
