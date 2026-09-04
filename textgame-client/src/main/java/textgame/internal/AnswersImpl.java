package textgame.internal;

import java.util.List;
import java.util.Map;
import textgame.Answers;
import textgame.Player;

/**
 * The answers from one {@code askAll}.
 *
 * <p>The typed getters only mean something for the ask they belong to. Rather than hide that,
 * this class remembers which ask produced it and says so: {@code getInt} on answers from a
 * plain {@code askAll} explains that {@code askAll} never checked for numbers, instead of
 * throwing a bare {@code NumberFormatException}.
 */
public final class AnswersImpl implements Answers {

    /** Which {@code askAll} produced these answers. */
    public enum Kind {
        RAW("askAll"),
        INT("askAllInt"),
        DOUBLE("askAllDouble"),
        YES_NO("askAllYesNo"),
        CHOICE("askAllChoice");

        private final String askName;

        Kind(String askName) {
            this.askName = askName;
        }

        String askName() {
            return askName;
        }
    }

    private final List<Player> players;
    private final Map<Player, String> text;
    private final Map<Player, Integer> index;
    private final Kind kind;

    AnswersImpl(List<Player> players, Map<Player, String> text, Map<Player, Integer> index,
                Kind kind) {
        this.players = players;
        this.text = text;
        this.index = index;
        this.kind = kind;
    }

    @Override
    public List<Player> players() {
        return players;
    }

    @Override
    public String get(Player p) {
        String answer = text.get(p);
        if (answer == null) {
            throw new IllegalArgumentException(who(p) + " was not asked this question."
                    + " These answers came from " + kind.askName() + " on a room holding: "
                    + names() + ".");
        }
        return answer;
    }

    @Override
    public int getInt(Player p) {
        String answer = get(p);
        if (kind == Kind.INT) {
            return Integer.parseInt(answer);
        }
        if (kind == Kind.RAW) {
            Integer value = Prompts.asInt(answer);
            if (value != null) {
                return value;
            }
            throw new IllegalStateException(wrongKind(p, answer, "a whole number", "askAllInt"));
        }
        throw new IllegalStateException(fromWrongAsk("getInt", "askAllInt"));
    }

    @Override
    public double getDouble(Player p) {
        String answer = get(p);
        if (kind == Kind.DOUBLE || kind == Kind.INT) {
            return Double.parseDouble(answer);
        }
        if (kind == Kind.RAW) {
            Double value = Prompts.asDouble(answer);
            if (value != null) {
                return value;
            }
            throw new IllegalStateException(wrongKind(p, answer, "a number", "askAllDouble"));
        }
        throw new IllegalStateException(fromWrongAsk("getDouble", "askAllDouble"));
    }

    @Override
    public boolean getYesNo(Player p) {
        String answer = get(p);
        if (kind == Kind.YES_NO) {
            return "yes".equals(answer);
        }
        if (kind == Kind.RAW) {
            Boolean value = Prompts.asYesNo(answer);
            if (value != null) {
                return value;
            }
            throw new IllegalStateException(wrongKind(p, answer, "a yes or a no", "askAllYesNo"));
        }
        throw new IllegalStateException(fromWrongAsk("getYesNo", "askAllYesNo"));
    }

    @Override
    public int getIndex(Player p) {
        get(p);
        Integer i = index.get(p);
        if (i == null) {
            throw new IllegalStateException(fromWrongAsk("getIndex", "askAllChoice"));
        }
        return i;
    }

    private String wrongKind(Player p, String answer, String wanted, String rightAsk) {
        return "\"" + answer + "\" (from " + who(p) + ") is not " + wanted + "."
                + " This answer came from askAll, which does not check what players type —"
                + " use " + rightAsk + " if you want " + wanted + ".";
    }

    private String fromWrongAsk(String getter, String rightAsk) {
        return getter + " only works on answers from " + rightAsk + "."
                + " These answers came from " + kind.askName()
                + ", so use get instead — or ask with " + rightAsk + " next time.";
    }

    private static String who(Player p) {
        return p == null ? "null" : p.name();
    }

    private String names() {
        StringBuilder sb = new StringBuilder();
        for (Player p : players) {
            sb.append(sb.isEmpty() ? "" : ", ").append(p.name());
        }
        return sb.isEmpty() ? "nobody" : sb.toString();
    }
}
