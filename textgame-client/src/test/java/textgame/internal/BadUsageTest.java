package textgame.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import textgame.GameServer;
import textgame.GameServerException;
import textgame.protocol.MessageType;

/** Mistakes a student can actually make, and what the framework says about them. */
class BadUsageTest {

    private final ScriptedServer server = new ScriptedServer();

    @AfterEach
    void tearDown() {
        server.shutdown();
    }

    @Test
    void connectComplainsInPlainLanguageAboutABadPort() {
        GameServerException e = org.junit.jupiter.api.Assertions.assertThrows(
                GameServerException.class, () -> GameServer.connect("localhost", 99999));
        assertTrue(e.getMessage().contains("between 1 and 65535"), e.getMessage());
    }

    @Test
    void hostingWithNoGameSaysWhatToPass() {
        GameServerException e = org.junit.jupiter.api.Assertions.assertThrows(
                GameServerException.class,
                () -> GameServer.connect("localhost", 4000).host(null));
        assertTrue(e.getMessage().contains("host(new NumberDuel())"), e.getMessage());
    }

    @Test
    void aChoiceWithNoOptionsFailsOnTheStudentsOwnConsole() {
        PrintStream realErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            server.start(new TestGame(room ->
                    room.players().get(0).askChoice("Pick one")));
            server.startTable("t1", "alice");
            server.expect(MessageType.ENDMATCH);
        } finally {
            System.setErr(realErr);
        }
        assertTrue(captured.toString(StandardCharsets.UTF_8)
                .contains("at least one option to pick from"),
                captured.toString(StandardCharsets.UTF_8));
    }

    @Test
    void onlyWithSomebodyElsesPlayerSaysWhoIsActuallyInTheRoom() {
        PrintStream realErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        AtomicReference<textgame.Player> stranger = new AtomicReference<>();
        try {
            server.start(new TestGame(room -> {
                if (stranger.get() == null) {
                    stranger.set(room.players().get(0));
                    return;
                }
                room.only(stranger.get()).tellAll("hi");
            }));
            server.startTable("t1", "alice");
            server.expect(MessageType.ENDMATCH);
            server.startTable("t2", "bob");
            server.expect(MessageType.ENDMATCH);
        } finally {
            System.setErr(realErr);
        }
        String console = captured.toString(StandardCharsets.UTF_8);
        assertTrue(console.contains("alice is not in this room"), console);
        assertTrue(console.contains("This room holds: bob"), console);
    }

    @Test
    void aGameThatDescribesItselfBadlyIsCaughtBeforeAnybodyCanJoin() {
        GameServerException e = org.junit.jupiter.api.Assertions.assertThrows(
                GameServerException.class,
                () -> new HostRuntime(new ScriptedServer(),
                        new TestGame(room -> { }, 4, 2)).run());
        assertTrue(e.getMessage().contains("minPlayers() is 4"), e.getMessage());
        assertTrue(e.getMessage().contains("maxPlayers() is 2"), e.getMessage());
    }

    @Test
    void newMatchReturningNullSaysWhatItShouldReturn() {
        PrintStream realErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            server.start(new TestGame(null));
            server.startTable("t1", "alice");
            assertEquals(MessageType.ENDMATCH, server.next().type());
        } finally {
            System.setErr(realErr);
        }
        assertTrue(captured.toString(StandardCharsets.UTF_8)
                .contains("newMatch() returned null"),
                captured.toString(StandardCharsets.UTF_8));
    }
}
