package textgame;

/**
 * Thrown out of an {@code ask} when the match cannot go on, because a player disconnected or
 * went quiet for too long.
 *
 * <p><b>You do not catch this.</b> The framework catches it outside your {@code play} method,
 * tells the other players what happened, and puts the table back in its lobby. It exists so
 * that a blocked {@code ask} has a way to stop waiting — not so that your game has to deal
 * with it.
 */
public class PlayerGoneException extends RuntimeException {

    public PlayerGoneException(String message) {
        super(message);
    }
}
