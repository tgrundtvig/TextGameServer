package textgame.example;

import java.util.ArrayList;
import java.util.List;
import textgame.Answers;
import textgame.Match;
import textgame.Player;
import textgame.Room;

/**
 * Shows {@code room.only} and {@code room.without}: one player is told something the others
 * must not see, and then the others are asked something the impostor is not.
 */
public class ImpostorMatch implements Match {

    private final List<String[]> wordPairs;

    public ImpostorMatch(List<String[]> wordPairs) {
        this.wordPairs = wordPairs;
    }

    public void play(Room room) {
        String[] pair = wordPairs.get((int) (Math.random() * wordPairs.size()));
        Player impostor = room.players().get((int) (Math.random() * room.players().size()));

        room.without(impostor).tellAll("Your word is: " + pair[0]);
        room.only(impostor).tellAll("Your word is: " + pair[1]
                + "   (everybody else has a different one — do not let them find out)");

        room.tellAll("");
        room.tellAll("Describe your word in one sentence, without saying it.");
        for (Player p : room.players()) {
            String clue = p.ask("Your clue?");
            room.tellAll(p.name() + " says: " + clue);
        }

        String[] names = new String[room.players().size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = room.players().get(i).name();
        }

        room.tellAll("");
        Answers votes = room.askAllChoice("Who is the impostor?", names);

        List<Player> accused = new ArrayList<>();
        for (Player p : room.players()) {
            room.tellAll(p.name() + " voted for " + votes.get(p) + ".");
            accused.add(room.players().get(votes.getIndex(p)));
        }

        Player mostVoted = mostVotedIn(room, accused);
        room.tellAll("");
        room.tellAll("The table picked " + mostVoted.name() + ".");
        room.tellAll("The impostor was " + impostor.name() + ", with the word "
                + pair[1] + ".");
        room.tellAll(mostVoted == impostor
                ? "The table wins."
                : "The impostor wins.");
    }

    private Player mostVotedIn(Room room, List<Player> accused) {
        Player best = room.players().get(0);
        int bestCount = -1;
        for (Player p : room.players()) {
            int count = 0;
            for (Player vote : accused) {
                if (vote == p) {
                    count++;
                }
            }
            if (count > bestCount) {
                bestCount = count;
                best = p;
            }
        }
        return best;
    }
}
