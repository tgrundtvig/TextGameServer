package textgame.server;

/** One connected player: their name, where they are sitting, and whether a game is waiting. */
final class PlayerSession {

    private final String id;
    private final Endpoint out;
    private final String name;

    private Table table;
    private boolean ready;
    private boolean prompted;
    private long promptedAt;

    PlayerSession(String id, Endpoint out, String name) {
        this.id = id;
        this.out = out;
        this.name = name;
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

    Table table() {
        return table;
    }

    void sitAt(Table table) {
        this.table = table;
        this.ready = false;
        clearPrompt();
    }

    void stand() {
        this.table = null;
        this.ready = false;
        clearPrompt();
    }

    boolean isReady() {
        return ready;
    }

    void setReady(boolean ready) {
        this.ready = ready;
    }

    boolean isPrompted() {
        return prompted;
    }

    void prompted() {
        prompted = true;
        promptedAt = System.nanoTime();
    }

    void clearPrompt() {
        prompted = false;
    }

    /** How long this player has been keeping the game waiting, in seconds. */
    long waitingSeconds() {
        return prompted ? (System.nanoTime() - promptedAt) / 1_000_000_000L : 0;
    }
}
