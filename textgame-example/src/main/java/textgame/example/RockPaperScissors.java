package textgame.example;

import textgame.Game;
import textgame.GameServer;
import textgame.Match;

/** Everybody throws at the same time — which is what {@code room.askAllChoice} is for. */
public class RockPaperScissors implements Game {

    public String name()        { return "Rock Paper Scissors"; }
    public String description() { return "Three rounds. Everybody throws at once."; }
    public int    minPlayers()  { return 2; }
    public int    maxPlayers()  { return 6; }
    public Match  newMatch()    { return new RockPaperScissorsMatch(); }

    public static void main(String[] args) {
        GameServer.connect("localhost", 4000)
                  .host(new RockPaperScissors());
    }
}
