package textgame.example;

import java.util.Arrays;
import textgame.Match;
import textgame.Player;
import textgame.Room;

/**
 * Shows a game whose state changes from round to round, kept in plain arrays that run
 * parallel to {@code room.players()}: player number {@code i} owns {@code dice[i]}.
 *
 * <p>It also shows a rule that no {@code ask} can check for you. {@code askInt} makes sure a
 * bid is a number in range, but only the game knows whether it beats the previous bid — so
 * the game asks again until it does, the same way {@code askInt} would.
 *
 * <p>The rules: everybody rolls in secret. A bid says "at least this many dice on the whole
 * table show this value". Each bid must beat the last, or you call the last bidder a liar
 * and all cups come up. Whoever was wrong loses a die. Ones are jokers and count as every
 * value; when bidding, 1 is the highest value and only real ones count. Last player with
 * dice wins.
 */
public class LiarsDiceMatch implements Match {

    private static final int DICE_EACH = 5;

    private Room room;
    private int[] diceLeft;   // how many dice player i still has; 0 means out of the game
    private int[][] dice;     // what player i rolled this round, diceLeft[i] of them

    // The bid on the table: "at least bidCount dice show bidValue". bidCount 0: no bid yet.
    private int bidCount;
    private int bidValue;
    private int bidder;       // the player who made it

    public void play(Room room) {
        this.room = room;
        diceLeft = new int[room.players().size()];
        dice = new int[diceLeft.length][];
        for (int i = 0; i < diceLeft.length; i++) {
            diceLeft[i] = DICE_EACH;
        }
        explainRules();

        int starter = 0;
        for (int round = 1; playersLeft() > 1; round++) {
            room.tellAll("");
            room.tellAll("—— Round " + round + ": " + totalDice() + " dice on the table ——");
            rollAll();
            int loser = playRound(starter);
            starter = diceLeft[loser] > 0 ? loser : nextWithDice(loser);
        }

        room.tellAll("");
        room.tellAll(name(starter) + " is the last one with dice and wins!");
    }

    private void explainRules() {
        room.tellAll("Liar's Dice. Everybody has " + DICE_EACH + " dice under a cup.");
        room.tellAll("A bid says: at least THIS MANY dice on the whole table show THIS VALUE.");
        room.tellAll("Ones are jokers and count as every value. Bidding on ones, only real"
                + " ones count, and 1 is the highest value.");
        room.tellAll("Raise the bid, or call the last bidder a liar. Whoever was wrong loses"
                + " a die.");
    }

    private void rollAll() {
        for (int i = 0; i < dice.length; i++) {
            dice[i] = new int[diceLeft[i]];
            for (int d = 0; d < dice[i].length; d++) {
                dice[i][d] = 1 + (int) (Math.random() * 6);
            }
            Arrays.sort(dice[i]);
            if (diceLeft[i] > 0) {
                room.players().get(i).tell("Your dice: " + show(dice[i]));
            }
        }
    }

    /** One round: bids go round the table until somebody calls liar. Returns who lost a die. */
    private int playRound(int starter) {
        bidCount = 0;
        bidValue = 0;
        int turn = starter;
        while (true) {
            Player p = room.players().get(turn);
            if (bidCount > 0) {
                String move = p.askChoice("Your move?", "raise the bid", "call liar");
                if (move.equals("call liar")) {
                    return showdown(turn);
                }
            }
            askForBid(p, turn);
            turn = nextWithDice(turn);
        }
    }

    private void askForBid(Player p, int turn) {
        while (true) {
            int count = p.askInt("How many dice?", 1, totalDice());
            int value = p.askInt("Showing which value? (2-6, or 1 which is highest)", 1, 6);
            if (beatsCurrentBid(count, value)) {
                bidCount = count;
                bidValue = value;
                bidder = turn;
                room.tellAll(p.name() + " bids " + describe(count, value) + ".");
                return;
            }
            p.tell("That does not beat " + describe(bidCount, bidValue)
                    + ". Bid more dice, or the same number of a higher value.");
        }
    }

    private boolean beatsCurrentBid(int count, int value) {
        return count > bidCount || (count == bidCount && rank(value) > rank(bidValue));
    }

    /** 2 is the lowest value and 1 the highest, so 1 is ranked above 6. */
    private int rank(int value) {
        return value == 1 ? 7 : value;
    }

    /** Cups up: everybody's dice are shown, counted, and somebody loses a die. */
    private int showdown(int challenger) {
        room.tellAll(name(challenger) + " calls " + name(bidder) + " a liar! Cups up:");
        for (int i = 0; i < dice.length; i++) {
            if (diceLeft[i] > 0) {
                room.tellAll("  " + name(i) + ": " + show(dice[i]));
            }
        }
        int found = countShowing(bidValue);
        room.tellAll("The bid was " + describe(bidCount, bidValue) + ", and there are "
                + found + (bidValue == 1 ? "." : " (ones included)."));

        int loser = found >= bidCount ? challenger : bidder;
        diceLeft[loser]--;
        if (diceLeft[loser] == 0) {
            room.tellAll(name(loser) + " loses the last die and is out.");
        } else {
            room.tellAll(name(loser) + " loses a die and has " + diceLeft[loser] + " left.");
        }
        return loser;
    }

    private int countShowing(int value) {
        int found = 0;
        for (int i = 0; i < dice.length; i++) {
            for (int d = 0; d < dice[i].length; d++) {
                if (dice[i][d] == value || dice[i][d] == 1) {
                    found++;
                }
            }
        }
        return found;
    }

    private int totalDice() {
        int total = 0;
        for (int i = 0; i < diceLeft.length; i++) {
            total += diceLeft[i];
        }
        return total;
    }

    private int playersLeft() {
        int left = 0;
        for (int i = 0; i < diceLeft.length; i++) {
            if (diceLeft[i] > 0) {
                left++;
            }
        }
        return left;
    }

    /** The next player round the table who still has dice. */
    private int nextWithDice(int from) {
        int i = from;
        do {
            i = (i + 1) % diceLeft.length;
        } while (diceLeft[i] == 0);
        return i;
    }

    private String name(int i) {
        return room.players().get(i).name();
    }

    private String describe(int count, int value) {
        return count + (count == 1 ? " die" : " dice") + " showing " + value;
    }

    private String show(int[] rolled) {
        String text = "";
        for (int d = 0; d < rolled.length; d++) {
            text += rolled[d] + " ";
        }
        return text.trim();
    }
}
