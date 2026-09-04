package textgame;

/** Thrown when the game server cannot be reached, or the connection to it breaks. */
public class GameServerException extends RuntimeException {

    public GameServerException(String message) {
        super(message);
    }

    public GameServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
