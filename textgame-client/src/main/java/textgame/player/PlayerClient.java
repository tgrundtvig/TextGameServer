package textgame.player;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import textgame.protocol.Message;
import textgame.protocol.MessageChannel;
import textgame.protocol.MessageType;
import textgame.protocol.ProtocolException;

/**
 * The console client people play with. Shipped ready-made; students do not write one.
 *
 * <pre>{@code java -cp textgame-client.jar textgame.player.PlayerClient localhost 4000}</pre>
 *
 * <p>Everything that happens — a line arriving from the server, a line typed at the keyboard —
 * becomes an event on one queue, handled by one thread. That is why chat can arrive in the
 * middle of a menu without anything getting confused.
 */
public final class PlayerClient {

    /** A line the person at the keyboard typed. */
    private record Typed(String line) {
    }

    /** The keyboard is closed; nothing more is coming. */
    private static final Typed END_OF_INPUT = new Typed(null);

    private enum State { NAMING, PICKING_GAME, PICKING_TABLE, NAMING_TABLE, AT_TABLE, IN_MATCH }

    private record GameEntry(String id, String name, String description, int tables,
                             int min, int max) {
    }

    private record TableEntry(String name, int players, int max, String state) {
    }

    private final BlockingQueue<Object> events = new LinkedBlockingQueue<>();
    private final PrintStream out = System.out;
    private MessageChannel channel;

    private State state = State.NAMING;
    private String myName = "player";
    private final List<GameEntry> gameList = new ArrayList<>();
    private final List<TableEntry> tableList = new ArrayList<>();
    private int gamesExpected;
    private int tablesExpected;
    private String chosenGameId;
    private String chosenGameName = "";
    private boolean finished;

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = 4000;
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Usage: PlayerClient [host] [port]");
                return;
            }
        }
        new PlayerClient().run(host, port);
    }

    private void run(String host, int port) {
        try {
            channel = MessageChannel.connect(host, port);
        } catch (IOException e) {
            out.println("Could not reach the game server at " + host + ":" + port + ".");
            out.println("Check the address, and that the server is running.");
            return;
        }
        out.println("Connected to class server.");
        startReaders();
        prompt("Your name? ");

        try {
            while (!finished) {
                Object event = events.take();
                if (event instanceof Message message) {
                    fromServer(message);
                } else {
                    typed((Typed) event);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            channel.close();
        }
    }

    private void startReaders() {
        Thread.ofVirtual().name("server-reader").start(() -> {
            try {
                while (true) {
                    Message message = channel.receive();
                    if (message == null) {
                        break;
                    }
                    events.add(message);
                }
            } catch (IOException | ProtocolException e) {
                // Falls through to the goodbye below.
            }
            events.add(Message.withText(MessageType.BYE,
                    "The connection to the server ended."));
        });
        Thread.ofVirtual().name("keyboard").start(() -> {
            BufferedReader keyboard = new BufferedReader(
                    new InputStreamReader(System.in, StandardCharsets.UTF_8));
            try {
                String line;
                while ((line = keyboard.readLine()) != null) {
                    events.add(new Typed(line));
                }
            } catch (IOException e) {
                // Same ending as a closed keyboard.
            }
            events.add(END_OF_INPUT);
        });
    }

    // ---- what the server says -----------------------------------------------

    private void fromServer(Message message) {
        switch (message.type()) {
            case NAME_OK -> {
                myName = message.text();
                out.println();
                send(Message.of(MessageType.LIST_GAMES));
            }
            case GAME_LIST -> {
                gameList.clear();
                gamesExpected = Integer.parseInt(message.arg(0));
                if (gamesExpected == 0) {
                    showGames();
                }
            }
            case GAME_ENTRY -> gameList.add(new GameEntry(message.arg(0), message.text(), "",
                    Integer.parseInt(message.arg(1)), Integer.parseInt(message.arg(2)),
                    Integer.parseInt(message.arg(3))));
            case GAME_DESC -> {
                describeLast(message.arg(0), message.text());
                if (gameList.size() == gamesExpected) {
                    showGames();
                }
            }
            case TABLE_LIST -> {
                tableList.clear();
                tablesExpected = Integer.parseInt(message.arg(1));
                if (tablesExpected == 0) {
                    showTables();
                }
            }
            case TABLE_ENTRY -> {
                tableList.add(new TableEntry(message.arg(0), Integer.parseInt(message.arg(1)),
                        Integer.parseInt(message.arg(2)), message.arg(3)));
                if (tableList.size() == tablesExpected) {
                    showTables();
                }
            }
            case JOINED -> {
                state = State.AT_TABLE;
                int min = minPlayersOf(message.text());
                out.println();
                out.println("—— " + message.text() + " / " + message.arg(0)
                        + (min > 0 ? " (needs " + min + "-" + message.arg(2) + " players)" : "")
                        + " ——");
                out.println("Waiting for players. Type 'ready' when you want to start,"
                        + " or 'help' for what else you can type.");
                prompt("> ");
            }
            case LEFT -> {
                out.println(message.text());
                state = State.PICKING_GAME;
                send(Message.of(MessageType.LIST_GAMES));
            }
            case MATCH_START -> {
                state = State.IN_MATCH;
                out.println();
                out.println(message.text());
            }
            case MATCH_END -> {
                state = State.AT_TABLE;
                out.println();
                out.println(message.text());
            }
            case MSG, NOTICE, CHATLINE -> out.println(message.text());
            case PROMPT -> {
                out.println(message.text());
                prompt("> ");
            }
            case ERR -> {
                out.println(message.text());
                reprompt();
            }
            case BYE -> {
                out.println(message.text());
                finished = true;
            }
            default -> {
                // A message meant for a game program. Nothing for a player to see.
            }
        }
    }

    private void describeLast(String gameId, String description) {
        for (int i = 0; i < gameList.size(); i++) {
            GameEntry g = gameList.get(i);
            if (g.id().equals(gameId)) {
                gameList.set(i, new GameEntry(g.id(), g.name(), description, g.tables(),
                        g.min(), g.max()));
                return;
            }
        }
    }

    private int minPlayersOf(String gameName) {
        for (GameEntry g : gameList) {
            if (g.name().equals(gameName)) {
                return g.min();
            }
        }
        return 0;
    }

    // ---- the two menus -------------------------------------------------------

    private void showGames() {
        state = State.PICKING_GAME;
        out.println();
        if (gameList.isEmpty()) {
            out.println("No games are being hosted right now.");
            out.println("Somebody has to start their game program first.");
            prompt("Press enter to look again, or type a table name: ");
            return;
        }
        int width = 0;
        for (GameEntry g : gameList) {
            width = Math.max(width, g.name().length());
        }
        out.println("Games:");
        for (int i = 0; i < gameList.size(); i++) {
            GameEntry g = gameList.get(i);
            out.printf("  %d) %-" + width + "s   %s%n", i + 1, g.name(), tableCount(g.tables()));
            out.println("     " + g.description());
        }
        out.println();
        prompt("Which game? ");
    }

    private static String tableCount(int tables) {
        return switch (tables) {
            case 0 -> "no tables yet";
            case 1 -> "1 table";
            default -> tables + " tables";
        };
    }

    private void showTables() {
        state = State.PICKING_TABLE;
        out.println();
        out.println(chosenGameName + " tables:");
        int width = "create a new table".length();
        for (TableEntry t : tableList) {
            width = Math.max(width, t.name().length());
        }
        for (int i = 0; i < tableList.size(); i++) {
            TableEntry t = tableList.get(i);
            out.printf("  %d) %-" + width + "s   %s%n", i + 1, t.name(), describe(t));
        }
        out.printf("  %d) %s%n", tableList.size() + 1, "create a new table");
        out.println();
        prompt("Which? ");
    }

    private static String describe(TableEntry t) {
        return switch (t.state()) {
            case "PLAYING" -> "playing";
            case "FULL" -> t.players() + "/" + t.max() + " full";
            default -> t.players() + "/" + t.max() + " players";
        };
    }

    // ---- what the person types -----------------------------------------------

    private void typed(Typed event) {
        if (event == END_OF_INPUT) {
            send(Message.of(MessageType.QUIT));
            finished = true;
            return;
        }
        String line = event.line().strip();
        switch (state) {
            case NAMING -> {
                if (line.isEmpty()) {
                    prompt("Your name? ");
                    return;
                }
                send(Message.withText(MessageType.NAME, line));
            }
            case PICKING_GAME -> pickGame(line);
            case PICKING_TABLE -> pickTable(line);
            case NAMING_TABLE -> {
                String name = line.isEmpty() ? defaultTableName() : line;
                send(Message.of(MessageType.CREATE_TABLE, chosenGameId, name));
            }
            case AT_TABLE -> atTable(line);
            case IN_MATCH -> send(Message.withText(MessageType.ANSWER, event.line()));
        }
    }

    private void pickGame(String line) {
        if (line.isEmpty() || line.equalsIgnoreCase("refresh")) {
            send(Message.of(MessageType.LIST_GAMES));
            return;
        }
        if (line.equalsIgnoreCase("quit")) {
            send(Message.of(MessageType.QUIT));
            finished = true;
            return;
        }
        Integer pick = number(line);
        if (pick != null && pick >= 1 && pick <= gameList.size()) {
            GameEntry g = gameList.get(pick - 1);
            chosenGameId = g.id();
            chosenGameName = g.name();
            send(Message.of(MessageType.LIST_TABLES, g.id()));
            return;
        }
        // Table names are unique server-wide, so one can be typed at any prompt.
        joinByName(line);
    }

    private void pickTable(String line) {
        if (line.equalsIgnoreCase("back") || line.isEmpty()) {
            send(Message.of(MessageType.LIST_GAMES));
            return;
        }
        Integer pick = number(line);
        if (pick != null && pick == tableList.size() + 1) {
            state = State.NAMING_TABLE;
            prompt("Table name [" + defaultTableName() + "]? ");
            return;
        }
        if (pick != null && pick >= 1 && pick <= tableList.size()) {
            send(Message.of(MessageType.JOIN_TABLE, tableList.get(pick - 1).name()));
            return;
        }
        joinByName(line);
    }

    private void joinByName(String line) {
        if (line.matches("[A-Za-z0-9_-]{1,20}")) {
            send(Message.of(MessageType.JOIN_TABLE, line));
        } else {
            out.println("Type the number of what you want, or the name of a table.");
            reprompt();
        }
    }

    private void atTable(String line) {
        switch (line.toLowerCase(Locale.ROOT)) {
            case "ready" -> send(Message.of(MessageType.READY));
            case "unready" -> send(Message.of(MessageType.UNREADY));
            case "leave" -> send(Message.of(MessageType.LEAVE));
            case "who" -> send(Message.of(MessageType.WHO));
            case "help" -> {
                out.println("ready    — you are ready to start; the match begins when"
                        + " everybody is");
                out.println("unready  — changed your mind");
                out.println("who      — who is at this table");
                out.println("leave    — go back to the lobby");
                out.println("Anything else you type is chat.");
                prompt("> ");
            }
            default -> {
                if (!line.isEmpty()) {
                    send(Message.withText(MessageType.CHAT, line));
                }
            }
        }
    }

    private String defaultTableName() {
        String base = myName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        String name = (base.isEmpty() ? "table" : base) + "-table";
        return name.length() > 20 ? name.substring(0, 20) : name;
    }

    private static Integer number(String line) {
        try {
            return Integer.valueOf(line);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Asks again for whatever we were in the middle of, after an error interrupted it. */
    private void reprompt() {
        switch (state) {
            case NAMING -> prompt("Your name? ");
            case PICKING_GAME -> prompt("Which game? ");
            case PICKING_TABLE -> prompt("Which? ");
            case NAMING_TABLE -> prompt("Table name [" + defaultTableName() + "]? ");
            case AT_TABLE, IN_MATCH -> prompt("> ");
        }
    }

    private void prompt(String text) {
        out.print(text);
        out.flush();
    }

    private void send(Message message) {
        try {
            channel.send(message);
        } catch (IOException e) {
            out.println("The connection to the server ended.");
            finished = true;
        }
    }
}
