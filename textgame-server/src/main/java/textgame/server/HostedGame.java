package textgame.server;

import java.util.ArrayList;
import java.util.List;

/** One game program that has registered itself, and the tables playing it. */
final class HostedGame {

    private final String id;
    private final Endpoint out;
    private final String name;
    private final int minPlayers;
    private final int maxPlayers;
    private final List<Table> tables = new ArrayList<>();

    private String description = "";

    HostedGame(String id, Endpoint out, String name, int minPlayers, int maxPlayers) {
        this.id = id;
        this.out = out;
        this.name = name;
        this.minPlayers = minPlayers;
        this.maxPlayers = maxPlayers;
    }

    String id() {
        return id;
    }

    Endpoint out() {
        return out;
    }

    String name() {
        return name;
    }

    String description() {
        return description;
    }

    void describe(String description) {
        this.description = description;
    }

    int minPlayers() {
        return minPlayers;
    }

    int maxPlayers() {
        return maxPlayers;
    }

    List<Table> tables() {
        return tables;
    }
}
