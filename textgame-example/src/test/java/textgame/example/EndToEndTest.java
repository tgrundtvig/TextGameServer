package textgame.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import textgame.Game;
import textgame.GameServer;
import textgame.protocol.Message;
import textgame.protocol.MessageType;
import textgame.server.TextGameServer;

/**
 * The regression test that matters: a real server, a real game program and real player
 * connections, all in one JVM, playing a whole match from the lobby to the end.
 */
class EndToEndTest {

    private TextGameServer server;
    private int port;
    private final List<Thread> hosts = new java.util.ArrayList<>();

    @BeforeEach
    void startServer() throws Exception {
        server = TextGameServer.start(0);
        port = server.port();
    }

    @AfterEach
    void stopServer() {
        server.close();
        for (Thread t : hosts) {
            t.interrupt();
        }
    }

    /** Launches a student's game program the way their IDE would. */
    private void hostGame(Game game) {
        Thread t = new Thread(() -> GameServer.connect("localhost", port).host(game),
                "game-" + game.name());
        t.setDaemon(true);
        t.start();
        hosts.add(t);
    }

    private ScriptedPlayer player(String name) throws Exception {
        ScriptedPlayer p = new ScriptedPlayer(port, name);
        assertEquals(name, p.await(MessageType.NAME_OK).text());
        return p;
    }

    /** Waits until the game program has actually registered and the lobby shows it. */
    private String findGame(ScriptedPlayer p, String gameName) {
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
    void twoPlayersFindAGameInTheLobbyAndPlayAWholeMatch() throws Exception {
        hostGame(new NumberDuel());

        try (ScriptedPlayer alice = player("alice"); ScriptedPlayer bob = player("bob")) {
            String gameId = findGame(alice, "Number Duel");

            // Alice makes a table; Bob finds it by name from the game prompt.
            alice.send(Message.of(MessageType.CREATE_TABLE, gameId, "duel"));
            Message joined = alice.await(MessageType.JOINED);
            assertEquals("duel", joined.arg(0));
            assertEquals("Number Duel", joined.text());

            bob.send(Message.of(MessageType.JOIN_TABLE, "duel"));
            assertEquals("2", bob.await(MessageType.JOINED).arg(1));
            assertTrue(alice.await(MessageType.NOTICE).text().contains("bob joined (2/4)"));

            // The table lobby is a chat room until everybody is ready.
            alice.send(Message.withText(MessageType.CHAT, "hi bob"));
            assertEquals("alice: hi bob", bob.await(MessageType.CHATLINE).text());

            alice.say(MessageType.READY);
            assertTrue(bob.await(MessageType.NOTICE).text().contains("alice is ready (1/2)"));
            bob.say(MessageType.READY);

            assertTrue(alice.await(MessageType.MATCH_START).text().contains("Number Duel"));
            assertTrue(bob.await(MessageType.MATCH_START).text().contains("Number Duel"));
            assertEquals("I am thinking of a number between 1 and 100.",
                    alice.await(MessageType.MSG).text());

            // Guess 1, 2, 3, ... in turn. The secret is somewhere in there.
            String ending = null;
            int guess = 1;
            outer:
            while (guess <= 101) {
                for (ScriptedPlayer p : List.of(alice, bob)) {
                    Message m = p.await(MessageType.PROMPT, MessageType.MATCH_END);
                    if (m.type() == MessageType.MATCH_END) {
                        ending = m.text();
                        if (p != alice) {
                            // Everybody is told; alice just had not looked yet.
                            assertEquals(ending, alice.await(MessageType.MATCH_END).text());
                        }
                        break outer;
                    }
                    assertEquals("Your guess?", m.text());
                    p.answer(String.valueOf(guess++));
                }
            }

            assertEquals("—— the match is over ——", ending);
            assertTrue(alice.transcript().contains("got it!"), alice.transcript());
            assertTrue(alice.transcript().contains("too high")
                    || alice.transcript().contains("too low"), alice.transcript());

            // The table drops back to its own lobby, with the same people still sitting at it.
            assertTrue(alice.await(MessageType.NOTICE).text()
                    .contains("You are back at duel. Type 'ready' to play again."));

            // And they can play again straight away.
            alice.say(MessageType.READY);
            bob.say(MessageType.READY);
            alice.await(MessageType.MATCH_START);
            assertEquals("I am thinking of a number between 1 and 100.",
                    alice.await(MessageType.MSG).text());
        }
    }

    @Test
    void badInputIsReAskedWithoutTheGameEverSeeingIt() throws Exception {
        hostGame(new NumberDuel());
        try (ScriptedPlayer alice = player("alice"); ScriptedPlayer bob = player("bob")) {
            String gameId = findGame(alice, "Number Duel");
            alice.send(Message.of(MessageType.CREATE_TABLE, gameId, "duel"));
            alice.await(MessageType.JOINED);
            bob.send(Message.of(MessageType.JOIN_TABLE, "duel"));
            bob.await(MessageType.JOINED);
            alice.say(MessageType.READY);
            bob.say(MessageType.READY);
            alice.await(MessageType.MATCH_START);

            assertEquals("Your guess?", alice.await(MessageType.PROMPT).text());
            alice.answer("banana");
            assertEquals("Please type a whole number between 1 and 100.",
                    alice.await(MessageType.PROMPT).text());
            alice.answer("500");
            assertEquals("Please type a whole number between 1 and 100.",
                    alice.await(MessageType.PROMPT).text());
            alice.answer("50");

            // Only now does anything reach the game, and only once.
            assertTrue(alice.await(MessageType.MSG).text().contains("alice guessed 50"));
        }
    }

    @Test
    void typingOutOfTurnIsRefusedAndNotBuffered() throws Exception {
        hostGame(new NumberDuel());
        try (ScriptedPlayer alice = player("alice"); ScriptedPlayer bob = player("bob")) {
            String gameId = findGame(alice, "Number Duel");
            alice.send(Message.of(MessageType.CREATE_TABLE, gameId, "duel"));
            alice.await(MessageType.JOINED);
            bob.send(Message.of(MessageType.JOIN_TABLE, "duel"));
            bob.await(MessageType.JOINED);
            alice.say(MessageType.READY);
            bob.say(MessageType.READY);
            bob.await(MessageType.MATCH_START);

            alice.await(MessageType.PROMPT);
            bob.answer("7");                                     // not bob's turn
            assertEquals("It's not your turn.", bob.await(MessageType.ERR).text());

            alice.answer("1");
            alice.await(MessageType.MSG);

            // Bob's aside was thrown away, not saved up and played as his move.
            assertEquals("Your guess?", bob.await(MessageType.PROMPT).text());
            bob.answer("2");
            assertTrue(bob.await(MessageType.MSG).text().contains("bob guessed 2"));
        }
    }

    @Test
    void oneDisconnectEndsTheMatchAndEverybodyIsTold() throws Exception {
        hostGame(new NumberDuel());
        try (ScriptedPlayer alice = player("alice")) {
            ScriptedPlayer bob = player("bob");
            String gameId = findGame(alice, "Number Duel");
            alice.send(Message.of(MessageType.CREATE_TABLE, gameId, "duel"));
            alice.await(MessageType.JOINED);
            bob.send(Message.of(MessageType.JOIN_TABLE, "duel"));
            bob.await(MessageType.JOINED);
            alice.say(MessageType.READY);
            bob.say(MessageType.READY);
            alice.await(MessageType.MATCH_START);
            alice.await(MessageType.PROMPT);

            bob.close();

            assertEquals("—— Game ended: bob disconnected. ——",
                    alice.await(MessageType.MATCH_END).text());
            assertTrue(alice.await(MessageType.NOTICE).text().contains("back at duel"));

            // The table survives, and alice is still sitting at it.
            alice.say(MessageType.WHO);
            assertTrue(alice.await(MessageType.NOTICE).text().contains("alice"));
        }
    }

    @Test
    void everybodyIsAskedAtOnceInRockPaperScissors() throws Exception {
        hostGame(new RockPaperScissors());
        try (ScriptedPlayer alice = player("alice"); ScriptedPlayer bob = player("bob")) {
            String gameId = findGame(alice, "Rock Paper Scissors");
            alice.send(Message.of(MessageType.CREATE_TABLE, gameId, "rps"));
            alice.await(MessageType.JOINED);
            bob.send(Message.of(MessageType.JOIN_TABLE, "rps"));
            bob.await(MessageType.JOINED);
            alice.say(MessageType.READY);
            bob.say(MessageType.READY);
            alice.await(MessageType.MATCH_START);

            for (int round = 1; round <= 3; round++) {
                // Both are prompted before either has answered — that is the whole point.
                Message toAlice = alice.await(MessageType.PROMPT);
                Message toBob = bob.await(MessageType.PROMPT);
                assertTrue(toAlice.text().contains("1) rock"), toAlice.text());
                assertTrue(toBob.text().contains("3) scissors"), toBob.text());
                alice.answer("1");
                bob.answer("3");
            }

            assertTrue(alice.await(MessageType.MATCH_END) != null);
            // rock beats scissors, three times.
            assertTrue(alice.transcript().contains("alice wins!"), alice.transcript());
        }
    }

    @Test
    void aSecretIsToldToOnePlayerAndNotTheOthers() throws Exception {
        hostGame(new Impostor());
        try (ScriptedPlayer alice = player("alice");
             ScriptedPlayer bob = player("bob");
             ScriptedPlayer carol = player("carol")) {
            String gameId = findGame(alice, "Impostor");
            alice.send(Message.of(MessageType.CREATE_TABLE, gameId, "spies"));
            alice.await(MessageType.JOINED);
            for (ScriptedPlayer p : List.of(bob, carol)) {
                p.send(Message.of(MessageType.JOIN_TABLE, "spies"));
                p.await(MessageType.JOINED);
            }
            for (ScriptedPlayer p : List.of(alice, bob, carol)) {
                p.say(MessageType.READY);
            }
            alice.await(MessageType.MATCH_START);

            List<String> words = new java.util.ArrayList<>();
            for (ScriptedPlayer p : List.of(alice, bob, carol)) {
                words.add(p.await(MessageType.MSG).text());
            }
            long distinct = words.stream().distinct().count();
            assertEquals(2, distinct, "exactly one player gets a different word: " + words);
            long odd = words.stream().filter(w -> words.indexOf(w) == words.lastIndexOf(w))
                    .count();
            assertEquals(1, odd, "only one impostor: " + words);
        }
    }
}
