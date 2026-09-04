package textgame.server;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import textgame.protocol.Message;
import textgame.protocol.MessageChannel;
import textgame.protocol.MessageType;
import textgame.protocol.ProtocolException;

/**
 * The class server: one instance, one port, running for the whole lesson.
 *
 * <pre>{@code java -jar textgame-server.jar 4000}</pre>
 *
 * <p>Every connection — player clients and students' game programs alike — dials in, and says
 * which it is with its first message. Each gets a virtual thread, so a full classroom costs
 * nothing much.
 */
public final class TextGameServer implements AutoCloseable {

    private static final int DEFAULT_PORT = 4000;

    private final ServerSocket listener;
    private final Hub hub;
    private final long idleSeconds;
    private final ExecutorService connections = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Endpoint> live = ConcurrentHashMap.newKeySet();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private volatile boolean running = true;

    private TextGameServer(ServerSocket listener, long idleSeconds, int maxTablesPerGame) {
        this.listener = listener;
        this.hub = new Hub(maxTablesPerGame);
        this.idleSeconds = idleSeconds;
    }

    public static void main(String[] args) throws Exception {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Usage: TextGameServer [port]   (default " + DEFAULT_PORT
                        + ")");
                System.exit(2);
            }
        }
        try (TextGameServer server = start(port)) {
            System.out.println("[server] listening on port " + server.port());
            System.out.println("[server] students point their game at this machine and this"
                    + " port; players run the console client against it.");
            server.awaitShutdown();
        }
    }

    /** Starts listening and returns straight away. Pass 0 to be given a free port. */
    public static TextGameServer start(int port) throws IOException {
        long idleSeconds = Long.getLong("textgame.idleSeconds", 120);
        int maxTables = Integer.getInteger("textgame.maxTablesPerGame", 20);
        ServerSocket listener = new ServerSocket(port);
        TextGameServer server = new TextGameServer(listener, idleSeconds, maxTables);
        Thread.ofVirtual().name("accept").start(server::acceptLoop);
        Thread.ofVirtual().name("idle-watch").start(server::idleLoop);
        return server;
    }

    /** The port actually in use, which matters when you asked for 0. */
    public int port() {
        return listener.getLocalPort();
    }

    public void awaitShutdown() throws InterruptedException {
        stopped.await();
    }

    @Override
    public void close() {
        running = false;
        try {
            listener.close();
        } catch (IOException ignored) {
            // Closing the listener is how the accept loop is told to stop.
        }
        // Closing the sockets is what unblocks the connection threads, which are all sitting
        // in a blocking read; interrupting a thread does not.
        for (Endpoint endpoint : live) {
            endpoint.abort();
        }
        connections.shutdownNow();
        stopped.countDown();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = listener.accept();
                socket.setTcpNoDelay(true);
                connections.execute(() -> serve(socket));
            } catch (IOException e) {
                if (running) {
                    System.err.println("[server] could not accept a connection: "
                            + e.getMessage());
                }
                break;
            }
        }
        stopped.countDown();
    }

    /**
     * A game blocked forever on somebody who has walked away holds a whole table hostage, so
     * an unanswered question eventually counts as leaving.
     */
    private void idleLoop() {
        long scanSeconds = Math.max(1, Math.min(5, idleSeconds));
        while (running) {
            try {
                TimeUnit.SECONDS.sleep(scanSeconds);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            hub.endIdleMatches(idleSeconds);
        }
    }

    /**
     * One connection, from hello to goodbye.
     *
     * <p>Nobody says up front whether they are a player or a game program: the first message
     * settles it. {@code REGISTER} means a student's game, {@code NAME} means somebody who
     * wants to play.
     */
    private void serve(Socket socket) {
        Endpoint out;
        try {
            out = new Endpoint(new MessageChannel(socket));
        } catch (IOException e) {
            return;
        }
        live.add(out);
        HostedGame game = null;
        PlayerSession player = null;
        try {
            while (true) {
                Message message;
                try {
                    message = out.receive();
                } catch (ProtocolException e) {
                    // One unreadable line is not worth dropping the connection over.
                    out.err("The server could not read that: " + e.getMessage());
                    continue;
                }
                if (message == null || message.type() == MessageType.QUIT) {
                    break;
                }
                if (game != null) {
                    hub.fromGame(game, message);
                } else if (player != null) {
                    hub.fromPlayer(player, message);
                } else if (message.type() == MessageType.REGISTER) {
                    game = registerGame(out, message);
                    if (game == null) {
                        break;
                    }
                } else if (message.type() == MessageType.NAME) {
                    // A rejected name is not fatal: the client asks for another one.
                    player = hub.join(out, message.text());
                } else {
                    out.err("Say who you are first: a player client sends NAME,"
                            + " a game program sends REGISTER.");
                }
            }
        } catch (IOException | UncheckedIOException e) {
            // The far end went away. The cleanup below is the same either way.
        } finally {
            if (game != null) {
                hub.unregister(game);
            }
            if (player != null) {
                hub.disconnect(player);
            }
            live.remove(out);
            out.close();
        }
    }

    private HostedGame registerGame(Endpoint out, Message message) {
        int min;
        int max;
        try {
            min = Integer.parseInt(message.arg(0));
            max = Integer.parseInt(message.arg(1));
        } catch (NumberFormatException e) {
            out.goodbye("minPlayers() and maxPlayers() must be whole numbers.");
            return null;
        }
        HostedGame registered = hub.register(out, message.text(), min, max);
        if (registered == null) {
            out.goodbye("The game could not be registered — see the message above.");
        }
        return registered;
    }
}
