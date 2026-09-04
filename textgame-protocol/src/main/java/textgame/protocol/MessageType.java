package textgame.protocol;

/**
 * Every message the wire protocol knows about.
 *
 * <p>The enum constant name is the wire word, so a line always looks like:
 * <pre>WORD arg1 arg2 free text to the end of the line</pre>
 * The number of fixed arguments is known per message type, which keeps parsing
 * to a single {@code split} with a limit. Fixed arguments never contain spaces;
 * only the trailing text may, and it is escaped so it never contains a newline.
 *
 * <p>Wire words are unique across both directions, so a line can be decoded
 * without knowing who sent it.
 *
 * <p>The server routes lines of text and nothing else. It has no idea what an
 * {@code askInt} is: validation and re-prompting happen in the student's own
 * JVM, so there is at most one question outstanding per player and no need for
 * request ids.
 */
public enum MessageType {

    // ---- game program -> server --------------------------------------------
    /** {@code REGISTER <min> <max> <name...>} — offer this game to the lobby. */
    REGISTER(2, true),
    /** {@code DESCRIBE <text...>} — the one-line description, sent right after REGISTER. */
    DESCRIBE(0, true),
    /** {@code MSG_ONE <tableId> <playerId> <text...>} — text for one player. */
    MSG_ONE(2, true),
    /** {@code MSG_ALL <tableId> <text...>} — text for everyone at the table. */
    MSG_ALL(1, true),
    /** {@code PROMPT_ONE <tableId> <playerId> <text...>} — ask; expect INPUT back. */
    PROMPT_ONE(2, true),
    /** {@code ENDMATCH <tableId> <text...>} — play() returned; text says why. */
    ENDMATCH(1, true),

    // ---- server -> game program --------------------------------------------
    /** {@code REGISTERED <gameId>} — the game is listed in the lobby. */
    REGISTERED(1, false),
    /** {@code TABLE_START <tableId> <seatCount>} — a table is about to play. */
    TABLE_START(2, false),
    /** {@code TABLE_SEAT <tableId> <playerId> <name...>} — one seat, in join order. */
    TABLE_SEAT(2, true),
    /** {@code TABLE_GO <tableId>} — all seats sent; run newMatch().play(room). */
    TABLE_GO(1, false),
    /** {@code INPUT <tableId> <playerId> <text...>} — the line that player typed. */
    INPUT(2, true),
    /** {@code PLAYER_GONE <tableId> <playerId>} — kills the match (DESIGN.md section 7). */
    PLAYER_GONE(2, false),

    // ---- player client -> server -------------------------------------------
    /** {@code NAME <name...>} — the name this player typed when connecting. */
    NAME(0, true),
    /** {@code LIST_GAMES} */
    LIST_GAMES(0, false),
    /** {@code LIST_TABLES <gameId>} */
    LIST_TABLES(1, false),
    /** {@code CREATE_TABLE <gameId> <tableName>} */
    CREATE_TABLE(2, false),
    /** {@code JOIN_TABLE <tableName>} — table names are unique server-wide. */
    JOIN_TABLE(1, false),
    /** {@code READY} */
    READY(0, false),
    /** {@code UNREADY} */
    UNREADY(0, false),
    /** {@code LEAVE} — leave the table, back to the lobby. */
    LEAVE(0, false),
    /** {@code WHO} — who is at this table. */
    WHO(0, false),
    /** {@code CHAT <text...>} — table small talk while waiting. */
    CHAT(0, true),
    /** {@code ANSWER <text...>} — the answer to a PROMPT. */
    ANSWER(0, true),
    /** {@code QUIT} — disconnect politely. */
    QUIT(0, false),

    // ---- server -> player client -------------------------------------------
    /** {@code WELCOME <text...>} */
    WELCOME(0, true),
    /** {@code NAME_OK <name...>} — the name the server accepted. */
    NAME_OK(0, true),
    /** {@code GAME_LIST <count>} — followed by that many GAME_ENTRY/GAME_DESC pairs. */
    GAME_LIST(1, false),
    /** {@code GAME_ENTRY <gameId> <tableCount> <min> <max> <name...>} */
    GAME_ENTRY(4, true),
    /** {@code GAME_DESC <gameId> <text...>} */
    GAME_DESC(1, true),
    /** {@code TABLE_LIST <gameId> <count>} — followed by that many TABLE_ENTRY. */
    TABLE_LIST(2, false),
    /** {@code TABLE_ENTRY <tableName> <players> <max> <state>} — state WAITING/FULL/PLAYING. */
    TABLE_ENTRY(4, false),
    /** {@code JOINED <tableName> <players> <max> <gameName...>} — you are at a table. */
    JOINED(3, true),
    /** {@code LEFT <text...>} — you are back in the lobby; text says why. */
    LEFT(0, true),
    /** {@code NOTICE <text...>} — table housekeeping: joins, ready counts. */
    NOTICE(0, true),
    /** {@code CHATLINE <text...>} — already formatted as "alice: hello". */
    CHATLINE(0, true),
    /** {@code MSG <text...>} — output from the game. */
    MSG(0, true),
    /** {@code PROMPT <text...>} — the game is waiting for this player's input. */
    PROMPT(0, true),
    /** {@code MATCH_START <text...>} */
    MATCH_START(0, true),
    /** {@code MATCH_END <text...>} */
    MATCH_END(0, true),

    // ---- anyone -> server, before anything else ------------------------------
    /**
     * {@code PASSWORD <text...>} — the shared class password, sent as the first message.
     *
     * <p>Only meaningful when the server was started with one. A server without a password
     * ignores it, so a client can always send one it has.
     */
    PASSWORD(0, true),

    // ---- server -> anyone ---------------------------------------------------
    /** {@code ERR <text...>} — the last request was rejected; the text says why. */
    ERR(0, true),
    /** {@code BYE <text...>} — the connection is about to close. */
    BYE(0, true);

    private final int argCount;
    private final boolean hasText;

    MessageType(int argCount, boolean hasText) {
        this.argCount = argCount;
        this.hasText = hasText;
    }

    /** Number of space-free arguments that follow the wire word. */
    public int argCount() {
        return argCount;
    }

    /** Whether a free-text field follows the fixed arguments. */
    public boolean hasText() {
        return hasText;
    }
}
