package textgame;

/**
 * One game being played at one table.
 *
 * <p>Write {@link #play(Room)} as an ordinary program: loops, {@code if}s, local variables.
 * {@code p.ask("...")} blocks and hands you back a line of text, exactly the way a
 * {@code Scanner} does — except the keyboard is on somebody else's laptop.
 *
 * <pre>{@code
 * public class NumberDuelMatch implements Match {
 *     public void play(Room room) {
 *         int secret = 1 + (int) (Math.random() * 100);
 *         room.tellAll("I am thinking of a number between 1 and 100.");
 *         while (true) {
 *             for (Player p : room.players()) {
 *                 int guess = p.askInt("Your guess?", 1, 100);
 *                 if (guess == secret) {
 *                     room.tellAll(p.name() + " got it! It was " + secret + ".");
 *                     return;
 *                 }
 *             }
 *         }
 *     }
 * }
 * }</pre>
 *
 * <p>Each match gets its own object, so ordinary instance fields are safe. When
 * {@code play} returns, the match is over; to end it early, {@code return}.
 */
public interface Match {

    /** Plays one whole match, from start to finish. */
    void play(Room room);
}
