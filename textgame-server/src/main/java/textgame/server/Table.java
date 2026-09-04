package textgame.server;

import java.util.ArrayList;
import java.util.List;

/** One named table: who is sitting at it, who is ready, and whether a match is running. */
final class Table {

    private final String id;
    private final String name;
    private final HostedGame game;
    private final List<PlayerSession> seated = new ArrayList<>();

    private boolean playing;

    Table(String id, String name, HostedGame game) {
        this.id = id;
        this.name = name;
        this.game = game;
    }

    String id() {
        return id;
    }

    String name() {
        return name;
    }

    HostedGame game() {
        return game;
    }

    List<PlayerSession> seated() {
        return seated;
    }

    boolean isPlaying() {
        return playing;
    }

    void setPlaying(boolean playing) {
        this.playing = playing;
    }

    boolean isFull() {
        return seated.size() >= game.maxPlayers();
    }

    /** WAITING, FULL or PLAYING — what the lobby shows next to the name. */
    String state() {
        if (playing) {
            return "PLAYING";
        }
        return isFull() ? "FULL" : "WAITING";
    }

    int readyCount() {
        int count = 0;
        for (PlayerSession p : seated) {
            if (p.isReady()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Unanimity, and at least minPlayers. No countdown and no owner pressing go, so no
     * privileged player to explain and nobody stranded when somebody quits.
     */
    boolean everybodyReady() {
        return !playing
                && seated.size() >= game.minPlayers()
                && readyCount() == seated.size();
    }
}
