package textgame.example;

import textgame.Match;
import textgame.Player;
import textgame.Room;

/**
 * A complete networked multiplayer game.
 *
 * <p>Nothing in here is about networking: it is the console program you already know how to
 * write, with the console replaced by somebody else's laptop.
 */
public class NumberDuelMatch implements Match {

    public void play(Room room) {
        int secret = 1 + (int) (Math.random() * 100);
        room.tellAll("I am thinking of a number between 1 and 100.");

        while (true) {
            for (Player p : room.players()) {
                int guess = p.askInt("Your guess?", 1, 100);

                if (guess == secret) {
                    room.tellAll(p.name() + " got it! It was " + secret + ".");
                    return;
                }
                room.tellAll(p.name() + " guessed " + guess + " — too "
                             + (guess < secret ? "low" : "high") + ".");
            }
        }
    }
}
