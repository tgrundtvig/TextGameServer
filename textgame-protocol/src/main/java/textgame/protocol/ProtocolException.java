package textgame.protocol;

/** Thrown when a line cannot be understood as a {@link Message}. */
public class ProtocolException extends RuntimeException {

    public ProtocolException(String message) {
        super(message);
    }
}
