package textgame;

import textgame.internal.GameHosting;

/**
 * The connection from your program to the game server.
 *
 * <pre>{@code
 * public static void main(String[] args) {
 *     GameServer.connect("class.example.dk", 4000)
 *               .host(new NumberDuel());
 * }
 * }</pre>
 *
 * <p>{@link #host} keeps running for as long as you want your game to be playable. Your game
 * appears in the lobby when the program starts and disappears when you stop it.
 */
public final class GameServer {

    private final String host;
    private final int port;

    private GameServer(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Connects to a game server.
     *
     * <p>The class server is the usual choice: it runs all the time, and your game shows up
     * there for everybody. If your teacher has given you the server program to run on your
     * own machine, {@code "localhost"} points at that instead. Nothing else changes.
     */
    public static GameServer connect(String host, int port) {
        if (host == null || host.isBlank()) {
            throw new GameServerException("connect needs the server's address, for example"
                    + " GameServer.connect(\"localhost\", 4000).");
        }
        if (port < 1 || port > 65535) {
            throw new GameServerException("A port number must be between 1 and 65535, not "
                    + port + ".");
        }
        return new GameServer(host, port);
    }

    /**
     * Offers this game in the lobby and plays it until the program is stopped.
     *
     * <p>This call keeps going until you stop the program or the connection to the server is
     * lost. Every time a table of players is ready, the framework asks your {@link Game} for a
     * new {@link Match} and plays it.
     */
    public void host(Game game) {
        if (game == null) {
            throw new GameServerException("host needs a game, for example:"
                    + " host(new NumberDuel()).");
        }
        GameHosting.run(host, port, game);
    }
}
