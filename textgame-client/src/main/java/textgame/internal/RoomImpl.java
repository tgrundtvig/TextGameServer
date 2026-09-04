package textgame.internal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import textgame.Answers;
import textgame.Player;
import textgame.PlayerGoneException;
import textgame.Room;
import textgame.protocol.Message;
import textgame.protocol.MessageType;

/**
 * A room, or a view of part of one.
 *
 * <p>{@code only} and {@code without} return another {@code RoomImpl} over a smaller member
 * list, sharing the same table — which is why every method works on a subset without any of
 * the {@code askAll} methods needing an extra player-list parameter.
 *
 * <p>The {@code askAll} family asks everyone at once by running each player's own re-prompting
 * loop on its own virtual thread and waiting for all of them. Students never see the threads:
 * the call simply blocks until the last answer is in.
 */
public final class RoomImpl implements Room {

    private final MatchTable table;
    private final List<PlayerImpl> members;
    private final List<Player> membersView;

    RoomImpl(MatchTable table, List<PlayerImpl> members) {
        this.table = table;
        this.members = members;
        this.membersView = List.copyOf(members);
    }

    @Override
    public List<Player> players() {
        return membersView;
    }

    @Override
    public void tellAll(String text) {
        table.checkAlive();
        if (members.size() == table.seats().size()) {
            table.send(Message.withText(MessageType.MSG_ALL, table.id(), text));
            return;
        }
        for (PlayerImpl p : members) {
            p.tell(text);
        }
    }

    @Override
    public Room only(Player... some) {
        return only(Arrays.asList(some));
    }

    @Override
    public Room only(List<Player> some) {
        List<PlayerImpl> kept = new ArrayList<>();
        for (Player p : some) {
            PlayerImpl member = member(p, "only");
            if (!kept.contains(member)) {
                kept.add(member);
            }
        }
        return new RoomImpl(table, kept);
    }

    @Override
    public Room without(Player... some) {
        return without(Arrays.asList(some));
    }

    @Override
    public Room without(List<Player> some) {
        List<PlayerImpl> excluded = new ArrayList<>();
        for (Player p : some) {
            excluded.add(member(p, "without"));
        }
        List<PlayerImpl> kept = new ArrayList<>(members);
        kept.removeAll(excluded);
        return new RoomImpl(table, kept);
    }

    private PlayerImpl member(Player p, String method) {
        if (p == null) {
            throw new IllegalArgumentException(
                    "room." + method + " was given null instead of a player.");
        }
        if (!members.contains(p)) {
            throw new IllegalArgumentException(
                    p.name() + " is not in this room, so room." + method
                            + " cannot use them. This room holds: " + names(members) + ".");
        }
        return (PlayerImpl) p;
    }

    private static String names(Collection<PlayerImpl> players) {
        if (players.isEmpty()) {
            return "nobody";
        }
        StringBuilder sb = new StringBuilder();
        for (PlayerImpl p : players) {
            sb.append(sb.isEmpty() ? "" : ", ").append(p.name());
        }
        return sb.toString();
    }

    // ---- asking everybody at once -------------------------------------------

    @Override
    public Answers askAll(String question) {
        return collect(AnswersImpl.Kind.RAW, (p, text, index) -> text.put(p, p.ask(question)));
    }

    @Override
    public Answers askAllInt(String question) {
        return collect(AnswersImpl.Kind.INT,
                (p, text, index) -> text.put(p, String.valueOf(p.askInt(question))));
    }

    @Override
    public Answers askAllInt(String question, int min, int max) {
        Prompts.checkRange(min, max);
        return collect(AnswersImpl.Kind.INT,
                (p, text, index) -> text.put(p, String.valueOf(p.askInt(question, min, max))));
    }

    @Override
    public Answers askAllDouble(String question) {
        return collect(AnswersImpl.Kind.DOUBLE,
                (p, text, index) -> text.put(p, String.valueOf(p.askDouble(question))));
    }

    @Override
    public Answers askAllYesNo(String question) {
        return collect(AnswersImpl.Kind.YES_NO,
                (p, text, index) -> text.put(p, p.askYesNo(question) ? "yes" : "no"));
    }

    @Override
    public Answers askAllChoice(String question, String... options) {
        Prompts.checkOptions(options);
        return collect(AnswersImpl.Kind.CHOICE, (p, text, index) -> {
            int i = p.askChoiceIndex(question, options);
            text.put(p, options[i]);
            index.put(p, i);
        });
    }

    /** What one player is asked, and where their answer goes. */
    private interface AskOne {
        void ask(PlayerImpl player, Map<Player, String> text, Map<Player, Integer> index);
    }

    private Answers collect(AnswersImpl.Kind kind, AskOne ask) {
        table.checkAlive();
        Map<Player, String> text = new LinkedHashMap<>();
        Map<Player, Integer> index = new LinkedHashMap<>();
        Map<Player, String> textSafe = java.util.Collections.synchronizedMap(text);
        Map<Player, Integer> indexSafe = java.util.Collections.synchronizedMap(index);

        runTogether(p -> ask.ask(p, textSafe, indexSafe));

        // Rebuild in room order: the answers arrive in whatever order players type.
        Map<Player, String> ordered = new LinkedHashMap<>();
        Map<Player, Integer> orderedIndex = new LinkedHashMap<>();
        for (PlayerImpl p : members) {
            ordered.put(p, text.get(p));
            if (index.containsKey(p)) {
                orderedIndex.put(p, index.get(p));
            }
        }
        return new AnswersImpl(membersView, ordered, orderedIndex, kind);
    }

    /** Runs {@code task} for every member at once and waits for all of them. */
    private void runTogether(Consumer<PlayerImpl> task) {
        if (members.isEmpty()) {
            return;
        }
        if (members.size() == 1) {
            task.accept(members.get(0));
            return;
        }
        AtomicReference<RuntimeException> failure = new AtomicReference<>();
        List<Thread> threads = new ArrayList<>(members.size());
        for (PlayerImpl p : members) {
            threads.add(Thread.ofVirtual().name("ask-" + p.name()).start(() -> {
                try {
                    task.accept(p);
                } catch (RuntimeException e) {
                    failure.compareAndSet(null, e);
                    // Nobody else's answer can matter now, so stop them waiting too.
                    table.end(e instanceof PlayerGoneException ? e.getMessage() : table.endReason());
                } catch (Error e) {
                    failure.compareAndSet(null, new IllegalStateException(e));
                    table.end(table.endReason());
                }
            }));
        }
        boolean interrupted = false;
        for (Thread t : threads) {
            while (true) {
                try {
                    t.join();
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        RuntimeException problem = failure.get();
        if (problem != null) {
            throw problem;
        }
    }
}
