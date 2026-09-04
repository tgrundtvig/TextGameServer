package textgame.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import textgame.Match;
import textgame.protocol.Message;
import textgame.protocol.MessageType;

/**
 * The ways a match can end. All of them are the framework's problem, never the student's:
 * nothing here is caught inside a {@code play} method.
 */
class MatchEndingTest {

    private final ScriptedServer server = new ScriptedServer();

    @AfterEach
    void tearDown() {
        server.shutdown();
    }

    @Test
    void aNormalReturnEndsTheMatchQuietly() {
        server.start(new TestGame(room -> room.tellAll("that's all")));
        server.startTable("t1", "alice");
        server.expect(MessageType.MSG_ALL);
        Message end = server.expect(MessageType.ENDMATCH);
        assertEquals("t1", end.arg(0));
        assertEquals("", end.text());
    }

    @Test
    void aDisconnectUnblocksTheWaitingAskAndEndsTheMatch() {
        AtomicBoolean sawException = new AtomicBoolean();
        AtomicBoolean returnedNormally = new AtomicBoolean();
        server.start(new TestGame(room -> {
            try {
                room.players().get(0).ask("Waiting for you...");
                returnedNormally.set(true);
            } catch (RuntimeException e) {
                sawException.set(true);
                throw e;
            }
        }));
        server.startTable("t1", "alice", "bob");

        server.expect(MessageType.PROMPT_ONE);
        server.playerLeaves("t1", "p2");

        Message end = server.expect(MessageType.ENDMATCH);
        assertEquals("Game ended: bob disconnected.", end.text());
        assertTrue(sawException.get(), "the ask must stop waiting");
        assertFalse(returnedNormally.get());
    }

    @Test
    void aDisconnectAlsoUnblocksEverybodyInAnAskAll() throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        server.start(new TestGame(room -> {
            try {
                room.askAll("Everybody answer");
            } finally {
                finished.countDown();
            }
        }));
        server.startTable("t1", "alice", "bob", "carol");

        server.expect(MessageType.PROMPT_ONE);
        server.expect(MessageType.PROMPT_ONE);
        server.expect(MessageType.PROMPT_ONE);
        server.playerLeaves("t1", "p3");

        assertTrue(finished.await(5, TimeUnit.SECONDS), "askAll must not hang after a disconnect");
        assertEquals("Game ended: carol disconnected.",
                server.expect(MessageType.ENDMATCH).text());
    }

    @Test
    void aStudentCrashEndsOnlyThatMatchAndPrintsTheTraceLocally() {
        PrintStream realErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
        try {
            server.start(new TestGame(room -> {
                int[] scores = new int[2];
                room.tellAll("scores: " + scores[5]);   // the classic
            }));
            server.startTable("t1", "alice");

            Message end = server.expect(MessageType.ENDMATCH);
            assertTrue(end.text().contains("has a bug"), end.text());
            assertFalse(end.text().contains("ArrayIndexOutOfBounds"),
                    "players must not be shown a Java type name: " + end.text());
        } finally {
            System.setErr(realErr);
        }
        String console = captured.toString(StandardCharsets.UTF_8);
        assertTrue(console.contains("crashed while playing with alice"), console);
        assertTrue(console.contains("ArrayIndexOutOfBoundsException"), console);
        assertTrue(console.contains("Your other tables are still running"), console);
    }

    @Test
    void oneTableCrashingLeavesTheOtherTablePlaying() {
        PrintStream realErr = System.err;
        System.setErr(new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
        try {
            Match match = room -> {
                if (room.players().get(0).name().equals("doomed")) {
                    throw new IllegalStateException("boom");
                }
                room.tellAll("still here: " + room.players().get(0).ask("ok?"));
            };
            server.start(new TestGame(match));
            server.startTable("t1", "survivor");
            server.startTable("t2", "doomed");

            // t2 dies; t1 is still waiting on its prompt and then finishes normally.
            List<Message> seen = server.drainUntilEndMatch();
            assertEquals("t2", seen.get(seen.size() - 1).arg(0));
            assertTrue(seen.stream().anyMatch(
                    m -> m.type() == MessageType.PROMPT_ONE && m.arg(0).equals("t1")));

            server.answer("t1", "p1", "yes");
            assertEquals("still here: yes", server.expect(MessageType.MSG_ALL).text());
            assertEquals("t1", server.expect(MessageType.ENDMATCH).arg(0));
        } finally {
            System.setErr(realErr);
        }
    }

    @Test
    void tellingADeadRoomStopsTheMatchRatherThanLoopingForever() {
        AtomicBoolean loopedForever = new AtomicBoolean();
        server.start(new TestGame(room -> {
            for (int i = 0; i < 1_000_000; i++) {
                room.tellAll("tick " + i);
            }
            loopedForever.set(true);
        }));
        server.startTable("t1", "alice", "bob");
        server.expect(MessageType.MSG_ALL);
        server.playerLeaves("t1", "p1");

        server.drainUntilEndMatch();
        assertFalse(loopedForever.get());
    }
}
