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
}
