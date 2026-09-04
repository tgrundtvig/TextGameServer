package textgame.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import textgame.protocol.Message;
import textgame.protocol.MessageType;

/** The blocking {@code ask} family, and the re-prompting the student never writes. */
class AskTest {

    private final ScriptedServer server = new ScriptedServer();

    @AfterEach
    void tearDown() {
        server.shutdown();
    }

    @Test
    void askReturnsTheLineExactlyAsTyped() {
        AtomicReference<String> got = new AtomicReference<>();
        server.start(new TestGame(room -> got.set(room.players().get(0).ask("Your name?"))));
        server.startTable("t1", "alice");

        assertEquals("Your name?", server.answerPrompt("t1", "p1", "  Ada Lovelace  "));
        server.expect(MessageType.ENDMATCH);
        assertEquals("  Ada Lovelace  ", got.get());
    }

    @Test
    void askIntReAsksUntilItGetsAWholeNumber() {
        AtomicReference<Integer> got = new AtomicReference<>();
        server.start(new TestGame(room -> got.set(room.players().get(0).askInt("How many?"))));
        server.startTable("t1", "alice");

        assertEquals("How many?", server.answerPrompt("t1", "p1", "banana"));
        assertEquals("Please type a whole number.", server.answerPrompt("t1", "p1", "3.5"));
        assertEquals("Please type a whole number.", server.answerPrompt("t1", "p1", " 42 "));
        server.expect(MessageType.ENDMATCH);
        assertEquals(42, got.get());
    }

    @Test
    void askIntWithRangeNamesTheRangeEveryTime() {
        AtomicReference<Integer> got = new AtomicReference<>();
        server.start(new TestGame(room ->
                got.set(room.players().get(0).askInt("Your guess?", 1, 100))));
        server.startTable("t1", "alice");

        assertEquals("Your guess?", server.answerPrompt("t1", "p1", "0"));
        assertEquals("Please type a whole number between 1 and 100.",
                server.answerPrompt("t1", "p1", "101"));
        assertEquals("Please type a whole number between 1 and 100.",
                server.answerPrompt("t1", "p1", "banana"));
        assertEquals("Please type a whole number between 1 and 100.",
                server.answerPrompt("t1", "p1", "7"));
        server.expect(MessageType.ENDMATCH);
        assertEquals(7, got.get());
    }

    @Test
    void askDoubleTakesDecimals() {
        AtomicReference<Double> got = new AtomicReference<>();
        server.start(new TestGame(room ->
                got.set(room.players().get(0).askDouble("How much?"))));
        server.startTable("t1", "alice");

        server.answerPrompt("t1", "p1", "lots");
        assertEquals("Please type a number.", server.answerPrompt("t1", "p1", "2.5"));
        server.expect(MessageType.ENDMATCH);
        assertEquals(2.5, got.get());
    }

    @Test
    void askYesNoAcceptsTheShortForms() {
        AtomicReference<Boolean> got = new AtomicReference<>();
        server.start(new TestGame(room ->
                got.set(room.players().get(0).askYesNo("Play again?"))));
        server.startTable("t1", "alice");

        server.answerPrompt("t1", "p1", "maybe");
        assertEquals("Please answer yes or no.", server.answerPrompt("t1", "p1", "Y"));
        server.expect(MessageType.ENDMATCH);
        assertTrue(got.get());
    }

    @Test
    void askChoiceShowsANumberedMenuAndReturnsTheOptionText() {
        AtomicReference<String> got = new AtomicReference<>();
        server.start(new TestGame(room -> got.set(
                room.players().get(0).askChoice("Your move?", "rock", "paper", "scissors"))));
        server.startTable("t1", "alice");

        assertEquals("""
                Your move?
                  1) rock
                  2) paper
                  3) scissors""", server.answerPrompt("t1", "p1", "4"));
        assertEquals("Please type a number between 1 and 3.",
                server.answerPrompt("t1", "p1", "2"));
        server.expect(MessageType.ENDMATCH);
        assertEquals("paper", got.get());
    }

    @Test
    void askChoiceIndexCountsFromZero() {
        AtomicReference<Integer> got = new AtomicReference<>();
        server.start(new TestGame(room -> got.set(
                room.players().get(0).askChoiceIndex("Attack who?", "goblin", "troll"))));
        server.startTable("t1", "alice");

        server.answerPrompt("t1", "p1", "2");
        server.expect(MessageType.ENDMATCH);
        assertEquals(1, got.get());
    }

    @Test
    void tellGoesToOnePlayerAndTellAllToEverybody() {
        server.start(new TestGame(room -> {
            room.tellAll("Welcome.");
            room.players().get(1).tell("Psst — you are the impostor.");
        }));
        server.startTable("t1", "alice", "bob");

        Message all = server.expect(MessageType.MSG_ALL);
        assertEquals("t1", all.arg(0));
        assertEquals("Welcome.", all.text());

        Message one = server.expect(MessageType.MSG_ONE);
        assertEquals("p2", one.arg(1));
        assertEquals("Psst — you are the impostor.", one.text());
        server.expect(MessageType.ENDMATCH);
    }

    @Test
    void playersKeepTheirNamesAndJoinOrder() {
        AtomicReference<String> got = new AtomicReference<>();
        server.start(new TestGame(room -> {
            StringBuilder sb = new StringBuilder();
            room.players().forEach(p -> sb.append(p.name()).append(" "));
            got.set(sb.toString().trim());
        }));
        server.startTable("t1", "alice", "bob", "carol");
        server.expect(MessageType.ENDMATCH);
        assertEquals("alice bob carol", got.get());
    }
}
