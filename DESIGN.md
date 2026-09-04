# TextGameServer — Design

A server for turn-based text games, built so that a first-semester student can write a
networked multiplayer game as one method and never touch a socket.

Formatted version (same content, easier to read):
<https://claude.ai/code/artifact/5c068bd7-b6cb-404f-a0ec-eac5c3178ae7>

**Status:** design settled and built. Server, client framework, console player client and
worked examples all work end to end. See `NEXT-SESSION.md`.

---

## 1. The shape of the system

The server does networking and logistics, and nothing else. It knows about connections, a
lobby, named tables, and how to move a line of text from one machine to another. It contains
no game rules and never runs student code.

Everything else is a client:

- A student's **game program** runs on the student's own laptop, launched from their IDE,
  and dials out to the server over TCP.
- **Players** run a ready-made console client that dials out to the same server.

```
 player clients                the server              student programs
 ┌──────────┐              ┌──────────────┐            ┌────────────────┐
 │ alice    │─────────────▶│ lobby        │◀───────────│ NumberDuel     │
 ├──────────┤              │ named tables │            │ (alice's mac)  │
 │ bob      │─────────────▶│ routing      │            ├────────────────┤
 ├──────────┤              │ disconnects  │◀───────────│ ZorkButWorse   │
 │ carol    │─────────────▶│              │            │ (dan's laptop) │
 └──────────┘              │ no game rules│            └────────────────┘
                           └──────────────┘
```

Both sides are TCP clients; the arrows show who dials whom. Consequence: a student's game is
playable only while their program is running. Close the laptop and it disappears from the
lobby. That is the right trade for a classroom — their crash is their own, and one broken
game cannot take down the server or anybody else's.

---

## 2. What a student writes

Two small classes. One *describes* the game; the other *is* one match of it.

```java
public interface Game {           // the game, as a type
    String name();                // shown in the lobby
    String description();         // one line, under the name
    int    minPlayers();
    int    maxPlayers();
    Match  newMatch();            // one of these per table, per match
}

public interface Match {          // one game being played at one table
    void play(Room room);
}
```

The split is deliberate teaching, not an implementation accident. *Number Duel* is a game;
four groups can be playing four matches of it at the same moment. Name, description and
player counts describe the game and are identical in every match; the secret number belongs
to one match and nobody else. Students already hold this distinction for board games — Ludo,
versus tonight's game of Ludo.

**Naming rule:** the `Game` gets the plain name (it is what `host` receives and what appears
in the lobby); the `Match` class takes the `Match` suffix.

```java
// NumberDuel.java — the descriptor
public class NumberDuel implements Game {
    public String name()        { return "Number Duel"; }
    public String description() { return "Guess my number before anyone else does."; }
    public int    minPlayers()  { return 2; }
    public int    maxPlayers()  { return 4; }
    public Match  newMatch()    { return new NumberDuelMatch(); }
}
```

```java
// NumberDuelMatch.java — a complete networked multiplayer game
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
```

The match file contains nothing but game logic. No networking, no threading, no error
handling, no callbacks. Flow of control is visible top to bottom, state lives in ordinary
local variables, and `askInt` blocks the way a `Scanner` does. **That resemblance is the
entire point of the design**: it is the console program they already know how to write, with
the console replaced by somebody else's laptop.

---

## 3. The `Player` interface

Everything a game can do with one player. The `ask*` family is deliberately wide so that
autocomplete after typing `p.ask` shows students what is available.

| Signature | Behaviour |
|---|---|
| `String name()` | The name this player typed when connecting. |
| `void tell(String text)` | One-way text to this player. Newlines preserved. |
| `String ask(String question)` | Blocks; returns the line exactly as typed. The only `ask` that does no validation. |
| `int askInt(String question)` | Re-prompts until a whole number. |
| `int askInt(String question, int min, int max)` | Re-prompts until in range, inclusive. |
| `double askDouble(String question)` | Re-prompts until a number. |
| `boolean askYesNo(String question)` | Accepts `y`/`yes`/`n`/`no`, case-insensitive. |
| `String askChoice(String question, String... options)` | Numbered menu; returns the chosen option's text. |
| `int askChoiceIndex(String question, String... options)` | Same menu, returns zero-based position. |

Both `askChoice` forms exist because they serve different jobs: the string form reads well and
switches cleanly (`switch (p.askChoice(...)) { case "rock" -> ... }`), the index form drops
into the parallel-array pattern (`monsters[p.askChoiceIndex("Attack who?", names)]`).

What the player sees:

```
Your move?
  1) rock
  2) paper
  3) scissors
> 4
Please type a number between 1 and 3.
> 2
```

---

## 4. `Room` and `Answers`

A `Room` is the group of players at one table. Asking one player at a time is what `Player`
is for; `Room` exists for talking to several at once and asking them simultaneously.

| Signature | Behaviour |
|---|---|
| `List<Player> players()` | Everyone at this table, in join order. Never changes during `play`. |
| `void tellAll(String text)` | One-way text to every player. |
| `Room only(Player... some)` / `Room only(List<Player> some)` | A view of a subset; every method works on it. |
| `Room without(Player... some)` / `Room without(List<Player> some)` | The complement. |
| `Answers askAll(String question)` | Asks everyone at once; blocks until all answer. |
| `Answers askAllInt(String question)` / `(String, int min, int max)` | Each player individually re-prompted. |
| `Answers askAllDouble(String question)` | The same, for numbers with decimals. |
| `Answers askAllYesNo(String question)` | Simultaneous yes/no — votes, "play again". |
| `Answers askAllChoice(String question, String... options)` | Simultaneous menu. Makes rock-paper-scissors possible. |

Subsets are handled by `only` returning a `Room` rather than by giving every `askAll*` an
extra player-list parameter — otherwise five methods become ten.

| `Answers` | Behaviour |
|---|---|
| `List<Player> players()` | Who was asked, in room order. |
| `String get(Player p)` | What they answered, as text. Always available. |
| `int getInt(Player p)` | Typed view, matching `askAllInt`. |
| `double getDouble(Player p)` | Typed view, matching `askAllDouble`. |
| `boolean getYesNo(Player p)` | Typed view, matching `askAllYesNo`. |
| `int getIndex(Player p)` | Typed view, matching `askAllChoice`. |

```java
Answers moves = room.askAllChoice("Rock, paper or scissors?",
                                  "rock", "paper", "scissors");
for (Player p : room.players()) {
    room.tellAll(p.name() + " played " + moves.get(p) + ".");
}
```

**Sharp edge, named rather than hidden.** The typed getters only mean something for the
matching ask. `getInt` on answers from a plain `askAll` has to parse unvalidated text and can
fail — when it does it must say *"this answer came from askAll, which does not check for
numbers — use askAllInt"*, not throw a bare `NumberFormatException`.

**Why `players()` never changes.** Because a disconnect ends the whole match rather than
shrinking the room. Real cost — a four-player game dies when one person's wifi hiccups —
bought deliberately, so that no student ever checks whether a player is still there and
`players().get(0)` is always valid.

---

## 5. The lobby

Entirely the server's job. `play` is called only once the lobby has finished, so none of this
appears in any student's code.

```
Connected to class server.
Your name? alice

Games:
  1) Number Duel        2 tables
     Guess my number before anyone else does.
  2) Zork But Worse     1 table
     A dungeon, described badly, by me.
  3) Dungeon Crawl      no tables yet
     Fight monsters. Take their things.

Which game? 1

Number Duel tables:
  1) speedrun        1/4 players
  2) grudge-match    playing
  3) create a new table

Which? 3
Table name [alices-table]? ⏎

—— Number Duel / alices-table (needs 2-4 players) ——
Waiting for players. Type 'ready' when you want to start.
> 
```

Then the table lobby is a chat room until everyone is ready:

```
* bob joined (2/4)
> hi bob, waiting for carol
alice: hi bob, waiting for carol
bob: sure
* carol joined (3/4)
* bob is ready (1/3)
* carol is ready (2/3)
> ready
* alice is ready (3/3)

—— Number Duel starts ——
```

### Rules, stated exactly

- **The match starts when every player at the table is ready**, provided there are at least
  `minPlayers`. Not a countdown, not an owner pressing go — unanimity, which needs no
  privileged player and cannot strand a table when someone quits.
- **Keywords are bare words**: `ready`, `unready`, `leave`, `who`, case-insensitive. Anything
  else is chat.
- **A new arrival resets nobody.** Those already ready stay ready; the newcomer types it too.
- **At `maxPlayers` the table is closed.** It stays listed as full so you can see it exists.
- **Table names are unique server-wide**, letters/digits/`-`/`_`, up to 20 characters, matched
  case-insensitively. Because they are globally unique, typing a table name at any lobby
  prompt jumps straight there — "everyone join `test1`" works with no browsing.
- **The name defaults to the creator's** (`alices-table`); press enter to accept.
- **No owner.** The creator gets no special powers.
- **Full and in-progress tables are not joinable** but stay listed (`4/4`, `playing`).

---

## 6. Tables and lifecycle

One student program serves **many tables at once**. Alice launches *Number Duel* once and four
groups can be playing it simultaneously, each unaware of the others.

That is what `Game` and `Match` are for. The program holds one `Game` for its whole run; the
framework calls `newMatch()` each time a table starts playing, so every match gets a private
object.

```java
public static void main(String[] args) {
    GameServer.connect("class.example.dk", 4000)
              .host(new NumberDuel());
}
```

No reflection anywhere, no class literals, no throwaway instance built to read constants.
Nothing carries over between matches — not across tables, not between two matches at the same
table. Because `Match` objects are never shared, instance fields inside a match are safe, and
the rule students need is the simple *"no `static` state"*.

The `Game` object **is** shared — one instance serves every table — so it is the home for what
is identical in every match and never changes:

```java
public class Hangman implements Game {

    private final List<String> words = loadWords();

    public String name()        { return "Hangman"; }
    public String description() { return "Guess the word, one letter at a time."; }
    public int    minPlayers()  { return 2; }
    public int    maxPlayers()  { return 6; }
    public Match  newMatch()    { return new HangmanMatch(words); }
}
```

Handing arguments to a match is the thing every other construction scheme made awkward. Here
it is simply what `newMatch` is.

**Caution:** because one `Game` serves every table, mutable state on it is shared across
matches on different threads. Keep descriptors read-only — `final` fields set once. A
scoreboard accumulating across matches is the tempting exception and the one thing in this
design that genuinely needs care.

### When `play` returns

The match is over. The table drops back to *its own* lobby with the same players still sitting
at it, everyone un-ready, and a fresh `Match` waiting to be created. A group enjoying itself
types `ready` again rather than being scattered back to the general lobby. The table
disappears, and its name is freed, when the last player leaves.

---

## 7. What the framework absorbs

Promises the framework makes, so a first-semester student never sees these:

| Situation | Behaviour |
|---|---|
| **Bad input** | Every `ask` except the raw one re-prompts until valid. `banana` at `askInt` cannot crash anybody's game. |
| **A player disconnects** | The pending `ask` throws an unchecked exception, caught by the framework **outside** `play`. Others are told *"Game ended: bob disconnected"* and the table returns to its lobby. The student writes no error handling. |
| **A player goes idle** | An `ask` unanswered past a timeout is treated as a disconnect. |
| **Typing out of turn** | *"It's not your turn."* Input is discarded, **not** buffered — buffering silently turns an aside into a move. |
| **The student's game crashes** | Ends that match with a message to its players, prints the stack trace to **the student's own console**, touches nothing else. Other tables in the same program keep running. |
| **Concurrency** | Each table runs on its own thread inside the student's program, but with a private `Match` and no shared state there is nothing to synchronise. The word "thread" never has to come up. |
| **The wire protocol** | One UTF-8 message per line over TCP, framed and escaped by the framework. Internal on both sides. |

---

## 8. Rules for students

The complete list. Short on purpose.

1. **Write two classes.** A `Game` saying what it is called and how many players it takes,
   and a `Match` that plays it. The `Game` is what you pass to `host`.
2. **`newMatch()` returns a brand new object every time.** `return new NumberDuelMatch();` —
   never a field, never the same object twice.
3. **No `static` fields holding game state, and nothing mutable on the `Game`.** Several
   matches run at once inside one program; those are the only two things they share.
4. **Never use `System.out` or `Scanner` for the game.** Output goes through `tell` and
   `tellAll`; input comes from `ask`. `System.out.println` prints on the student's own laptop
   where no player can see it — excellent for debugging, useless for playing.
5. **When `play` returns, the match is over.** To end it early, `return`.

**Expect rule 4 to be the one they break.** Every student arrives with `System.out.println`
and `Scanner` in their fingers, and both compile fine here. A `Scanner` on `System.in` inside
`play` blocks a table forever waiting for a keyboard nobody is sitting at. Worth a framework
warning the first time a game reads `System.in`.

---

## 9. Running it

- **The server** — one instance, hosted by the teacher, running for the whole lesson. Holds
  the lobby and every table. Needs no configuration beyond a port. Students never run it.

  ```
  java -jar textgame-server.jar 4000
  ```

- **A student's game** — an ordinary Java program launched from their IDE, containing the two
  classes and the three-line `main`. Appears in the lobby when it connects, vanishes when
  stopped.

- **The player client** — shipped ready-made. Students do not write one. Works against
  `localhost` for developing alone and against the class server for playing each other's, with
  no code change.

  ```
  java -jar textgame-client.jar localhost 4000
  ```

`mvn install` builds two self-contained jars — `textgame-server/target/textgame-server.jar`
and `textgame-client/target/textgame-client.jar` — with the protocol classes inside, so
neither needs a classpath. The Maven artifacts students depend on stay thin.

Two server settings, both with sensible defaults and neither student-facing:
`-Dtextgame.idleSeconds` (default 120) and `-Dtextgame.maxTablesPerGame` (default 20).

### Module layout (Maven, Java 21, no dependencies)

```
textgame-protocol   // wire format; internal to both sides
textgame-server     // the hosted server; lobby, tables, routing
textgame-client     // Game, Match, Player, Room, Answers, GameServer
                    // + the ready-made console player client
textgame-example    // NumberDuel and friends, as worked examples
```

Students depend on `textgame-client` and nothing else. It is the only module documented for
them, and deliberately small enough to read in one sitting.

---

## 10. Decision log

Alternatives considered seriously and rejected, recorded so the reasoning survives.

| Considered | Outcome |
|---|---|
| **Event-driven game API** (`onPlayerInput`, `onPlayerJoined`) | **Rejected.** Idiomatic and maps cleanly onto the network, but inversion of control is where beginners fall off. Blocking calls let the game be a plain loop with state in local variables. This is the most expensive decision in the design and the one that makes the rest worth building. |
| **One interface, four methods** (`TextGame` with `name`/`min`/`max`/`play`) | **Superseded** by `Game` + `Match`. Metadata on the object that plays a match is a category error, and it forced a throwaway instance built only to read three constants. The pitch got *stronger*: "your game is one method" beats "four methods", and the split teaches type-versus-instance for free. |
| `.host(MyGame.class)` | **Rejected.** Needs reflection, forbids constructor arguments outright, puts an opaque token in front of students who have never seen a class literal. The constructor-argument limitation is what killed it: a Hangman loading a word list has nowhere to put it but a `static`, which rule 3 forbids. |
| `.host(new MyGame())` reflecting on `getClass()` | **Rejected, emphatically.** Most familiar syntax, most dishonest: reads as though the object you passed will play, and it will not. `new Hangman(words)` would compile, run, and silently drop the words. |
| **A fifth method `newMatch()` on the game itself** | **Rejected.** Needs no new syntax but taxes every game class, and fails horribly on copy-paste: duplicate a game file, forget that line, and the new game shows up in the lobby playing the old one. |
| `.host(MyGame::new)` | **Rejected.** Right answer for anyone who knows method references, which is not this audience; the compiler error mentions `Supplier<Match>`. |
| **A `Choice` result object** (`index()`, `text()`, `is()`) | **Rejected.** Avoids the `==` trap, but you cannot `switch` on it, so any menu with more than two branches unwraps it anyway — and menus are what `askChoice` is for. Introduces a type whose only job is to postpone a decision, plus a nonstandard `is()` idiom that transfers nowhere. |
| **Only `askChoice` returning `String`** | **Rejected.** The `==` hazard is unusually nasty: comparing against an interned literal *works*, so the bug passes every classroom test and surfaces only once a student builds an option by concatenation. |
| **Room names typed from memory** | **Superseded** by a browsable lobby plus named tables — discovery *and* a name you can say out loud. |
| **Owner presses start** | **Rejected** for unanimous ready. No privileged player, no concept to explain, nobody stranded when the owner quits. |
| **Buffering out-of-turn input** | **Rejected.** Feels responsive, produces baffling bugs. |
| **One table per program** | **Rejected.** A popular game would block everyone behind one match. |
| **Letting the room shrink when somebody drops** | **Rejected**, again, once it was implemented: the disconnect ends the match and every waiting `ask` throws at once. Cheaper to build than the alternative, and it is the reason `players().get(0)` needs no null check. |
| **The server rendering the lobby as text** | **Rejected.** A dumb terminal is less code on the client, but the numbered menus and the re-prompting belong wherever validation already lives — in the client — and it keeps the server free of anything that formats. The server sends entries; the client draws menus. |
| **Buffering a player's answer when nobody asked** | **Rejected** in code as well as on paper: an `ANSWER` arriving unprompted is refused with *"It's not your turn."* and dropped. |
| **Uploading student games to the server** | **Never viable.** Sandboxing untrusted code is a project in itself, and a crash would be everyone's problem. |

---

## 11. Still open

Two of the five have been decided while building; the rest still do not block anything.

- **Spectators.** Joining an in-progress table read-only. Attractive for demoing to the class;
  needs a rule for what a spectator sees, since much text is private to one player.
- **Reconnecting.** A dropped player currently ends the match. Letting them rejoin the same
  seat within a grace period would soften the harshest consequence of the fixed-`players()`
  rule.
- **Idle timeout duration.** Two minutes is implemented and configurable, but the number is
  still a guess: it wants a real class to tune it.

### Decided while building

- **Player names** are unique server-wide, matched case-insensitively, and are 1–16 characters
  of letters, digits, `-` or `_`. A name that is taken or malformed is refused with a plain
  message and the client simply asks again. Uniqueness is what makes `* alice is ready (1/3)`
  mean one person, and what lets a name be said out loud across a classroom.
- **An unanswered question is a disconnect** after 120 seconds (`-Dtextgame.idleSeconds`). The
  match ends for everybody, exactly as a real disconnect would — but the idle player keeps
  their seat and stays connected, because being slow is not the same as walking out.
- **Twenty tables per game program** (`-Dtextgame.maxTablesPerGame`), so a runaway loop cannot
  spawn a thousand. Server-side, not student-facing; the twenty-first `create` is refused with
  a message naming the limit.
