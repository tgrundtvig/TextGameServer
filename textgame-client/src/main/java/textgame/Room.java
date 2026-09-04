package textgame;

import java.util.List;

/**
 * The group of players at one table.
 *
 * <p>Talking to one player at a time is what {@link Player} is for. A {@code Room} is for
 * talking to several at once, and for asking them all the same question simultaneously.
 *
 * <p>{@link #players()} never changes while the match runs, so {@code players().get(0)} is
 * always somebody. If a player disconnects the whole match ends instead of the room
 * shrinking — you never have to check whether somebody is still there.
 */
public interface Room {

    /** Everyone at this table, in the order they joined. Never changes during the match. */
    List<Player> players();

    /** Sends one-way text to every player here. */
    void tellAll(String text);

    /** A view of just these players. Everything a {@code Room} can do works on it. */
    Room only(Player... some);

    /** A view of just these players. Everything a {@code Room} can do works on it. */
    Room only(List<Player> some);

    /** A view of everyone here except these players. */
    Room without(Player... some);

    /** A view of everyone here except these players. */
    Room without(List<Player> some);

    /** Asks everyone at once and waits for all the answers, exactly as typed. */
    Answers askAll(String question);

    /** Asks everyone at once for a whole number. Each player is re-asked on their own. */
    Answers askAllInt(String question);

    /** Asks everyone at once for a whole number from {@code min} to {@code max}. */
    Answers askAllInt(String question, int min, int max);

    /** Asks everyone at once for a number. Decimals allowed. */
    Answers askAllDouble(String question);

    /** Asks everyone the same yes/no question at once. Good for votes and "play again?". */
    Answers askAllYesNo(String question);

    /** Shows everyone the same menu at once. This is what makes rock-paper-scissors possible. */
    Answers askAllChoice(String question, String... options);
}
