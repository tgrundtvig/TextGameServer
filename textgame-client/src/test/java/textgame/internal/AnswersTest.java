package textgame.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import textgame.Answers;
import textgame.Player;
import org.junit.jupiter.api.Test;

/**
 * The sharp edge named in DESIGN.md section 4: a typed getter only means something for the
 * ask it belongs to, and when it does not, it has to say which ask the student wanted.
 */
class AnswersTest {

    /** A stand-in player: Answers only ever uses identity and name. */
    private record Seat(String name) implements Player {
        @Override public void tell(String text) { }
        @Override public String ask(String q) { return null; }
        @Override public int askInt(String q) { return 0; }
        @Override public int askInt(String q, int min, int max) { return 0; }
        @Override public double askDouble(String q) { return 0; }
        @Override public boolean askYesNo(String q) { return false; }
        @Override public String askChoice(String q, String... o) { return null; }
        @Override public int askChoiceIndex(String q, String... o) { return 0; }
    }

    private static final Player ALICE = new Seat("alice");
    private static final Player BOB = new Seat("bob");

    private static Answers answers(AnswersImpl.Kind kind, String aliceSaid, String bobSaid) {
        return new AnswersImpl(List.of(ALICE, BOB),
                new java.util.LinkedHashMap<>(Map.of(ALICE, aliceSaid, BOB, bobSaid)),
                new java.util.LinkedHashMap<>(), kind);
    }

    @Test
    void getAlwaysWorks() {
        Answers a = answers(AnswersImpl.Kind.RAW, "  hello  ", "42");
        assertEquals("  hello  ", a.get(ALICE));
        assertEquals(List.of(ALICE, BOB), a.players());
    }

    @Test
    void typedGettersWorkForTheirOwnAsk() {
        assertEquals(42, answers(AnswersImpl.Kind.INT, "42", "7").getInt(ALICE));
        assertEquals(2.5, answers(AnswersImpl.Kind.DOUBLE, "2.5", "1.0").getDouble(ALICE));
        assertTrue(answers(AnswersImpl.Kind.YES_NO, "yes", "no").getYesNo(ALICE));
        assertEquals(42.0, answers(AnswersImpl.Kind.INT, "42", "7").getDouble(ALICE));
    }

    @Test
    void getIntOnRawAnswersWorksWhenTheTextHappensToBeANumber() {
        assertEquals(42, answers(AnswersImpl.Kind.RAW, " 42 ", "7").getInt(ALICE));
    }

    @Test
    void getIntOnRawAnswersExplainsItselfInsteadOfThrowingNumberFormatException() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> answers(AnswersImpl.Kind.RAW, "banana", "7").getInt(ALICE));
        String m = e.getMessage();
        assertTrue(m.contains("\"banana\""), m);
        assertTrue(m.contains("alice"), m);
        assertTrue(m.contains("askAll, which does not check"), m);
        assertTrue(m.contains("use askAllInt"), m);
    }

    @Test
    void getYesNoOnRawAnswersExplainsItself() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> answers(AnswersImpl.Kind.RAW, "sure thing", "no").getYesNo(ALICE));
        assertTrue(e.getMessage().contains("use askAllYesNo"), e.getMessage());
    }

    @Test
    void aTypedGetterOnTheWrongAskNamesTheRightOne() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> answers(AnswersImpl.Kind.YES_NO, "yes", "no").getInt(ALICE));
        assertTrue(e.getMessage().contains("getInt only works on answers from askAllInt"),
                e.getMessage());
        assertTrue(e.getMessage().contains("came from askAllYesNo"), e.getMessage());
    }

    @Test
    void getIndexNeedsAskAllChoice() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> answers(AnswersImpl.Kind.RAW, "rock", "paper").getIndex(ALICE));
        assertTrue(e.getMessage().contains("askAllChoice"), e.getMessage());
    }

    @Test
    void askingAboutSomebodyWhoWasNotAskedSaysSo() {
        Answers a = answers(AnswersImpl.Kind.RAW, "x", "y");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> a.get(new Seat("carol")));
        assertTrue(e.getMessage().contains("carol was not asked"), e.getMessage());
        assertTrue(e.getMessage().contains("alice, bob"), e.getMessage());
    }
}
