package textgame.protocol;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketOption;
import java.nio.charset.StandardCharsets;
import jdk.net.ExtendedSocketOptions;

/**
 * A socket carrying one {@link Message} per line, in UTF-8.
 *
 * <p>Sending is synchronized, so several threads may send on the same channel.
 * Receiving is not: exactly one thread should own the read loop.
 *
 * <p>Every channel has TCP keepalive switched on, with short timers where the platform
 * allows. A laptop that reboots, sleeps or drops off the wifi never closes its connection,
 * so without keepalive the other end's read would block forever — a game would stay in the
 * lobby and a player's name would stay taken until the server was restarted. With it, the
 * kernel probes a silent connection and reports it dead; a machine that has come back
 * answers the first probe with a reset, so that case is noticed at once.
 */
public final class MessageChannel implements AutoCloseable {

    /** Seconds of silence before the first keepalive probe. */
    static final int KEEPALIVE_IDLE_SECONDS = 30;
    /** Seconds between probes once they have started. */
    static final int KEEPALIVE_INTERVAL_SECONDS = 10;
    /** Unanswered probes before the connection is declared dead. */
    static final int KEEPALIVE_PROBES = 3;
    /** Longer than any real message; a line this long is a mistake or an attack. */
    static final int MAX_LINE_CHARS = 64 * 1024;
    /** How long to wait for a server that does not answer, before saying so. */
    static final int CONNECT_TIMEOUT_MILLIS = 10_000;

    private final Socket socket;
    private final BufferedReader in;
    private final BufferedWriter out;

    public MessageChannel(Socket socket) throws IOException {
        this.socket = socket;
        keepAlive(socket);
        this.in = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    /** Opens a channel to a server. */
    public static MessageChannel connect(String host, int port) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
        } catch (IOException e) {
            socket.close();
            throw e;
        }
        socket.setTcpNoDelay(true);
        try {
            return new MessageChannel(socket);
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    /**
     * Turns on keepalive with timers short enough to matter, so that a peer that vanished
     * without saying goodbye is noticed in about a minute rather than never.
     *
     * <p>The timers are extensions (Linux, macOS, and partly Windows); where one is missing,
     * plain keepalive stays on with the operating system's own timing, which is usually two
     * hours — still better than forever. The probes have a second use on the client side:
     * they keep a home router's NAT entry alive, so an idle game program does not get quietly
     * cut off from behind.
     *
     * <p>Keepalive only watches an <em>idle</em> connection. If there is unacknowledged data in
     * flight, TCP's own retransmission timer decides instead, which on Linux gives up after
     * roughly fifteen minutes. Long, but finite.
     */
    static void keepAlive(Socket socket) throws IOException {
        socket.setKeepAlive(true);
        trySet(socket, ExtendedSocketOptions.TCP_KEEPIDLE, KEEPALIVE_IDLE_SECONDS);
        trySet(socket, ExtendedSocketOptions.TCP_KEEPINTERVAL, KEEPALIVE_INTERVAL_SECONDS);
        trySet(socket, ExtendedSocketOptions.TCP_KEEPCOUNT, KEEPALIVE_PROBES);
    }

    private static void trySet(Socket socket, SocketOption<Integer> option, int value) {
        try {
            socket.setOption(option, value);
        } catch (UnsupportedOperationException | IOException | LinkageError e) {
            // Not on this platform. Plain keepalive with the OS's own timers is what we get.
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
        String line = readLine();
        if (line == null) {
            return null;
        }
        return Message.decode(line);
    }

    /**
     * One line, or {@code null} at end of stream — like {@code BufferedReader.readLine}, but
     * with a ceiling. {@code readLine} would buffer a line of any length, so a peer that never
     * sends a newline could grow the process without limit.
     */
    private String readLine() throws IOException {
        StringBuilder line = new StringBuilder(80);
        while (true) {
            int c = in.read();
            if (c == -1) {
                return line.isEmpty() ? null : line.toString();
            }
            if (c == '\n') {
                return line.toString();
            }
            if (line.length() >= MAX_LINE_CHARS) {
                throw new IOException("a line longer than " + MAX_LINE_CHARS + " characters");
            }
            line.append((char) c);
        }
    }

    /** The socket underneath, so a test can look at how it is configured. */
    Socket socket() {
        return socket;
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
