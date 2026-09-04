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

    private final HostRuntime runtime;
    private final String id;
    private final int seatCount;
    private final List<PlayerImpl> seats = new ArrayList<>();

    private volatile String endReason;

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
        if (endReason != null) {
            throw new PlayerGoneException(endReason);
        }
    }
}
