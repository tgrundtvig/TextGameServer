package textgame.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import textgame.Answers;
import textgame.Player;
import textgame.protocol.Message;
import textgame.protocol.MessageType;

/** Asking everybody at once — the thing that makes rock-paper-scissors possible. */
class AskAllTest {

    private final ScriptedServer server = new ScriptedServer();

    @AfterEach
    void tearDown() {
        server.shutdown();
    }

    /** Collects the prompts for a whole askAll, which arrive in no particular order. */
    private List<Message> collectPrompts(int count) {
        List<Message> prompts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            prompts.add(server.expect(MessageType.PROMPT_ONE));
        }
        return prompts;
    }

    @Test
    void everybodyIsAskedAtOnceAndTheCallWaitsForAll() {
        AtomicReference<String> got = new AtomicReference<>();
        server.start(new TestGame(room -> {
            Answers moves = room.askAllChoice("Rock, paper or scissors?",
                    "rock", "paper", "scissors");
            StringBuilder sb = new StringBuilder();
            for (Player p : room.players()) {
                sb.append(p.name()).append("=").append(moves.get(p))
                        .append("/").append(moves.getIndex(p)).append(" ");
            }
            got.set(sb.toString().trim());
        }));
        server.startTable("t1", "alice", "bob");

        List<Message> prompts = collectPrompts(2);
        assertTrue(prompts.get(0).text().contains("1) rock"));
        // Answer out of order: bob is quicker than alice.
        server.answer("t1", "p2", "3");
        server.answer("t1", "p1", "1");

        server.expect(MessageType.ENDMATCH);
        assertEquals("alice=rock/0 bob=scissors/2", got.get());
    }

    @Test
    void eachPlayerIsReAskedOnTheirOwnWithoutHoldingUpTheOthers() {
        AtomicReference<String> got = new AtomicReference<>();
        server.start(new TestGame(room -> {
            Answers n = room.askAllInt("Pick a number 1-10", 1, 10);
            got.set(n.getInt(room.players().get(0)) + "+" + n.getInt(room.players().get(1)));
        }));
        server.startTable("t1", "alice", "bob");

        collectPrompts(2);
        server.answer("t1", "p1", "99");        // alice is wrong, and gets asked again
        Message again = server.expect(MessageType.PROMPT_ONE);
        assertEquals("p1", again.arg(1));
        assertEquals("Please type a whole number between 1 and 10.", again.text());

        server.answer("t1", "p2", "4");         // bob was never disturbed
        server.answer("t1", "p1", "9");

        server.expect(MessageType.ENDMATCH);
        assertEquals("9+4", got.get());
    }

    @Test
    void askAllYesNoCollectsVotes() {
        AtomicReference<String> got = new AtomicReference<>();
        server.start(new TestGame(room -> {
            Answers votes = room.askAllYesNo("Play again?");
            int yes = 0;
            for (Player p : room.players()) {
                if (votes.getYesNo(p)) {
                    yes++;
                }
            }
            got.set(yes + " of " + votes.players().size());
        }));
        server.startTable("t1", "alice", "bob", "carol");

        collectPrompts(3);
        server.answer("t1", "p1", "yes");
        server.answer("t1", "p2", "n");
        server.answer("t1", "p3", "YES");

        server.expect(MessageType.ENDMATCH);
        assertEquals("2 of 3", got.get());
    }

    @Test
    void onlyAsksASubsetAndWithoutAsksTheRest() {
        AtomicReference<String> got = new AtomicReference<>();
        server.start(new TestGame(room -> {
            Player spy = room.players().get(1);
            room.only(spy).tellAll("You are the spy.");
            room.without(spy).tellAll("Find the spy.");
            Answers guesses = room.without(spy).askAll("Who is it?");
            got.set(guesses.players().size() + ": " + guesses.get(room.players().get(0))
                    + "," + guesses.get(room.players().get(2)));
        }));
        server.startTable("t1", "alice", "bob", "carol");

        // only(bob) is a room of one, so it is told directly rather than as a broadcast.
        Message toSpy = server.expect(MessageType.MSG_ONE);
        assertEquals("p2", toSpy.arg(1));
        assertEquals("You are the spy.", toSpy.text());

        List<Message> toOthers = List.of(server.expect(MessageType.MSG_ONE),
                server.expect(MessageType.MSG_ONE));
        assertEquals(List.of("p1", "p3"),
                toOthers.stream().map(m -> m.arg(1)).toList());

        List<Message> prompts = collectPrompts(2);
        assertFalse(prompts.stream().anyMatch(m -> m.arg(1).equals("p2")),
                "the spy must not be asked");
        server.answer("t1", "p1", "bob");
        server.answer("t1", "p3", "bob");

        server.expect(MessageType.ENDMATCH);
        assertEquals("2: bob,bob", got.get());
    }

    @Test
    void tellAllOnAFullRoomIsOneBroadcast() {
        server.start(new TestGame(room -> room.tellAll("hello")));
        server.startTable("t1", "alice", "bob");
        assertEquals(MessageType.MSG_ALL, server.next().type());
        server.expect(MessageType.ENDMATCH);
    }
}
