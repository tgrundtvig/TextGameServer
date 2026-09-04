package textgame.internal;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import textgame.Player;
import textgame.PlayerGoneException;
import textgame.protocol.Message;
import textgame.protocol.MessageType;

/**
 * One seat at one table.
 *
 * <p>{@code ask} sends the question and then blocks on {@link #inbox} until the answer comes
 * back through the runtime's read loop. That blocking is the whole trick: it is what lets a
 * student write a network game as an ordinary top-to-bottom method.
 */
public final class PlayerImpl implements Player {

    /** Put in the inbox to wake an {@code ask} that will never be answered. */
    static final Object GONE = new Object();

    private final MatchTable table;
    private final String id;
    private final String name;
    private final BlockingQueue<Object> inbox = new LinkedBlockingQueue<>();

    PlayerImpl(MatchTable table, String id, String name) {
        this.table = table;
        this.id = id;
        this.name = name;
    }

    String id() {
        return id;
    }

    /** Called by the read loop when this player's answer arrives. */
    void deliver(String answer) {
        inbox.add(answer);
    }

    /** Called when the match is over for everybody, to unblock a waiting ask. */
    void wake() {
        inbox.add(GONE);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void tell(String text) {
        table.checkAlive();
        table.send(Message.withText(MessageType.MSG_ONE, table.id(), id, text));
    }

    @Override
    public String ask(String question) {
        table.checkAlive();
        table.send(Message.withText(MessageType.PROMPT_ONE, table.id(), id, question));
        Object answer;
        try {
            answer = inbox.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PlayerGoneException("the match was stopped");
        }
        if (answer == GONE) {
            throw new PlayerGoneException(table.endReason());
        }
        return (String) answer;
    }

    @Override
    public int askInt(String question) {
        String prompt = question;
        while (true) {
            Integer value = Prompts.asInt(ask(prompt));
            if (value != null) {
                return value;
            }
            prompt = Prompts.WHOLE_NUMBER;
        }
    }

    @Override
    public int askInt(String question, int min, int max) {
        Prompts.checkRange(min, max);
        String prompt = question;
        while (true) {
            Integer value = Prompts.asInt(ask(prompt));
            if (value != null && value >= min && value <= max) {
                return value;
            }
            prompt = Prompts.wholeNumberBetween(min, max);
        }
    }

    @Override
    public double askDouble(String question) {
        String prompt = question;
        while (true) {
            Double value = Prompts.asDouble(ask(prompt));
            if (value != null) {
                return value;
            }
            prompt = Prompts.NUMBER;
        }
    }

    @Override
    public boolean askYesNo(String question) {
        String prompt = question;
        while (true) {
            Boolean value = Prompts.asYesNo(ask(prompt));
            if (value != null) {
                return value;
            }
            prompt = Prompts.YES_OR_NO;
        }
    }

    @Override
    public String askChoice(String question, String... options) {
        return options[askChoiceIndex(question, options)];
    }

    @Override
    public int askChoiceIndex(String question, String... options) {
        Prompts.checkOptions(options);
        String prompt = Prompts.menu(question, options);
        while (true) {
            Integer value = Prompts.asInt(ask(prompt));
            if (value != null && value >= 1 && value <= options.length) {
                return value - 1;
            }
            prompt = Prompts.numberBetween(1, options.length);
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
