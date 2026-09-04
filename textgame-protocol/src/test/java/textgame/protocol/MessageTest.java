package textgame.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class MessageTest {

    private static Message roundTrip(Message m) {
        String line = m.encode();
        assertEquals(-1, line.indexOf('\n'), "encoded message must fit on one line");
        assertEquals(-1, line.indexOf('\r'), "encoded message must fit on one line");
        return Message.decode(line);
    }

    @Test
    void wordOnly() {
        Message m = Message.of(MessageType.LIST_GAMES);
        assertEquals("LIST_GAMES", m.encode());
        assertEquals(m, roundTrip(m));
        assertNull(m.text());
    }

    @Test
    void fixedArgsOnly() {
        Message m = Message.of(MessageType.PLAYER_GONE, "t7", "p3");
        assertEquals("PLAYER_GONE t7 p3", m.encode());
        assertEquals(m, roundTrip(m));
    }

    @Test
    void fourArgsAndText() {
        Message m = Message.withText(MessageType.GAME_ENTRY, "g1", "2", "2", "4", "Number Duel");
        assertEquals("GAME_ENTRY g1 2 2 4 Number Duel", m.encode());
        Message back = roundTrip(m);
        assertEquals(List.of("g1", "2", "2", "4"), back.args());
        assertEquals("Number Duel", back.text());
    }

    @Test
    void textWithSpacesSurvives() {
        Message m = Message.withText(MessageType.MSG, "you   have   many   spaces");
        assertEquals("you   have   many   spaces", roundTrip(m).text());
    }

    @Test
    void leadingSpacesInTextSurvive() {
        assertEquals("   indented", roundTrip(Message.withText(MessageType.MSG, "   indented")).text());
        assertEquals("  indented",
                roundTrip(Message.withText(MessageType.MSG_ALL, "t1", "  indented")).text());
    }

    @Test
    void emptyTextSurvives() {
        assertEquals("", roundTrip(Message.withText(MessageType.MSG, "")).text());
        assertEquals("", roundTrip(Message.withText(MessageType.ENDMATCH, "t1", "")).text());
    }

    @Test
    void newlinesAndCarriageReturnsSurvive() {
        String art = "  __\r\n / o\\\n \\__/\n";
        assertEquals(art, roundTrip(Message.withText(MessageType.MSG, art)).text());
    }

    @Test
    void backslashesSurvive() {
        String s = "C:\\games\\ and a lone \\ and \\n that is not a newline";
        assertEquals(s, roundTrip(Message.withText(MessageType.MSG, s)).text());
    }

    @Test
    void trailingBackslashSurvives() {
        assertEquals("ends with \\", roundTrip(Message.withText(MessageType.MSG, "ends with \\")).text());
    }

    @Test
    void everyTypeRoundTrips() {
        for (MessageType type : MessageType.values()) {
            List<String> args = new java.util.ArrayList<>();
            for (int i = 0; i < type.argCount(); i++) {
                args.add("a" + i);
            }
            String text = type.hasText() ? "some \\ text\nwith lines" : null;
            Message m = new Message(type, args, text);
            assertEquals(m, roundTrip(m), type + " did not round-trip");
        }
    }

    @Test
    void unknownWordIsRejected() {
        ProtocolException e = assertThrows(ProtocolException.class, () -> Message.decode("FLURB x"));
        org.junit.jupiter.api.Assertions.assertTrue(e.getMessage().contains("FLURB"));
    }

    @Test
    void missingArgumentIsRejected() {
        assertThrows(ProtocolException.class, () -> Message.decode("PLAYER_GONE t7"));
    }

    @Test
    void unexpectedTextIsRejected() {
        assertThrows(ProtocolException.class, () -> Message.decode("LIST_GAMES surprise"));
    }

    @Test
    void wrongArityIsRejectedWhenBuilding() {
        assertThrows(ProtocolException.class, () -> Message.of(MessageType.PLAYER_GONE, "onlyOne"));
        assertThrows(ProtocolException.class, () -> Message.of(MessageType.MSG));
        assertThrows(ProtocolException.class, () -> Message.withText(MessageType.LIST_GAMES, "text"));
    }

    @Test
    void argumentsWithSpacesAreRejected() {
        assertThrows(ProtocolException.class,
                () -> Message.of(MessageType.JOIN_TABLE, "two words"));
        assertThrows(ProtocolException.class, () -> Message.of(MessageType.JOIN_TABLE, ""));
    }

    @Test
    void trailingCarriageReturnFromCrlfPeerIsIgnored() {
        assertEquals(Message.of(MessageType.READY), Message.decode("READY\r"));
    }
}
