package textgame.internal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import textgame.Game;
import textgame.GameServerException;
import textgame.Match;
import textgame.PlayerGoneException;
import textgame.protocol.Message;
import textgame.protocol.MessageType;
import textgame.protocol.ProtocolException;

/**
 * Everything the framework does while a student's game is being hosted.
 *
 * <p>One thread reads messages from the server and hands each one to the table it belongs to.
 * Each table's {@code play} runs on its own virtual thread, so one program serves many tables
 * at once and a crash at one table cannot touch the others.
 */
public final class HostRuntime {

    private final Link link;
    private final Game game;
    /** Read once: a name() that throws must not be able to break the crash report. */
    private final String gameName;
    private final Map<String, MatchTable> tables = new ConcurrentHashMap<>();
    private final Object sendLock = new Object();

    private volatile boolean stopped;

    public HostRuntime(Link link, Game game) {
        this.link = link;
        this.game = game;
        this.gameName = String.valueOf(game.name());
    }

    void send(Message message) {
        if (stopped) {
            return;
        }
        synchronized (sendLock) {
            try {
                link.send(message);
            } catch (GameServerException e) {
                stopped = true;
                // Closing makes the read loop notice at once, instead of whenever the socket
                // gets round to failing on that side too.
                link.close();
            }
        }
    }

    /** Registers the game and then reads messages until the server or the student stops it. */
    public void run() {
        checkGame(game);
        // Before anything else, if there is a class password to send. A server without one
        // ignores it, so this is safe whether or not the server asks.
        String password = PasswordFile.read();
        if (password != null) {
            send(Message.withText(MessageType.PASSWORD, password));
        }
        send(Message.withText(MessageType.REGISTER, String.valueOf(game.minPlayers()),
                String.valueOf(game.maxPlayers()), game.name()));
        send(Message.withText(MessageType.DESCRIBE, game.description()));

        try {
            while (!stopped) {
                Message message;
                try {
                    message = link.receive();
                } catch (ProtocolException e) {
                    System.err.println("[textgame] ignoring a message the framework did not"
                            + " understand: " + e.getMessage());
                    continue;
                }
                if (message == null) {
                    break;
                }
                handle(message);
            }
        } finally {
            stopped = true;
            for (MatchTable table : tables.values()) {
                table.end("the connection to the game server was lost");
            }
            link.close();
        }
        System.out.println("[textgame] Disconnected from the game server. "
                + game.name() + " is no longer in the lobby.");
    }

    /**
     * The mistakes a game can make before it is even connected, said plainly. Called before
     * the socket is opened, so a program that fails here never appears in the lobby.
     */
    public static void checkGame(Game game) {
        if (game.name() == null || game.name().isBlank()) {
            throw new GameServerException("name() must return the name of your game,"
                    + " for example \"Number Duel\".");
        }
        if (game.description() == null) {
            throw new GameServerException("description() must return one line describing"
                    + " your game. It is shown under the name in the lobby.");
        }
        if (game.minPlayers() < 1 || game.maxPlayers() < game.minPlayers()) {
            throw new GameServerException("minPlayers() is " + game.minPlayers()
                    + " and maxPlayers() is " + game.maxPlayers()
                    + ". minPlayers() must be at least 1, and maxPlayers() at least as big.");
        }
    }

    private void handle(Message message) {
        switch (message.type()) {
            case REGISTERED -> System.out.println("[textgame] " + game.name()
                    + " is in the lobby. Players can find it there now."
                    + " Stop this program to take it down.");
            case TABLE_START -> tables.put(message.arg(0),
                    new MatchTable(this, message.arg(0), Integer.parseInt(message.arg(1))));
            case TABLE_SEAT -> {
                MatchTable table = tables.get(message.arg(0));
                if (table != null) {
                    table.seat(message.arg(1), message.text());
                }
            }
            case TABLE_GO -> startMatch(message.arg(0));
            case INPUT -> {
                MatchTable table = tables.get(message.arg(0));
                PlayerImpl player = table == null ? null : table.find(message.arg(1));
                if (player != null) {
                    player.deliver(message.text());
                }
            }
            case PLAYER_GONE -> {
                MatchTable table = tables.get(message.arg(0));
                if (table != null) {
                    PlayerImpl player = table.find(message.arg(1));
                    table.end((player == null ? "a player" : player.name()) + " disconnected");
                }
            }
            case ERR -> System.err.println("[textgame] the server says: " + message.text());
            case BYE -> {
                System.err.println("[textgame] the server closed the connection: "
                        + message.text());
                if (PasswordFile.read() == null) {
                    System.err.println("[textgame] " + PasswordFile.howToFixIt());
                }
                stopped = true;
            }
            default -> System.err.println("[textgame] ignoring unexpected message: " + message);
        }
    }

    private void startMatch(String tableId) {
        MatchTable table = tables.get(tableId);
        if (table == null) {
            return;
        }
        if (!table.seatsFilled()) {
            tables.remove(tableId, table);
            send(Message.withText(MessageType.ENDMATCH, tableId,
                    "The game program did not get everybody's seat. Try again."));
            return;
        }
        Thread.ofVirtual().name("match-" + tableId).start(() -> runMatch(table));
    }

    private void runMatch(MatchTable table) {
        String ending = "";
        MatchTable.CURRENT.set(table);
        try {
            Match match = game.newMatch();
            if (match == null) {
                throw new IllegalStateException("newMatch() returned null. It must return a new"
                        + " match object, for example: return new " + game.name().replace(" ", "")
                        + "Match();");
            }
            match.play(table.room());
        } catch (PlayerGoneException e) {
            ending = "Game ended: " + e.getMessage() + ".";
        } catch (Throwable e) {
            ending = "The game stopped because " + gameName + " has a bug."
                    + " Whoever is running it can see what went wrong.";
            reportCrash(table, e);
        } finally {
            table.end(ending.isEmpty() ? "the match ended" : ending);
            // Only our own entry: by now the server may have started the next match at this
            // table, and that one must be left alone.
            tables.remove(table.id(), table);
            send(Message.withText(MessageType.ENDMATCH, table.id(), ending));
            MatchTable.CURRENT.remove();
        }
    }

    /**
     * A student's exception belongs on the student's own console, with the stack trace they
     * need — and nowhere near the players, who cannot do anything about it.
     */
    private void reportCrash(MatchTable table, Throwable problem) {
        StringBuilder who = new StringBuilder();
        for (PlayerImpl p : table.seats()) {
            who.append(who.isEmpty() ? "" : ", ").append(p.name());
        }
        synchronized (System.err) {
            System.err.println();
            System.err.println("=== " + gameName + " crashed while playing with " + who
                    + " ===");
            System.err.println("The match was ended and those players were sent back to their"
                    + " table. Your other tables are still running.");
            problem.printStackTrace(System.err);
            System.err.println("=== end of crash report ===");
            System.err.println();
        }
    }
}
