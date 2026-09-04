package textgame.example;

import java.util.List;
import textgame.Game;
import textgame.GameServer;
import textgame.Match;

/**
 * Shows the two things a {@code Game} is for: describing the game, and handing a new match
 * whatever it needs to start.
 *
 * <p>The word list is loaded once and never changes, which is exactly what belongs on a
 * {@code Game}. One {@code Impostor} object serves every table at once, so anything here that
 * changed would be shared between matches — and that is the one thing to keep off it.
 */
public class Impostor implements Game {

    private final List<String[]> wordPairs = List.of(
            new String[] {"coffee", "tea"},
            new String[] {"beach", "desert"},
            new String[] {"guitar", "violin"},
            new String[] {"pizza", "lasagne"},
            new String[] {"winter", "autumn"},
            new String[] {"bicycle", "motorbike"});

    public String name()        { return "Impostor"; }
    public String description() { return "Everybody got the same word. Almost everybody."; }
    public int    minPlayers()  { return 3; }
    public int    maxPlayers()  { return 8; }

    public Match newMatch() {
        return new ImpostorMatch(wordPairs);
    }

    public static void main(String[] args) {
        GameServer.connect("localhost", 4000)
                  .host(new Impostor());
    }
}
