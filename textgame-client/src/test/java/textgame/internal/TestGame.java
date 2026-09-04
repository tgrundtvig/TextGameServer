package textgame.internal;

import textgame.Game;
import textgame.Match;

/** A {@link Game} whose match is whatever the test wants it to be. */
final class TestGame implements Game {

    private final Match match;
    private final int min;
    private final int max;

    TestGame(Match match) {
        this(match, 1, 8);
    }

    TestGame(Match match, int min, int max) {
        this.match = match;
        this.min = min;
        this.max = max;
    }

    @Override
    public String name() {
        return "Test Game";
    }

    @Override
    public String description() {
        return "Only ever played by tests.";
    }

    @Override
    public int minPlayers() {
        return min;
    }

    @Override
    public int maxPlayers() {
        return max;
    }

    @Override
    public Match newMatch() {
        return match;
    }
}
