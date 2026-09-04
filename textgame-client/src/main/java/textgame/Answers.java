package textgame;

import java.util.List;

/**
 * What everybody answered, from one of the {@code Room.askAll} methods.
 *
 * <pre>{@code
 * Answers moves = room.askAllChoice("Rock, paper or scissors?", "rock", "paper", "scissors");
 * for (Player p : room.players()) {
 *     room.tellAll(p.name() + " played " + moves.get(p) + ".");
 * }
 * }</pre>
 *
 * <p>{@link #get} always works. The typed views only mean something for the matching ask:
 * {@link #getInt} goes with {@code askAllInt}, {@link #getIndex} with {@code askAllChoice},
 * and so on. Using the wrong one tells you which one you wanted.
 */
public interface Answers {

    /** Who was asked, in room order. */
    List<Player> players();

    /** What this player answered, as text. Always available. */
    String get(Player p);

    /** What this player answered, as a whole number. Goes with {@code askAllInt}. */
    int getInt(Player p);

    /** What this player answered, as a number. Goes with {@code askAllDouble}. */
    double getDouble(Player p);

    /** What this player answered, as a yes or a no. Goes with {@code askAllYesNo}. */
    boolean getYesNo(Player p);

    /** Which option this player picked, counting from zero. Goes with {@code askAllChoice}. */
    int getIndex(Player p);
}
