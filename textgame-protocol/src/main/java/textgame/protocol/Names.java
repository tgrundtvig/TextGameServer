package textgame.protocol;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What a player name and a table name are allowed to look like — in the protocol module,
 * because both the server and the player client have to agree, and a rule that lives on only
 * one side is a rule the other side can break.
 *
 * <p>Both are carried as space-free protocol arguments, so a name with a space in it cannot
 * be sent at all. That makes checking before sending part of the wire format, not politeness.
 *
 * <p>The messages here describe what is wrong with <em>this</em> name rather than reciting the
 * rule, and suggest a name that would work. Someone who typed "Alices Game" wants to be told
 * about the space and offered "Alices-Game".
 */
public final class Names {

    /** Short enough to fit in "* alice is ready (1/3)" and to say out loud. */
    private static final int PLAYER_MAX = 16;
    /** Long enough to be descriptive, short enough to read in a lobby listing. */
    private static final int TABLE_MAX = 20;

    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9_-]+");

    private Names() {
    }

    public static boolean isPlayerName(String name) {
        return fits(name, PLAYER_MAX);
    }

    public static boolean isTableName(String name) {
        return fits(name, TABLE_MAX);
    }

    private static boolean fits(String name, int max) {
        return name != null && !name.isEmpty() && name.length() <= max
                && ALLOWED.matcher(name).matches();
    }

    /** Why this name will not do, and what to type instead. */
    public static String whyNotPlayerName(String name) {
        return why(name, PLAYER_MAX, "name", "alice", "alice_2");
    }

    /** Why this table name will not do, and what to type instead. */
    public static String whyNotTableName(String name) {
        return why(name, TABLE_MAX, "table name", "speedrun", "grudge-match");
    }

    private static String why(String name, int max, String what, String example1,
                              String example2) {
        if (name == null || name.isEmpty()) {
            return "A " + what + " cannot be empty. Something like " + example1 + " or "
                    + example2 + ".";
        }
        String suggestion = suggest(name, max);
        String tail = suggestion.isEmpty() ? "" : " Try " + suggestion + ".";

        if (containsWhitespace(name)) {
            return "A " + what + " cannot contain spaces." + tail;
        }
        if (name.length() > max) {
            return "That " + what + " is " + name.length() + " characters long;"
                    + " the most is " + max + "." + tail;
        }
        return "A " + what + " can only use letters, digits, - and _." + tail;
    }

    /**
     * The nearest name that would have been accepted, or empty if nothing usable is left.
     * Spaces become dashes, because that is what somebody typing two words meant.
     */
    public static String suggest(String name, int max) {
        if (name == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean lastWasDash = false;
        for (char c : name.strip().toCharArray()) {
            if (Character.isWhitespace(c)) {
                if (sb.length() > 0 && !lastWasDash) {
                    sb.append('-');
                    lastWasDash = true;
                }
            } else if (isAllowed(c)) {
                sb.append(c);
                lastWasDash = (c == '-');
            }
            // Anything else is dropped: an apostrophe or an emoji has no obvious repair.
        }
        while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '-') {
            sb.setLength(sb.length() - 1);
        }
        if (sb.length() > max) {
            sb.setLength(max);
            while (sb.length() > 0 && sb.charAt(sb.length() - 1) == '-') {
                sb.setLength(sb.length() - 1);
            }
        }
        return sb.toString();
    }

    /** A suggestion of a player name, for the client to offer. */
    public static String suggestPlayerName(String name) {
        return suggest(name, PLAYER_MAX);
    }

    /** A suggestion of a table name, for the client to offer. */
    public static String suggestTableName(String name) {
        return suggest(name, TABLE_MAX);
    }

    /** A default table name built from a player's name: alice becomes alice-table. */
    public static String defaultTableNameFor(String playerName) {
        String base = suggest(playerName == null ? "" : playerName.toLowerCase(Locale.ROOT),
                TABLE_MAX);
        String name = (base.isEmpty() ? "table" : base) + "-table";
        return name.length() > TABLE_MAX ? name.substring(0, TABLE_MAX) : name;
    }

    private static boolean isAllowed(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9') || c == '-' || c == '_';
    }

    private static boolean containsWhitespace(String name) {
        return name.chars().anyMatch(Character::isWhitespace);
    }
}
