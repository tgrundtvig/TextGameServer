package textgame.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import textgame.protocol.Message;
import textgame.protocol.MessageType;
import textgame.server.TextGameServer;

/**
 * The shared class password: what keeps the wider internet out of a lobby that a class is
 * using. Not strong security — a password everybody in a room knows never is — but the
 * difference between "my students" and "anybody who portscans the internet".
 */
class PasswordTest {

    private TextGameServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.close();
        }
        System.clearProperty("textgame.password");
    }

    private TextGameServer serverWith(String password) throws Exception {
        if (password == null) {
            System.clearProperty("textgame.password");
        } else {
            System.setProperty("textgame.password", password);
        }
        server = TextGameServer.start(0);
        return server;
    }

    @Test
    void theRightPasswordGetsYouIn() throws Exception {
        serverWith("rumraket");
        try (ScriptedPlayer p = new ScriptedPlayer(server.port(), "alice", "rumraket")) {
            assertEquals("alice", p.await(MessageType.NAME_OK).text());
        }
    }

    @Test
    void noPasswordAtAllIsTurnedAwayWithSomethingToDoAboutIt() throws Exception {
        serverWith("rumraket");
        try (ScriptedPlayer p = new ScriptedPlayer(server.port(), "alice", null)) {
            String bye = p.await(MessageType.BYE).text();
            assertTrue(bye.contains("needs the class password"), bye);
            assertTrue(bye.contains("kodeord.txt"), bye);
        }
    }

    @Test
    void theWrongPasswordSaysSoRatherThanFailingSilently() throws Exception {
        serverWith("rumraket");
        try (ScriptedPlayer p = new ScriptedPlayer(server.port(), "alice", "raketrum")) {
            String bye = p.await(MessageType.BYE).text();
            assertTrue(bye.contains("not the class password"), bye);
            assertTrue(bye.contains("kodeord.txt"), bye);
        }
    }

    @Test
    void aGameProgramNeedsThePasswordTooAndIsNotAWayIn() throws Exception {
        serverWith("rumraket");
        try (ScriptedGame g = new ScriptedGame(server.port(), "Sneaky", 2, 4, "not welcome")) {
            String bye = g.await(MessageType.BYE).text();
            assertTrue(bye.contains("needs the class password"), bye);
        }
    }

    @Test
    void aGameProgramWithThePasswordCanHostNormally() throws Exception {
        serverWith("rumraket");
        try (ScriptedGame g = new ScriptedGame(server.port(), "Number Duel", 2, 4, "Guess.",
                                               "rumraket");
             ScriptedPlayer p = new ScriptedPlayer(server.port(), "alice", "rumraket")) {
            assertEquals("g1", g.await(MessageType.REGISTERED).arg(0));
            p.await(MessageType.NAME_OK);
            p.say(MessageType.LIST_GAMES);
            assertEquals("1", p.await(MessageType.GAME_LIST).arg(0));
            assertEquals("Number Duel", p.await(MessageType.GAME_ENTRY).text());
        }
    }

    @Test
    void surroundingWhitespaceInTheFileDoesNotLockAnybodyOut() throws Exception {
        serverWith("rumraket");
        try (ScriptedPlayer p = new ScriptedPlayer(server.port(), "alice", "  rumraket\n")) {
            assertEquals("alice", p.await(MessageType.NAME_OK).text());
        }
    }

    @Test
    void aServerWithoutAPasswordStillAcceptsAClientThatSendsOne() throws Exception {
        serverWith(null);
        try (ScriptedPlayer p = new ScriptedPlayer(server.port(), "alice", "leftover")) {
            assertEquals("alice", p.await(MessageType.NAME_OK).text());
        }
    }

    @Test
    void aServerWithoutAPasswordLetsAnybodyIn() throws Exception {
        serverWith(null);
        try (ScriptedPlayer p = new ScriptedPlayer(server.port(), "alice", null)) {
            assertEquals("alice", p.await(MessageType.NAME_OK).text());
        }
    }

    @Test
    void nothingWorksBeforeThePasswordIsAccepted() throws Exception {
        serverWith("rumraket");
        try (ScriptedPlayer p = new ScriptedPlayer(server.port(), null, null)) {
            // Skips NAME entirely and goes straight for the lobby.
            p.send(Message.of(MessageType.LIST_GAMES));
            assertTrue(p.await(MessageType.BYE).text().contains("needs the class password"));
        }
    }
}
