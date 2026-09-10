package textgame.example;

import textgame.Game;
import textgame.GameServer;
import textgame.Match;

/** The descriptor for Liar's Dice. Every table gets its own {@code LiarsDiceMatch}. */
public class LiarsDice implements Game {

    public String name()        { return "Liar's Dice"; }
    public String description() { return "Bid on every die at the table. Doubt, and somebody loses one."; }
    public int    minPlayers()  { return 2; }
    public int    maxPlayers()  { return 6; }
    public Match  newMatch()    { return new LiarsDiceMatch(); }

    public static void main(String[] args) {
        GameServer.connect("localhost", 4000)
                  .host(new LiarsDice());
    }
}
