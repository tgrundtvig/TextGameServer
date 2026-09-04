package textgame.internal;

import java.util.Locale;

/**
 * The re-prompting rules, in one place, so that {@code Player.askInt} and
 * {@code Room.askAllInt} say exactly the same thing when an answer will not do.
 *
 * <p>All of this runs in the student's own JVM. The server never sees it.
 */
public final class Prompts {

    private Prompts() {
    }

    /** The answer as a whole number, or {@code null} if it is not one. */
    public static Integer asInt(String answer) {
        try {
            return Integer.valueOf(answer.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The answer as a number, or {@code null} if it is not one. */
    public static Double asDouble(String answer) {
        String trimmed = answer.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            double value = Double.parseDouble(trimmed);
            return Double.isFinite(value) ? Double.valueOf(value) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The answer as a yes or a no, or {@code null} if it is neither.
     *
     * <p>Danish as well as English, because the students writing these games and the players
     * answering them are Danish: a game that asks <em>"Vil du slå om?"</em> and then refuses
     * <em>ja</em> is the framework's fault, not the player's. There is no clash — {@code n}
     * means no in both languages.
     */
    public static Boolean asYesNo(String answer) {
        return switch (answer.trim().toLowerCase(Locale.ROOT)) {
            case "y", "yes", "j", "ja" -> Boolean.TRUE;
            case "n", "no", "nej" -> Boolean.FALSE;
            default -> null;
        };
    }

    public static final String WHOLE_NUMBER = "Please type a whole number.";
    public static final String NUMBER = "Please type a number.";
    public static final String YES_OR_NO = "Please answer yes or no (ja/nej).";

    public static String wholeNumberBetween(int min, int max) {
        return "Please type a whole number between " + min + " and " + max + ".";
    }

    public static String numberBetween(int min, int max) {
        return "Please type a number between " + min + " and " + max + ".";
    }

    /** The question with its options numbered underneath, as the player will see it. */
    public static String menu(String question, String[] options) {
        StringBuilder sb = new StringBuilder(question);
        for (int i = 0; i < options.length; i++) {
            sb.append("\n  ").append(i + 1).append(") ").append(options[i]);
        }
        return sb.toString();
    }

    /** Fails early, on the student's own console, rather than showing players an empty menu. */
    public static void checkOptions(String[] options) {
        if (options == null || options.length == 0) {
            throw new IllegalArgumentException(
                    "A choice needs at least one option to pick from.");
        }
        for (String option : options) {
            if (option == null) {
                throw new IllegalArgumentException("A choice option cannot be null.");
            }
        }
    }

    public static void checkRange(int min, int max) {
        if (min > max) {
            throw new IllegalArgumentException(
                    "The smallest allowed number (" + min + ") is bigger than the largest ("
                            + max + "), so no answer could ever be right.");
        }
    }
}
