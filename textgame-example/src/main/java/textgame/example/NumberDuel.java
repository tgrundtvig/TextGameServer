package textgame.example;

import textgame.Game;
import textgame.GameServer;
import textgame.Match;

/** The descriptor: what this game is called, and how many can play. */
public class NumberDuel implements Game {

    public String name()        { return "Number Duel"; }
    public String description() { return "Guess my number before anyone else does."; }
    public int    minPlayers()  { return 2; }
    public int    maxPlayers()  { return 4; }
    public Match  newMatch()    { return new NumberDuelMatch(); }

    public static void main(String[] args) {
        GameServer.connect("localhost", 4000)
                  .host(new NumberDuel());
    }
}
