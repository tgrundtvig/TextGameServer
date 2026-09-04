package textgame.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import textgame.Game;
import textgame.GameServer;
import textgame.Match;
import textgame.protocol.Message;
import textgame.protocol.MessageType;
import textgame.server.TextGameServer;

/** The lobby rules from DESIGN.md section 5, stated as tests. */
class LobbyTest {

    /** A game that needs exactly two, so "full" is easy to reach. */
    static class Duet implements Game {
        public String name()        { return "Duet"; }
        public String description() { return "For exactly two."; }
        public int    minPlayers()  { return 2; }
        public int    maxPlayers()  { return 2; }
        public Match  newMatch()    { return room -> room.players().get(0).ask("forever?"); }
    }

    private TextGameServer server;
    private int port;
    private final List<Thread> hosts = new ArrayList<>();
    private final List<ScriptedPlayer> open = new ArrayList<>();

    @BeforeEach
    void startServer() throws Exception {
        server = TextGameServer.start(0, null);
        port = server.port();
    }

    @AfterEach
    void stopServer() {
        open.forEach(ScriptedPlayer::close);
        server.close();
        hosts.forEach(Thread::interrupt);
    }

    private void hostGame(Game game) {
        Thread t = new Thread(() -> GameServer.connect("localhost", port).host(game),
                "game-" + game.name());
        t.setDaemon(true);
        t.start();
        hosts.add(t);
    }

    private ScriptedPlayer player(String name) throws Exception {
        ScriptedPlayer p = new ScriptedPlayer(port, name);
        open.add(p);
        p.await(MessageType.NAME_OK);
        return p;
    }

    private String gameId(ScriptedPlayer p, String gameName) {
        for (int attempt = 0; attempt < 50; attempt++) {
            p.say(MessageType.LIST_GAMES);
            Message list = p.await(MessageType.GAME_LIST);
            for (int i = 0; i < Integer.parseInt(list.arg(0)); i++) {
                Message entry = p.await(MessageType.GAME_ENTRY);
                if (entry.text().equals(gameName)) {
                    return entry.arg(0);
                }
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        throw new AssertionError(gameName + " never appeared in the lobby");
    }

    @Test
    void twoPeopleCannotShareAName() throws Exception {
        player("alice");
        ScriptedPlayer other = new ScriptedPlayer(port, "ALICE");
        open.add(other);
        assertTrue(other.await(MessageType.ERR).text()
                .contains("already called ALICE"), other.transcript());

        other.send(Message.withText(MessageType.NAME, "alice2"));
        assertEquals("alice2", other.await(MessageType.NAME_OK).text());
    }

    @Test
    void aNameHasToBeOneWord() throws Exception {
        ScriptedPlayer p = new ScriptedPlayer(port, "alice");
        open.add(p);
        p.await(MessageType.NAME_OK);
        ScriptedPlayer spaced = new ScriptedPlayer(port, "Ada Lovelace");
        open.add(spaced);
        // The space is the problem, so the space is what gets mentioned — with a fix.
        String said = spaced.await(MessageType.ERR).text();
        assertTrue(said.contains("cannot contain spaces"), said);
        assertTrue(said.contains("Try Ada-Lovelace."), said);
    }

    @Test
    void tableNamesAreUniqueServerWideAndCaseInsensitive() throws Exception {
        hostGame(new NumberDuel());
        hostGame(new RockPaperScissors());
        ScriptedPlayer alice = player("alice");
        ScriptedPlayer bob = player("bob");
        String duel = gameId(alice, "Number Duel");
        String rps = gameId(bob, "Rock Paper Scissors");

        alice.send(Message.of(MessageType.CREATE_TABLE, duel, "test1"));
        alice.await(MessageType.JOINED);

        // Same name, different game: still refused, because names are server-wide.
        bob.send(Message.of(MessageType.CREATE_TABLE, rps, "TEST1"));
        assertTrue(bob.await(MessageType.ERR).text().contains("already a table called TEST1"),
                bob.transcript());

        // And typing it anywhere jumps straight there.
        bob.send(Message.of(MessageType.JOIN_TABLE, "TeSt1"));
        assertEquals("test1", bob.await(MessageType.JOINED).arg(0));
    }

    @Test
    void aTableNameIsFreedWhenTheLastPersonLeaves() throws Exception {
        hostGame(new NumberDuel());
        ScriptedPlayer alice = player("alice");
        String duel = gameId(alice, "Number Duel");

        alice.send(Message.of(MessageType.CREATE_TABLE, duel, "once"));
        alice.await(MessageType.JOINED);
        alice.say(MessageType.LEAVE);
        alice.await(MessageType.LEFT);

        ScriptedPlayer bob = player("bob");
        bob.send(Message.of(MessageType.CREATE_TABLE, gameId(bob, "Number Duel"), "once"));
        assertEquals("once", bob.await(MessageType.JOINED).arg(0));
    }

    @Test
    void aFullTableIsListedButNotJoinable() throws Exception {
        hostGame(new Duet());
        ScriptedPlayer alice = player("alice");
        ScriptedPlayer bob = player("bob");
        ScriptedPlayer carol = player("carol");
        String duet = gameId(alice, "Duet");

        alice.send(Message.of(MessageType.CREATE_TABLE, duet, "pair"));
        alice.await(MessageType.JOINED);
        bob.send(Message.of(MessageType.JOIN_TABLE, "pair"));
        bob.await(MessageType.JOINED);

        carol.send(Message.of(MessageType.JOIN_TABLE, "pair"));
        assertTrue(carol.await(MessageType.ERR).text().contains("pair is full (2/2)"),
                carol.transcript());

        carol.send(Message.of(MessageType.LIST_TABLES, gameId(carol, "Duet")));
        assertEquals("1", carol.await(MessageType.TABLE_LIST).arg(1));
        Message entry = carol.await(MessageType.TABLE_ENTRY);
        assertEquals("pair", entry.arg(0));
        assertEquals("FULL", entry.arg(3));
    }

    @Test
    void aTableInTheMiddleOfAMatchIsListedAsPlayingAndNotJoinable() throws Exception {
        hostGame(new NumberDuel());
        ScriptedPlayer alice = player("alice");
        ScriptedPlayer bob = player("bob");
        ScriptedPlayer carol = player("carol");
        String duel = gameId(alice, "Number Duel");

        alice.send(Message.of(MessageType.CREATE_TABLE, duel, "busy"));
        alice.await(MessageType.JOINED);
        bob.send(Message.of(MessageType.JOIN_TABLE, "busy"));
        bob.await(MessageType.JOINED);
        alice.say(MessageType.READY);
        bob.say(MessageType.READY);
        alice.await(MessageType.MATCH_START);

        carol.send(Message.of(MessageType.JOIN_TABLE, "busy"));
        assertTrue(carol.await(MessageType.ERR).text().contains("middle of a match"),
                carol.transcript());

        carol.send(Message.of(MessageType.LIST_TABLES, gameId(carol, "Number Duel")));
        carol.await(MessageType.TABLE_LIST);
        assertEquals("PLAYING", carol.await(MessageType.TABLE_ENTRY).arg(3));
    }

    @Test
    void aNewArrivalResetsNobody() throws Exception {
        hostGame(new NumberDuel());
        ScriptedPlayer alice = player("alice");
        ScriptedPlayer bob = player("bob");
        String duel = gameId(alice, "Number Duel");

        alice.send(Message.of(MessageType.CREATE_TABLE, duel, "waiting"));
        alice.await(MessageType.JOINED);
        alice.say(MessageType.READY);
        assertTrue(alice.await(MessageType.NOTICE).text().contains("alice is ready (1/2)"));

        // Alice is alone and ready. Nothing starts, because minPlayers is 2.
        bob.send(Message.of(MessageType.JOIN_TABLE, "waiting"));
        bob.await(MessageType.JOINED);
        assertTrue(alice.await(MessageType.NOTICE).text().contains("bob joined (2/4)"));

        // Alice was not reset by Bob arriving: Bob readying up is enough to start.
        bob.say(MessageType.READY);
        assertTrue(bob.await(MessageType.NOTICE).text().contains("bob is ready (2/2)"));
        assertTrue(alice.await(MessageType.MATCH_START).text().contains("Number Duel"));
    }

    @Test
    void readyingUpOneByOneCountsTowardsEverybody() throws Exception {
        hostGame(new NumberDuel());
        ScriptedPlayer alice = player("alice");
        ScriptedPlayer bob = player("bob");
        ScriptedPlayer carol = player("carol");
        String duel = gameId(alice, "Number Duel");

        alice.send(Message.of(MessageType.CREATE_TABLE, duel, "trio"));
        alice.await(MessageType.JOINED);
        for (ScriptedPlayer p : List.of(bob, carol)) {
            p.send(Message.of(MessageType.JOIN_TABLE, "trio"));
            p.await(MessageType.JOINED);
        }
        assertTrue(alice.await(MessageType.NOTICE).text().contains("bob joined (2/4)"));
        assertTrue(alice.await(MessageType.NOTICE).text().contains("carol joined (3/4)"));

        bob.say(MessageType.READY);
        assertTrue(alice.await(MessageType.NOTICE).text().contains("bob is ready (1/3)"));
        carol.say(MessageType.READY);
        assertTrue(alice.await(MessageType.NOTICE).text().contains("carol is ready (2/3)"));

        // Nothing has started yet: unanimity, not a majority.
        alice.say(MessageType.WHO);
        assertTrue(alice.await(MessageType.NOTICE).text().contains("alice  not ready"));

        alice.say(MessageType.READY);
        assertTrue(alice.await(MessageType.MATCH_START).text().contains("Number Duel"));
    }

    @Test
    void anUnreadyPersonHoldsTheTable() throws Exception {
        hostGame(new NumberDuel());
        ScriptedPlayer alice = player("alice");
        ScriptedPlayer bob = player("bob");
        String duel = gameId(alice, "Number Duel");

        alice.send(Message.of(MessageType.CREATE_TABLE, duel, "hold"));
        alice.await(MessageType.JOINED);
        bob.send(Message.of(MessageType.JOIN_TABLE, "hold"));
        bob.await(MessageType.JOINED);

        alice.say(MessageType.READY);
        alice.say(MessageType.UNREADY);
        assertTrue(bob.await(MessageType.NOTICE).text().contains("alice is ready (1/2)"));
        assertTrue(bob.await(MessageType.NOTICE).text()
                .contains("alice is not ready any more (0/2)"));

        bob.say(MessageType.READY);
        bob.await(MessageType.NOTICE);
        alice.say(MessageType.READY);
        alice.await(MessageType.MATCH_START);
    }

    @Test
    void invalidTableNamesAreRefusedInPlainLanguage() throws Exception {
        hostGame(new NumberDuel());
        ScriptedPlayer alice = player("alice");
        String duel = gameId(alice, "Number Duel");

        alice.send(Message.of(MessageType.CREATE_TABLE, duel, "way-too-long-a-name-for-a-table"));
        String tooLong = alice.await(MessageType.ERR).text();
        assertTrue(tooLong.contains("31 characters long"), tooLong);
        assertTrue(tooLong.contains("the most is 20"), tooLong);

        alice.send(Message.of(MessageType.CREATE_TABLE, duel, "tab!e"));
        String odd = alice.await(MessageType.ERR).text();
        assertTrue(odd.contains("letters, digits, - and _"), odd);

        // The server checks too, even though the client cannot send a space in this field.
        alice.send(Message.of(MessageType.CREATE_TABLE, duel, "fine-name"));
        assertEquals("fine-name", alice.await(MessageType.JOINED).arg(0));
    }

    @Test
    void whenTheGameProgramStopsItsTablesGoAndItsPlayersGoBackToTheLobby() throws Exception {
        ScriptedGame duel = new ScriptedGame(port, "Number Duel", 2, 4, "Guess my number.");
        ScriptedPlayer alice = player("alice");
        alice.send(Message.of(MessageType.CREATE_TABLE, gameId(alice, "Number Duel"), "doomed"));
        alice.await(MessageType.JOINED);

        // The student closes their laptop.
        duel.close();

        assertTrue(alice.await(MessageType.LEFT).text().contains("no longer running"),
                alice.transcript());

        // The game is out of the lobby, and its table name is free again.
        alice.say(MessageType.LIST_GAMES);
        assertEquals("0", alice.await(MessageType.GAME_LIST).arg(0));
    }

    @Test
    void theLobbyShowsHowManyTablesEachGameHas() throws Exception {
        hostGame(new NumberDuel());
        ScriptedPlayer alice = player("alice");
        String duel = gameId(alice, "Number Duel");
        alice.send(Message.of(MessageType.CREATE_TABLE, duel, "one"));
        alice.await(MessageType.JOINED);
        alice.say(MessageType.LEAVE);
        alice.await(MessageType.LEFT);

        alice.say(MessageType.LIST_GAMES);
        alice.await(MessageType.GAME_LIST);
        assertEquals("0", alice.await(MessageType.GAME_ENTRY).arg(1));

        alice.send(Message.of(MessageType.CREATE_TABLE, duel, "two"));
        alice.await(MessageType.JOINED);
        alice.say(MessageType.LIST_GAMES);
        // At a table, listing games is refused: you are somewhere, not browsing.
        assertTrue(alice.await(MessageType.ERR).text().contains("waiting to start"));
    }
}
