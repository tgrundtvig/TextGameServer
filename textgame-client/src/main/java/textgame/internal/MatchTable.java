package textgame.internal;

import java.util.ArrayList;
import java.util.List;
import textgame.PlayerGoneException;
import textgame.protocol.Message;

/**
 * One table inside this game program: its seats, and whether its match is still alive.
 *
 * <p>A disconnect ends the whole match rather than shrinking the room, so "alive" is one flag
 * for the table, not one per player. When it goes out, every waiting {@code ask} is woken and
 * throws, wherever in the student's code it happens to be.
 */
public final class MatchTable {

    /**
     * The table whose match the current thread is playing. Inherited by the threads
     * {@code askAll} starts, so it holds there too. Lets a table tell the difference between
     * "this match is over" and "you are holding a player from an earlier match".
     */
    static final InheritableThreadLocal<MatchTable> CURRENT = new InheritableThreadLocal<>();

    /** Calls made after the end before the framework starts slowing them down. */
    private static final int FREE_CALLS_AFTER_END = 20;

    private final HostRuntime runtime;
    private final String id;
    private final int seatCount;
    private final List<PlayerImpl> seats = new ArrayList<>();

    private volatile String endReason;
    private int callsAfterEnd;
    private boolean warnedAboutSpinning;

    MatchTable(HostRuntime runtime, String id, int seatCount) {
        this.runtime = runtime;
        this.id = id;
        this.seatCount = seatCount;
    }

    String id() {
        return id;
    }

    boolean seatsFilled() {
        return seats.size() == seatCount;
    }

    void seat(String playerId, String name) {
        seats.add(new PlayerImpl(this, playerId, name));
    }

    List<PlayerImpl> seats() {
        return seats;
    }

    PlayerImpl find(String playerId) {
        for (PlayerImpl p : seats) {
            if (p.id().equals(playerId)) {
                return p;
            }
        }
        return null;
    }

    RoomImpl room() {
        return new RoomImpl(this, List.copyOf(seats));
    }

    void send(Message message) {
        runtime.send(message);
    }

    /** Whatever ended the match, phrased for players. */
    String endReason() {
        String reason = endReason;
        return reason == null ? "the match ended" : reason;
    }

    boolean isOver() {
        return endReason != null;
    }

    /** Ends the match for everybody and unblocks every waiting {@code ask}. */
    synchronized void end(String reason) {
        if (endReason != null) {
            return;
        }
        endReason = reason;
        for (PlayerImpl p : seats) {
            p.wake();
        }
    }

    void checkAlive() {
        if (endReason == null) {
            return;
        }
        MatchTable current = CURRENT.get();
        if (current != null && current != this && !current.isOver()) {
            // A live match is using one of our players — kept in a field from an earlier
            // match, most likely. That is a bug in the game, and it deserves a stack trace.
            throw new IllegalStateException("This Player belongs to a match that is already"
                    + " over. Players are only valid during the match they were handed to"
                    + " — get them from room.players() each time, and do not keep them in a"
                    + " static field.");
        }
        slowDownIfSpinning();
        throw new PlayerGoneException(endReason);
    }

    /**
     * A game that catches the exception and asks again would otherwise spin at full speed
     * forever, and one such table can starve every other table in the program. After a few
     * free calls, each further one pauses — the match is over, so nothing is lost — and the
     * student is told once what is happening.
     */
    private synchronized void slowDownIfSpinning() {
        callsAfterEnd++;
        if (callsAfterEnd <= FREE_CALLS_AFTER_END) {
            return;
        }
        if (!warnedAboutSpinning) {
            warnedAboutSpinning = true;
            System.err.println("[textgame] A match at this table ended (" + endReason + "),"
                    + " but the game keeps calling ask or tell. Something in play() is"
                    + " catching the exception that ends a match — let it through, or return"
                    + " from play() when it happens.");
        }
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
