package textgame;

/**
 * A game, as a type: what it is called, how many can play, and how to start one match of it.
 *
 * <p>One {@code Game} object describes your game for as long as your program runs, and the
 * lobby lists it. Every time a group of players is ready, the framework calls
 * {@link #newMatch()} to get a fresh {@link Match} for them.
 *
 * <pre>{@code
 * public class NumberDuel implements Game {
 *     public String name()        { return "Number Duel"; }
 *     public String description() { return "Guess my number before anyone else does."; }
 *     public int    minPlayers()  { return 2; }
 *     public int    maxPlayers()  { return 4; }
 *     public Match  newMatch()    { return new NumberDuelMatch(); }
 * }
 * }</pre>
 *
 * <p>The same {@code Game} object serves every table at once, so do not keep anything on it
 * that changes. Fields here should be {@code final} and set once — a word list is perfect, a
 * score counter is not.
 */
public interface Game {

    /** The name shown in the lobby. May contain spaces. */
    String name();

    /** One line, shown under the name in the lobby. */
    String description();

    /** The fewest players a match needs. */
    int minPlayers();

    /** The most players a table will take. */
    int maxPlayers();

    /**
     * A brand new match object, every time.
     *
     * <p>Always {@code return new SomethingMatch();} — never a field, and never the same
     * object twice. Several matches of your game can be running at the same moment.
     */
    Match newMatch();
}
