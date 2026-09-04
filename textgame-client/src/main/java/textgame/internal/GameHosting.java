package textgame.internal;

import textgame.Game;

/** The bridge from the two-line {@code GameServer} facade to the runtime that does the work. */
public final class GameHosting {

    private GameHosting() {
    }

    /** Connects, registers the game and serves tables until the connection ends. */
    public static void run(String host, int port, Game game) {
        ConsoleGuard.install();
        Link link = SocketLink.connect(host, port);
        new HostRuntime(link, game).run();
    }
}
