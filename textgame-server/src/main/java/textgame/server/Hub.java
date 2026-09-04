package textgame.server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import textgame.protocol.Message;
import textgame.protocol.MessageType;
import textgame.protocol.Names;

/**
 * The whole server, minus the sockets: games, tables, players, and the routing between them.
 *
 * <p>There are no game rules here and there never will be. The hub knows that a table has
 * players and that a game program wants to send one of them a line of text; it has no idea
 * what an {@code askInt} is, because all of that happens in the student's own JVM.
 *
 * <p>Everything runs under one lock. Sending is a queue push, so holding the lock while
 * telling twenty people something cannot block on anybody's network.
 */
final class Hub {

    private final Map<String, HostedGame> games = new LinkedHashMap<>();
    private final Map<String, Table> tablesByKey = new LinkedHashMap<>();
    private final Map<String, Table> tablesById = new LinkedHashMap<>();
    private final Map<String, PlayerSession> playersByKey = new LinkedHashMap<>();

    private final int maxTablesPerGame;
    private long nextId = 1;

    Hub(int maxTablesPerGame) {
        this.maxTablesPerGame = maxTablesPerGame;
    }

    private String newId(String prefix) {
        return prefix + (nextId++);
    }

    // ---- game programs -------------------------------------------------------

    synchronized HostedGame register(Endpoint out, String name, int minPlayers, int maxPlayers) {
        if (name.isBlank()) {
            out.err("Your game needs a name. Return one from name().");
            return null;
        }
        if (minPlayers < 1 || maxPlayers < minPlayers) {
            out.err("minPlayers() must be at least 1 and maxPlayers() at least as big;"
                    + " this game said " + minPlayers + " and " + maxPlayers + ".");
            return null;
        }
        HostedGame game = new HostedGame(newId("g"), out, name.strip(), minPlayers, maxPlayers);
        games.put(game.id(), game);
        out.send(Message.of(MessageType.REGISTERED, game.id()));
        System.out.println("[server] " + game.name() + " is now hosted from " + out.peer());
        return game;
    }

    synchronized void describe(HostedGame game, String description) {
        game.describe(description.strip());
    }

    /** The game program stopped: its tables go with it, and its players go back to the lobby. */
    synchronized void unregister(HostedGame game) {
        if (games.remove(game.id()) == null) {
            return;
        }
        System.out.println("[server] " + game.name() + " is no longer hosted");
        for (Table table : List.copyOf(game.tables())) {
            endMatch(table, game.name() + " was taken down while you were playing.");
            for (PlayerSession p : List.copyOf(table.seated())) {
                p.stand();
                p.out().send(Message.withText(MessageType.LEFT,
                        game.name() + " is no longer running, so its tables are gone."));
            }
            forget(table);
        }
    }

    /** One message from a game program, on its way to the players at one of its tables. */
    synchronized void fromGame(HostedGame game, Message message) {
        switch (message.type()) {
            case DESCRIBE -> describe(game, message.text());
            case MSG_ALL -> {
                Table table = liveTable(game, message.arg(0));
                if (table != null) {
                    tellAll(table, Message.withText(MessageType.MSG, message.text()));
                }
            }
            case MSG_ONE -> {
                PlayerSession p = seatedPlayer(game, message.arg(0), message.arg(1));
                if (p != null) {
                    p.out().send(Message.withText(MessageType.MSG, message.text()));
                }
            }
            case PROMPT_ONE -> {
                PlayerSession p = seatedPlayer(game, message.arg(0), message.arg(1));
                if (p != null) {
                    p.prompted();
                    p.out().send(Message.withText(MessageType.PROMPT, message.text()));
                }
            }
            case ENDMATCH -> {
                Table table = tablesById.get(message.arg(0));
                if (table != null && table.game() == game) {
                    endMatch(table, message.text());
                }
            }
            case REGISTER -> game.out().err("This connection is already hosting "
                    + game.name() + ". One game per program.");
            default -> game.out().err("The server does not expect " + message.type()
                    + " from a game program.");
        }
    }

    private Table liveTable(HostedGame game, String tableId) {
        Table table = tablesById.get(tableId);
        // Late messages from a match that has just ended are normal, and not worth an error.
        return table != null && table.game() == game && table.isPlaying() ? table : null;
    }

    private PlayerSession seatedPlayer(HostedGame game, String tableId, String playerId) {
        Table table = liveTable(game, tableId);
        if (table == null) {
            return null;
        }
        for (PlayerSession p : table.seated()) {
            if (p.id().equals(playerId)) {
                return p;
            }
        }
        return null;
    }

    // ---- players -------------------------------------------------------------

    /** Names are unique server-wide, so "* alice is ready" always means one person. */
    synchronized PlayerSession join(Endpoint out, String name) {
        String wanted = name.strip();
        if (!Names.isPlayerName(wanted)) {
            out.err(Names.whyNotPlayerName(wanted));
            return null;
        }
        if (playersByKey.containsKey(key(wanted))) {
            out.err("Somebody here is already called " + wanted + "."
                    + " Pick another name.");
            return null;
        }
        PlayerSession player = new PlayerSession(newId("p"), out, wanted);
        playersByKey.put(key(wanted), player);
        out.send(Message.withText(MessageType.NAME_OK, wanted));
        return player;
    }

    synchronized void disconnect(PlayerSession player) {
        if (playersByKey.remove(key(player.name())) == null) {
            return;
        }
        leaveTable(player, player.name() + " disconnected", false);
    }

    synchronized void fromPlayer(PlayerSession player, Message message) {
        Table table = player.table();
        if (table != null && table.isPlaying()) {
            duringMatch(player, table, message);
            return;
        }
        if (table != null) {
            atTable(player, table, message);
            return;
        }
        inLobby(player, message);
    }

    private void inLobby(PlayerSession player, Message message) {
        switch (message.type()) {
            case LIST_GAMES -> listGames(player);
            case LIST_TABLES -> listTables(player, message.arg(0));
            case CREATE_TABLE -> createTable(player, message.arg(0), message.arg(1));
            case JOIN_TABLE -> joinTable(player, message.arg(0));
            case NAME -> player.out().err("You already have a name.");
            default -> player.out().err("You are in the lobby — pick a game first.");
        }
    }

    private void atTable(PlayerSession player, Table table, Message message) {
        switch (message.type()) {
            case READY -> setReady(player, table, true);
            case UNREADY -> setReady(player, table, false);
            case LEAVE -> leaveTable(player, player.name() + " left", true);
            case WHO -> who(player, table);
            case CHAT -> tellAll(table, Message.withText(MessageType.CHATLINE,
                    player.name() + ": " + message.text()));
            case JOIN_TABLE -> player.out().err("You are already at " + table.name()
                    + ". Type 'leave' first.");
            case ANSWER -> player.out().err("Nothing is asking you anything yet."
                    + " The match starts when everybody is ready.");
            default -> player.out().err("You are at " + table.name()
                    + ", waiting to start. Try ready, unready, who or leave.");
        }
    }

    private void duringMatch(PlayerSession player, Table table, Message message) {
        switch (message.type()) {
            case ANSWER -> {
                if (!player.isPrompted()) {
                    // Not buffered: keeping it would turn an aside into a move, later.
                    player.out().err("It's not your turn.");
                    return;
                }
                player.clearPrompt();
                table.game().out().send(Message.withText(MessageType.INPUT, table.id(),
                        player.id(), message.text()));
            }
            case LEAVE -> leaveTable(player, player.name() + " left", true);
            case CHAT -> player.out().err("The match is running, so what you type goes to"
                    + " the game. Wait until it is over to chat.");
            case WHO -> who(player, table);
            default -> player.out().err("The match has started. Just answer what the game"
                    + " asks you, or type 'leave' to walk away from the table.");
        }
    }

    // ---- the lobby -----------------------------------------------------------

    private void listGames(PlayerSession player) {
        player.out().send(Message.of(MessageType.GAME_LIST, String.valueOf(games.size())));
        for (HostedGame game : games.values()) {
            player.out().send(Message.withText(MessageType.GAME_ENTRY, game.id(),
                    String.valueOf(game.tables().size()), String.valueOf(game.minPlayers()),
                    String.valueOf(game.maxPlayers()), game.name()));
            player.out().send(Message.withText(MessageType.GAME_DESC, game.id(),
                    game.description()));
        }
    }

    private void listTables(PlayerSession player, String gameId) {
        HostedGame game = games.get(gameId);
        if (game == null) {
            player.out().err("That game is not in the lobby any more —"
                    + " whoever was running it has stopped.");
            player.out().send(Message.of(MessageType.TABLE_LIST, "-", "0"));
            return;
        }
        player.out().send(Message.of(MessageType.TABLE_LIST, gameId,
                String.valueOf(game.tables().size())));
        for (Table table : game.tables()) {
            player.out().send(Message.of(MessageType.TABLE_ENTRY, table.name(),
                    String.valueOf(table.seated().size()),
                    String.valueOf(game.maxPlayers()), table.state()));
        }
    }

    private void createTable(PlayerSession player, String gameId, String name) {
        HostedGame game = games.get(gameId);
        if (game == null) {
            player.out().err("That game is not in the lobby any more —"
                    + " whoever was running it has stopped.");
            return;
        }
        if (!Names.isTableName(name)) {
            player.out().err(Names.whyNotTableName(name));
            return;
        }
        if (tablesByKey.containsKey(key(name))) {
            player.out().err("There is already a table called " + name
                    + ". Join it, or pick another name.");
            return;
        }
        if (game.tables().size() >= maxTablesPerGame) {
            player.out().err(game.name() + " already has " + maxTablesPerGame
                    + " tables, which is as many as it is allowed.");
            return;
        }
        Table table = new Table(newId("t"), name, game);
        tablesByKey.put(key(name), table);
        tablesById.put(table.id(), table);
        game.tables().add(table);
        seat(player, table);
    }

    private void joinTable(PlayerSession player, String name) {
        Table table = tablesByKey.get(key(name));
        if (table == null) {
            player.out().err("There is no table called " + name + ".");
            return;
        }
        if (table.isPlaying()) {
            player.out().err(table.name() + " is in the middle of a match."
                    + " Wait for it to finish, or start a table of your own.");
            return;
        }
        if (table.isFull()) {
            player.out().err(table.name() + " is full ("
                    + table.seated().size() + "/" + table.game().maxPlayers() + ").");
            return;
        }
        seat(player, table);
    }

    private void seat(PlayerSession player, Table table) {
        table.seated().add(player);
        player.sitAt(table);
        player.out().send(Message.withText(MessageType.JOINED, table.name(),
                String.valueOf(table.seated().size()),
                String.valueOf(table.game().maxPlayers()), table.game().name()));
        // A new arrival resets nobody: whoever was ready stays ready.
        tellOthers(table, player, "* " + player.name() + " joined ("
                + table.seated().size() + "/" + table.game().maxPlayers() + ")");
    }

    private void setReady(PlayerSession player, Table table, boolean ready) {
        if (player.isReady() == ready) {
            player.out().notice(ready ? "You are already ready." : "You were not ready.");
            return;
        }
        player.setReady(ready);
        int needed = Math.max(table.seated().size(), table.game().minPlayers());
        tellAll(table, Message.withText(MessageType.NOTICE,
                "* " + player.name() + " is " + (ready ? "ready" : "not ready any more")
                        + " (" + table.readyCount() + "/" + needed + ")"));
        if (table.everybodyReady()) {
            startMatch(table);
        }
    }

    private void who(PlayerSession player, Table table) {
        StringBuilder sb = new StringBuilder("At " + table.name() + " ("
                + table.game().name() + "):");
        for (PlayerSession p : table.seated()) {
            sb.append("\n  ").append(p.name());
            if (!table.isPlaying()) {
                sb.append(p.isReady() ? "  ready" : "  not ready");
            }
        }
        player.out().notice(sb.toString());
    }

    /**
     * Leaves the table. During a match this ends the match for everybody — the room never
     * shrinks, so that a student's {@code players().get(0)} is always somebody.
     */
    private void leaveTable(PlayerSession player, String reason, boolean backToLobby) {
        Table table = player.table();
        if (table == null) {
            return;
        }
        boolean wasPlaying = table.isPlaying();
        table.seated().remove(player);
        player.stand();

        if (wasPlaying) {
            table.game().out().send(Message.of(MessageType.PLAYER_GONE, table.id(),
                    player.id()));
            endMatch(table, "Game ended: " + reason + ".");
        } else {
            tellAll(table, Message.withText(MessageType.NOTICE, "* " + reason + " ("
                    + table.seated().size() + "/" + table.game().maxPlayers() + ")"));
        }
        if (backToLobby) {
            player.out().send(Message.withText(MessageType.LEFT, "You left " + table.name()
                    + "."));
        }
        if (table.seated().isEmpty()) {
            forget(table);
        } else if (table.everybodyReady()) {
            // The people still sitting there had all said ready before.
            startMatch(table);
        }
    }

    /** The table is empty, so it stops existing and its name is free again. */
    private void forget(Table table) {
        tablesByKey.remove(key(table.name()));
        tablesById.remove(table.id());
        table.game().tables().remove(table);
    }

    // ---- matches -------------------------------------------------------------

    private void startMatch(Table table) {
        table.setPlaying(true);
        Endpoint host = table.game().out();
        host.send(Message.of(MessageType.TABLE_START, table.id(),
                String.valueOf(table.seated().size())));
        for (PlayerSession p : table.seated()) {
            p.setReady(false);
            p.clearPrompt();
            host.send(Message.withText(MessageType.TABLE_SEAT, table.id(), p.id(), p.name()));
        }
        host.send(Message.of(MessageType.TABLE_GO, table.id()));
        tellAll(table, Message.withText(MessageType.MATCH_START,
                "—— " + table.game().name() + " starts ——"));
    }

    /** Ends a match exactly once, whoever noticed first: the game, a disconnect or a timeout. */
    private void endMatch(Table table, String reason) {
        if (!table.isPlaying()) {
            return;
        }
        table.setPlaying(false);
        String ending = reason == null || reason.isBlank()
                ? "—— the match is over ——"
                : "—— " + reason + " ——";
        for (PlayerSession p : table.seated()) {
            p.setReady(false);
            p.clearPrompt();
        }
        tellAll(table, Message.withText(MessageType.MATCH_END, ending));
        tellAll(table, Message.withText(MessageType.NOTICE,
                "You are back at " + table.name() + ". Type 'ready' to play again."));
    }

    /**
     * A player who leaves a game waiting forever is treated as gone. The match ends; they keep
     * their seat, because a slow answer is not the same as walking out.
     */
    synchronized void endIdleMatches(long idleSeconds) {
        for (Table table : List.copyOf(tablesById.values())) {
            if (!table.isPlaying()) {
                continue;
            }
            for (PlayerSession p : List.copyOf(table.seated())) {
                if (p.isPrompted() && p.waitingSeconds() >= idleSeconds) {
                    table.game().out().send(Message.of(MessageType.PLAYER_GONE, table.id(),
                            p.id()));
                    endMatch(table, "Game ended: " + p.name() + " did not answer in time");
                    break;
                }
            }
        }
    }

    // ---- sending -------------------------------------------------------------

    private void tellAll(Table table, Message message) {
        for (PlayerSession p : table.seated()) {
            p.out().send(message);
        }
    }

    private void tellOthers(Table table, PlayerSession except, String notice) {
        Message message = Message.withText(MessageType.NOTICE, notice);
        for (PlayerSession p : table.seated()) {
            if (p != except) {
                p.out().send(message);
            }
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    // ---- for tests and status ------------------------------------------------

    synchronized List<String> tableNames() {
        return new ArrayList<>(tablesByKey.keySet());
    }

    synchronized int gameCount() {
        return games.size();
    }

    synchronized int playerCount() {
        return playersByKey.size();
    }
}
