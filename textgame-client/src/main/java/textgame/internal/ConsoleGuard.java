package textgame.internal;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Watches for the one mistake every student makes: reading {@code System.in} from inside a
 * match.
 *
 * <p>A {@code Scanner} on {@code System.in} compiles perfectly and then blocks the table
 * forever, waiting for a keyboard nobody is sitting at. The read is left alone — this only
 * says, once, what has gone wrong.
 */
final class ConsoleGuard extends FilterInputStream {

    private static volatile boolean installed;
    private volatile boolean warned;

    private ConsoleGuard(InputStream in) {
        super(in);
    }

    static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;
        System.setIn(new ConsoleGuard(System.in));
    }

    private void warn() {
        if (warned) {
            return;
        }
        warned = true;
        System.err.println();
        System.err.println("[textgame] Something is reading System.in — a Scanner, probably.");
        System.err.println("[textgame] Players are not sitting at this keyboard, so nothing"
                + " will ever be typed here and the table will wait forever.");
        System.err.println("[textgame] Ask a player instead: String answer ="
                + " p.ask(\"Your guess?\");");
        System.err.println();
    }

    @Override
    public int read() throws IOException {
        warn();
        return super.read();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        warn();
        return super.read(b, off, len);
    }
}
