package textgame.example;

import java.util.HashMap;
import java.util.Map;
import textgame.Answers;
import textgame.Match;
import textgame.Player;
import textgame.Room;

/**
 * Shows {@code askAllChoice}: everybody is asked at the same moment, and the call does not
 * come back until the last person has picked. Nobody can wait to see what anybody else threw.
 */
public class RockPaperScissorsMatch implements Match {

    private final Map<Player, Integer> score = new HashMap<>();

    public void play(Room room) {
        for (Player p : room.players()) {
            score.put(p, 0);
        }

        for (int round = 1; round <= 3; round++) {
            room.tellAll("");
            room.tellAll("—— Round " + round + " ——");

            Answers thrown = room.askAllChoice("Rock, paper or scissors?",
                                               "rock", "paper", "scissors");

            for (Player p : room.players()) {
                room.tellAll(p.name() + " played " + thrown.get(p) + ".");
            }
            for (Player p : room.players()) {
                for (Player other : room.players()) {
                    if (p != other && beats(thrown.getIndex(p), thrown.getIndex(other))) {
                        score.put(p, score.get(p) + 1);
                    }
                }
            }
            showScores(room);
        }

        room.tellAll("");
        room.tellAll(winner(room) + " wins!");
    }

    /** rock 0 beats scissors 2, paper 1 beats rock 0, scissors 2 beats paper 1. */
    private boolean beats(int mine, int theirs) {
        return (mine + 2) % 3 == theirs;
    }

    private void showScores(Room room) {
        for (Player p : room.players()) {
            room.tellAll("  " + p.name() + ": " + score.get(p));
        }
    }

    private String winner(Room room) {
        int best = -1;
        String names = "";
        for (Player p : room.players()) {
            if (score.get(p) > best) {
                best = score.get(p);
                names = p.name();
            } else if (score.get(p) == best) {
                names = names + " and " + p.name();
            }
        }
        return names;
    }
}
