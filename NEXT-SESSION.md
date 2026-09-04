# Where this stands, and what to do next

Rewritten 2026-09-04, at the end of the session that built it. Read `DESIGN.md` first — it is
the source of truth. This file is only about state and sequencing.

## Status in one line

**It is built and it works.** A server, the student-facing framework, the console player
client and three worked examples, playing whole matches end to end, with 71 tests passing.

```
mvn install        # builds everything and runs every test
```

---

## What is on disk

```
textgame-protocol/   Message, MessageChannel, MessageType, ProtocolException   DONE
textgame-server/     TextGameServer, Hub, Table, PlayerSession, Endpoint       DONE
textgame-client/     Game, Match, Player, Room, Answers, GameServer            DONE
                     + textgame.internal.* runtime, + textgame.player client
textgame-example/    NumberDuel, RockPaperScissors, Impostor + the tests       DONE
```

Two self-contained jars come out of `mvn install`, each with the protocol classes shaded in
so neither needs a classpath. The Maven artifacts students depend on stay thin.

```
java -jar textgame-server/target/textgame-server.jar 4000
java -jar textgame-client/target/textgame-client.jar localhost 4000
```

A student's game is an ordinary `main`, exactly as in `DESIGN.md` §6 — see
`textgame-example/src/main/java/textgame/example/NumberDuel.java`.

### The protocol

`MessageType` is the whole wire format, with a comment on every constant. Three
conversations, one enum, wire words unique across all of them so a line decodes without
knowing who sent it:

- game program ↔ server: `REGISTER`, `DESCRIBE`, `MSG_ONE`, `MSG_ALL`, `PROMPT_ONE`,
  `ENDMATCH` out; `REGISTERED`, `TABLE_START`, `TABLE_SEAT`, `TABLE_GO`, `INPUT`,
  `PLAYER_GONE` back.
- player ↔ server: `NAME`, `LIST_GAMES`, `LIST_TABLES`, `CREATE_TABLE`, `JOIN_TABLE`,
  `READY`, `UNREADY`, `LEAVE`, `WHO`, `CHAT`, `ANSWER`, `QUIT` out; `NAME_OK`, the
  `GAME_*`/`TABLE_*` listings, `JOINED`, `LEFT`, `NOTICE`, `CHATLINE`, `MSG`, `PROMPT`,
  `MATCH_START`, `MATCH_END` back.
- either direction: `ERR`, `BYE`.

Anything with a variable-length list is a count followed by that many single-item messages,
because a message has fixed arity by design.

---

## The architectural insight, still true and now load-bearing

**All input validation and re-prompting happens in the student's JVM, not on the server.**

`askInt`, `askChoice`, the "please type a number between 1 and 3" loop — all of it is
`textgame.internal.Prompts` and `PlayerImpl`, running on the student's laptop. The server
routes raw lines and does not know what an `askInt` is. There are no request ids anywhere,
because at most one question is outstanding per player, and `Room.askAll` is just N individual
prompts with the match thread blocked until all N answers are in.

---

## Where the interesting code is

| Thing | Where | Why it is worth knowing |
|---|---|---|
| Blocking `ask` | `PlayerImpl.ask` | Sends `PROMPT_ONE`, blocks on a queue. This one method is the whole design. |
| Asking everybody at once | `RoomImpl.runTogether` | One virtual thread per player, joined. Students see a blocking call. |
| A disconnect | `MatchTable.end` | Sets one flag and wakes every waiting `ask`, wherever it is. |
| A student's crash | `HostRuntime.runMatch` | Catches everything, ends that table only, trace to the student's own console. |
| Ending a match once | `Hub.endMatch` | Idempotent: the game, a disconnect and a timeout all funnel here. |
| Reading `System.in` | `ConsoleGuard` | Warns once when a `Scanner` appears, which is `DESIGN.md` §8's predicted mistake. |

---

## Tests, and what each layer is for

| Where | What it proves |
|---|---|
| `MessageTest` | Encode/decode round-trips: spaces, leading spaces, empty text, backslashes, newlines, every message type. |
| `AskTest`, `AskAllTest` | The `ask` family against a scripted transport, with no server at all. Assert on exactly what the player is shown. |
| `MatchEndingTest` | Normal return, disconnect mid-`ask`, disconnect mid-`askAll`, a student's crash, one table dying while another plays on. |
| `AnswersTest` | The typed-getter error messages from `DESIGN.md` §4, word for word. |
| `BadUsageTest` | Mistakes a student can make, and whether the message tells them what to do. |
| `EndToEndTest` | Real server, real sockets, real game programs, whole matches from lobby to end. |
| `LobbyTest`, `ServerRulesTest` | The rules in `DESIGN.md` §5, plus name collisions, idle timeout and the host disconnecting. |

`ScriptedServer` (client tests) and `ScriptedPlayer`/`ScriptedGame` (integration tests) are
the harnesses. Anything new should reuse them rather than grow a third style.

---

## What is genuinely left

Nothing blocks a first lesson. In rough order of value:

1. **Run it with a real class.** Everything below is guesswork until then, particularly the
   two-minute idle timeout — and whether the school network can reach the game port at all
   (`deploy/README.md`).
2. **Spectators** and **reconnecting** — the two open questions in `DESIGN.md` §11.
3. **Fold the template into the course material** — `template/` is written and works, in
   Danish. It wants copying into `DAT-GBG-DA-E26AB` under the week it is taught, which is
   week 40 at the earliest: `implements Game` needs interfaces, and the plan has
   inheritance/polymorphism in weeks 39–41. After the autumn break (43+) is the comfortable
   slot.
4. **A longer written guide**, if the template's README turns out not to be enough. Worth
   waiting for a real lesson before writing more.

### Smaller things noticed while building

- The console client prints `> ` and then chat can land on the same line. Inherent to a
  line-based terminal without ANSI; livable, and the alternative is a curses dependency.
- `Answers.getDouble` accepts answers from `askAllInt` as well as `askAllDouble`, on the
  grounds that every whole number is a number. No test-driven reason to change it, but it is
  the one place the typed getters are not strict.
- The server logs three lines total. If a lesson goes wrong, there is not much to look at.

---

## Design document

The formatted version lives at
<https://claude.ai/code/artifact/5c068bd7-b6cb-404f-a0ec-eac5c3178ae7> and is the same content
as `DESIGN.md`. If the design changes, update **both**.
