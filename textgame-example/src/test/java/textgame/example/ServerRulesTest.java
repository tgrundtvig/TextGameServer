package textgame.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import textgame.protocol.Message;
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
}
