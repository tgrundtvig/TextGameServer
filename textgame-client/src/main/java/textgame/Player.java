package textgame;

/**
 * One player at the table.
 *
 * <p>{@link #tell} sends text to them; the {@code ask} family sends a question and blocks
 * until they answer. Every {@code ask} except {@link #ask(String)} keeps re-asking until the
 * answer makes sense, so a player typing {@code banana} at an {@code askInt} cannot break
 * your game.
 */
public interface Player {

    /** The name this player typed when they connected. */
    String name();

    /** Sends one-way text to this player. Newlines are kept. */
    void tell(String text);

    /**
     * Asks a question and returns the line exactly as typed.
     *
     * <p>The only {@code ask} that checks nothing — you get whatever they wrote, spaces and
     * all. Use one of the others if you want a number or a yes.
     */
    String ask(String question);

    /** Asks for a whole number, re-asking until they type one. */
    int askInt(String question);

    /** Asks for a whole number from {@code min} to {@code max}, both included. */
    int askInt(String question, int min, int max);

    /** Asks for a number, re-asking until they type one. Decimals allowed. */
    double askDouble(String question);

    /** Asks a yes/no question. Accepts {@code y}, {@code yes}, {@code n}, {@code no}. */
    boolean askYesNo(String question);

    /**
     * Shows a numbered menu and returns the option they picked, as text.
     *
     * <pre>{@code
     * switch (p.askChoice("Your move?", "rock", "paper", "scissors")) {
     *     case "rock" -> ...
     * }
     * }</pre>
     */
    String askChoice(String question, String... options);

    /**
     * The same menu, but returns the position they picked, counting from zero.
     *
     * <pre>{@code
     * Monster target = monsters[p.askChoiceIndex("Attack who?", names)];
     * }</pre>
     */
    int askChoiceIndex(String question, String... options);
}
