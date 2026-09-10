package textgame.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Socket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import textgame.protocol.Message;
import textgame.protocol.MessageChannel;
import textgame.protocol.MessageType;
import textgame.server.TextGameServer;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.io.IOException;

/** Server behaviour that no student ever sees, and that has to be right anyway. */
class ServerRulesTest {

    private TextGameServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.close();
        }
        System.clearProperty("textgame.idleSeconds");
    }

    @Test
    void aGameThatRegistersNonsenseIsToldWhyBeforeBeingDropped() throws Exception {
        server = TextGameServer.start(0, null);
        try (ScriptedGame bad = new ScriptedGame(server.port(), "Backwards", 5, 2, "nope")) {
            assertTrue(bad.await(MessageType.ERR).text().contains("minPlayers()"));
            // The goodbye has to survive the connection being closed right after it.
            assertTrue(bad.await(MessageType.BYE).text().contains("could not be registered"));
        }
    }

    @Test
    void aPlayerWhoNeverAnswersEndsTheMatchForEverybody() throws Exception {
        System.setProperty("textgame.idleSeconds", "1");
        server = TextGameServer.start(0, null);
        int port = server.port();

        try (ScriptedGame duel = new ScriptedGame(port, "Number Duel", 2, 4, "Guess.");
             ScriptedPlayer alice = new ScriptedPlayer(port, "alice");
             ScriptedPlayer bob = new ScriptedPlayer(port, "bob")) {
            alice.await(MessageType.NAME_OK);
            bob.await(MessageType.NAME_OK);

            alice.say(MessageType.LIST_GAMES);
            alice.await(MessageType.GAME_LIST);
            String gameId = alice.await(MessageType.GAME_ENTRY).arg(0);

            alice.send(Message.of(MessageType.CREATE_TABLE, gameId, "slow"));
            alice.await(MessageType.JOINED);
            bob.send(Message.of(MessageType.JOIN_TABLE, "slow"));
            bob.await(MessageType.JOINED);
            alice.say(MessageType.READY);
            bob.say(MessageType.READY);

            String tableId = duel.await(MessageType.TABLE_START).arg(0);
            String alicesId = duel.await(MessageType.TABLE_SEAT).arg(1);
            duel.await(MessageType.TABLE_GO);
            duel.send(Message.withText(MessageType.PROMPT_ONE, tableId, alicesId,
                    "Your guess?"));
            assertEquals("Your guess?", alice.await(MessageType.PROMPT).text());

            // Alice wanders off. Nobody types anything ever again.
            assertEquals(tableId, duel.await(MessageType.PLAYER_GONE).arg(0));
            assertTrue(bob.await(MessageType.MATCH_END).text()
                    .contains("alice did not answer in time"), bob.transcript());

            assertTrue(bob.await(MessageType.NOTICE).text().contains("back at slow"));

            // Alice keeps her seat: being slow is not the same as walking out.
            bob.say(MessageType.WHO);
            assertTrue(bob.await(MessageType.NOTICE).text().contains("alice"));
        }
    }

    @Test
    void aConnectionThatSaysSomethingUnexpectedFirstIsToldWhatToDo() throws Exception {
        server = TextGameServer.start(0, null);
        try (ScriptedPlayer stranger = new ScriptedPlayer(server.port(), "ok")) {
            stranger.await(MessageType.NAME_OK);
        }
        try (textgame.protocol.MessageChannel raw =
                     textgame.protocol.MessageChannel.connect("localhost", server.port())) {
            raw.send(Message.of(MessageType.READY));
            Message reply = raw.receive();
            assertEquals(MessageType.ERR, reply.type());
            assertTrue(reply.text().contains("a game program sends REGISTER"), reply.text());
        }
    }

    // ---- machines that vanish --------------------------------------------------
    //
    // A laptop that reboots, sleeps or drops off the wifi never closes its connection, so the
    // server's read simply stops getting anything. Keepalive turns that silence into an error
    // (MessageChannelTest checks it is on); once the machine is back it answers the first
    // probe with a reset. A test cannot pull a cable, but it can send exactly that reset, by
    // closing a socket with linger zero — so what is tested here is what the server does the
    // moment the kernel reports the connection dead.

    /** A connection whose close is a reset rather than a goodbye, like a rebooted machine. */
    private static MessageChannel abruptConnection(int port) throws Exception {
        Socket socket = new Socket("localhost", port);
        socket.setSoLinger(true, 0);
        return new MessageChannel(socket);
    }

    @Test
    void aPlayerWhoseMachineDiesGetsTheirNameBack() throws Exception {
        server = TextGameServer.start(0, null);
        int port = server.port();

        MessageChannel firstMani = abruptConnection(port);
        firstMani.send(Message.withText(MessageType.NAME, "mani"));
        assertEquals(MessageType.NAME_OK, firstMani.receive().type());

        // While that connection lives, the name is taken.
        try (ScriptedPlayer sameName = new ScriptedPlayer(port, "mani")) {
            assertTrue(sameName.await(MessageType.ERR).text().contains("already called mani"));
        }

        firstMani.close();

        // And once it is dead, the name comes back — without anybody restarting the server.
        boolean freed = false;
        for (int attempt = 0; attempt < 100 && !freed; attempt++) {
            try (ScriptedPlayer mani = new ScriptedPlayer(port, "mani")) {
                freed = mani.await(MessageType.NAME_OK, MessageType.ERR).type()
                        == MessageType.NAME_OK;
            }
            if (!freed) {
                Thread.sleep(50);
            }
        }
        assertTrue(freed, "mani's name was still taken five seconds after the machine died");
    }

    @Test
    void aGameWhoseMachineDiesLeavesTheLobby() throws Exception {
        server = TextGameServer.start(0, null);
        int port = server.port();

        MessageChannel blackjack = abruptConnection(port);
        blackjack.send(Message.withText(MessageType.REGISTER, "2", "4", "Blackjack"));
        assertEquals(MessageType.REGISTERED, blackjack.receive().type());

        try (ScriptedPlayer mani = new ScriptedPlayer(port, "mani")) {
            mani.await(MessageType.NAME_OK);
            mani.say(MessageType.LIST_GAMES);
            assertEquals("1", mani.await(MessageType.GAME_LIST).arg(0));

            blackjack.close();

            String listed = "1";
            for (int attempt = 0; attempt < 100 && !listed.equals("0"); attempt++) {
                Thread.sleep(50);
                mani.say(MessageType.LIST_GAMES);
                listed = mani.await(MessageType.GAME_LIST).arg(0);
            }
            assertEquals("0", listed, "Blackjack was still in the lobby five seconds after"
                    + " the machine running it died");
        }
    }

    // ---- one id per match ------------------------------------------------------

    /** Three players at a table, all ready; returns the game and the match id. */
    private static String startThree(ScriptedGame game, ScriptedPlayer a, ScriptedPlayer b,
                                     ScriptedPlayer c, String tableName) {
        a.await(MessageType.NAME_OK);
        b.await(MessageType.NAME_OK);
        c.await(MessageType.NAME_OK);
        a.say(MessageType.LIST_GAMES);
        a.await(MessageType.GAME_LIST);
        String gameId = a.await(MessageType.GAME_ENTRY).arg(0);
        a.send(Message.of(MessageType.CREATE_TABLE, gameId, tableName));
        a.await(MessageType.JOINED);
        b.send(Message.of(MessageType.JOIN_TABLE, tableName));
        b.await(MessageType.JOINED);
        c.send(Message.of(MessageType.JOIN_TABLE, tableName));
        c.await(MessageType.JOINED);
        a.say(MessageType.READY);
        b.say(MessageType.READY);
        c.say(MessageType.READY);
        String matchId = game.await(MessageType.TABLE_START).arg(0);
        game.await(MessageType.TABLE_GO);
        return matchId;
    }

    @Test
    void aTableGetsAFreshIdEveryMatchSoALeftoverEndCannotHitTheNextOne() throws Exception {
        server = TextGameServer.start(0, null);
        int port = server.port();
        try (ScriptedGame duel = new ScriptedGame(port, "Number Duel", 2, 4, "Guess.");
             ScriptedPlayer alice = new ScriptedPlayer(port, "alice");
             ScriptedPlayer bob = new ScriptedPlayer(port, "bob");
             ScriptedPlayer carol = new ScriptedPlayer(port, "carol")) {
            String first = startThree(duel, alice, bob, carol, "again");

            // Alice walks out: the match ends for everybody, the table stays.
            alice.say(MessageType.LEAVE);
            assertEquals(first, duel.await(MessageType.PLAYER_GONE).arg(0));
            bob.await(MessageType.MATCH_END);
            carol.await(MessageType.MATCH_END);

            // Bob and carol go again — and get a new id.
            bob.say(MessageType.READY);
            carol.say(MessageType.READY);
            String second = duel.await(MessageType.TABLE_START).arg(0);
            duel.await(MessageType.TABLE_GO);
            assertNotEquals(first, second);

            // The old match's thread finally gives up, a little late. Nothing happens.
            duel.send(Message.withText(MessageType.ENDMATCH, first, "Game ended: late."));
            duel.send(Message.withText(MessageType.MSG_ALL, second, "still playing"));
            assertEquals("still playing", bob.await(MessageType.MSG).text());
            assertNull(bob.maybe(MessageType.MATCH_END, 300), bob.transcript());

            duel.send(Message.withText(MessageType.ENDMATCH, second, ""));
            bob.await(MessageType.MATCH_END);
        }
    }

    // ---- the lobby stays usable when things go away ---------------------------

    @Test
    void aSecondGameWithTheSameNameIsRefusedAndToldWhy() throws Exception {
        server = TextGameServer.start(0, null);
        try (ScriptedGame first = new ScriptedGame(server.port(), "Mit spil", 2, 4, "one");
             ScriptedGame second = new ScriptedGame(server.port(), "mit SPIL", 2, 4, "two")) {
            first.await(MessageType.REGISTERED);
            assertTrue(second.await(MessageType.ERR).text().contains("already in the lobby"));
            second.await(MessageType.BYE);
        }
    }

    @Test
    void whenAGameVanishesThePlayerGetsAFreshListInsteadOfADeadEnd() throws Exception {
        server = TextGameServer.start(0, null);
        int port = server.port();
        ScriptedGame blackjack = new ScriptedGame(port, "Blackjack", 2, 4, "Twenty-one.");
        blackjack.await(MessageType.REGISTERED);
        try (ScriptedPlayer mani = new ScriptedPlayer(port, "mani")) {
            mani.await(MessageType.NAME_OK);
            mani.say(MessageType.LIST_GAMES);
            mani.await(MessageType.GAME_LIST);
            String gameId = mani.await(MessageType.GAME_ENTRY).arg(0);

            // The student restarts their program while mani is looking at it.
            blackjack.close();
            mani.say(MessageType.LIST_GAMES);
            for (int i = 0; i < 50 && !"0".equals(mani.await(MessageType.GAME_LIST).arg(0)); i++) {
                Thread.sleep(50);
                mani.say(MessageType.LIST_GAMES);
            }

            // Both things the client can still do with the old id end in a fresh list.
            mani.send(Message.of(MessageType.LIST_TABLES, gameId));
            assertTrue(mani.await(MessageType.ERR).text().contains("not in the lobby any more"));
            assertEquals("0", mani.await(MessageType.GAME_LIST).arg(0));

            mani.send(Message.of(MessageType.CREATE_TABLE, gameId, "mine"));
            mani.await(MessageType.ERR);
            assertEquals("0", mani.await(MessageType.GAME_LIST).arg(0));
        }
    }

    @Test
    void takingDownAGameMidMatchDoesNotTellPlayersToTypeReady() throws Exception {
        server = TextGameServer.start(0, null);
        int port = server.port();
        ScriptedGame duel = new ScriptedGame(port, "Number Duel", 2, 4, "Guess.");
        try (ScriptedPlayer alice = new ScriptedPlayer(port, "alice");
             ScriptedPlayer bob = new ScriptedPlayer(port, "bob");
             ScriptedPlayer carol = new ScriptedPlayer(port, "carol")) {
            startThree(duel, alice, bob, carol, "doomed");
            duel.close();
            bob.await(MessageType.MATCH_END);
            bob.await(MessageType.LEFT);
            assertFalse(bob.transcript().contains("Type 'ready'"), bob.transcript());
        }
    }

    @Test
    void theIdleTimeoutCanBeSwitchedOff() throws Exception {
        System.setProperty("textgame.idleSeconds", "0");
        server = TextGameServer.start(0, null);
        int port = server.port();
        try (ScriptedGame duel = new ScriptedGame(port, "Number Duel", 2, 4, "Guess.");
             ScriptedPlayer alice = new ScriptedPlayer(port, "alice");
             ScriptedPlayer bob = new ScriptedPlayer(port, "bob");
             ScriptedPlayer carol = new ScriptedPlayer(port, "carol")) {
            String matchId = startThree(duel, alice, bob, carol, "slow");
            duel.send(Message.withText(MessageType.PROMPT_ONE, matchId, "p1", "Your guess?"));
            assertNull(duel.maybe(MessageType.PLAYER_GONE, 2500),
                    "0 must mean off, not 'at once'");
        }
    }

    // ---- a stranger at the door ----------------------------------------------

    @Test
    void aLineTooLongToBeHonestDropsTheConnection() throws Exception {
        server = TextGameServer.start(0, null);
        try (MessageChannel raw = MessageChannel.connect("localhost", server.port())) {
            raw.send(Message.withText(MessageType.NAME, "A".repeat(70_000)));
            Message reply;
            try {
                reply = raw.receive();
            } catch (IOException dropped) {
                reply = null;
            }
            assertNull(reply, "the server should have closed the connection, not answered");
        }
    }

    @Test
    void garbageBeforeThePasswordGetsAGoodbyeAndNothingElse() throws Exception {
        server = TextGameServer.start(0, "hemmeligt");
        try (MessageChannel raw = MessageChannel.connect("localhost", server.port())) {
            raw.send(Message.withText(MessageType.CHAT, "hello?"));   // fine to parse...
            Message first = raw.receive();
            assertEquals(MessageType.BYE, first.type());
            assertTrue(first.text().contains("password"), first.text());
        }
        try (java.net.Socket socket = new java.net.Socket("localhost", server.port())) {
            socket.getOutputStream().write("FLURB nonsense\n".getBytes(
                    java.nio.charset.StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            Message first = new MessageChannel(socket).receive();
            assertEquals(MessageType.BYE, first.type());
            assertFalse(first.text().contains("could not read"), first.text());
        }
    }
}
