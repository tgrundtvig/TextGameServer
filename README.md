# TextGameServer

A server for turn-based, text-based multiplayer games, written so that a
**first-semester Java student** can build a networked multiplayer game as one
method and never touch a socket.

```java
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

That is a complete multiplayer game. `askInt` blocks the way a `Scanner` does —
the console has simply been replaced by somebody else's laptop. No callbacks,
no threads, no error handling, no networking.

## How it fits together

The server does networking and logistics only. It has no game rules and never
runs student code. A student's game runs **on the student's own machine**,
launched from their IDE, and dials out to the server as a TCP client — exactly
as players do.

```
 player clients                the server              student programs
 ┌──────────┐              ┌──────────────┐            ┌────────────────┐
 │ alice    │─────────────▶│ lobby        │◀───────────│ NumberDuel     │
 ├──────────┤              │ named tables │            │ (alice's mac)  │
 │ bob      │─────────────▶│ routing      │            ├────────────────┤
 │ carol    │─────────────▶│ no game rules│◀───────────│ ZorkButWorse   │
 └──────────┘              └──────────────┘            └────────────────┘
```

## Modules

| Module | Contents |
|---|---|
| `textgame-protocol` | Wire format. Internal to both sides. |
| `textgame-server` | The hosted server: lobby, named tables, routing. |
| `textgame-client` | `Game`, `Match`, `Player`, `Room`, `Answers`, `GameServer`, and the ready-made console player client. **The only module students depend on.** |
| `textgame-example` | Worked example games. |

Java 21, Maven, no third-party dependencies.

## Building and running

```bash
mvn install                                            # builds everything, runs the tests
java -jar textgame-server/target/textgame-server.jar 4000        # the teacher, once
java -jar textgame-client/target/textgame-client.jar localhost 4000   # everybody who plays
```

Both jars are self-contained. A student's game is an ordinary program:

```java
public static void main(String[] args) {
    GameServer.connect("localhost", 4000)
              .host(new NumberDuel());
}
```

To try the worked examples without writing that `main` yourself, there is a
launcher — one self-contained jar, any example, any server:

```bash
java -jar textgame-example/target/textgame-example.jar NumberDuel
java -jar textgame-example/target/textgame-example.jar RockPaperScissors myserver 4000
```

`NumberDuel` (2–4, turn by turn), `RockPaperScissors` (2–6, simultaneous moves
via `askAllChoice`) and `Impostor` (3–8, secrets told to one player via
`only`/`without`).

### Playing a game end to end

Three terminals: one for the game program, one per player.

```bash
# 1 — the game program (this is what a student runs from their IDE)
java -jar textgame-example/target/textgame-example.jar NumberDuel localhost 4000

# 2 and 3 — two players
java -jar textgame-client/target/textgame-client.jar localhost 4000
```

In each player terminal: type a name, pick the game, then **create a table** in
one and **type that table's name** in the other — table names are unique
server-wide, so typing one at any lobby prompt jumps straight there. Both type
`ready`, and the match starts when everybody has.

## For students

Students do not build this repo. They depend on `textgame-client`, which is
published to a Maven repository, and get both the framework and the ready-made
player client from that one dependency:

```xml
<repositories>
    <repository>
        <id>tobiasgrundtvig</id>
        <url>https://maven.tobiasgrundtvig.dk</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>textgame</groupId>
        <artifactId>textgame-client</artifactId>
        <version>0.3.0</version>
    </dependency>
</dependencies>
```

`textgame-protocol` comes transitively; nothing else is needed, and there is no
jar to download or classpath to set. `template/` is a ready-to-copy Maven
project with the two classes written and a working game in them.

Publishing a new version: `deploy/publish-maven.sh`.

## Documentation

- **`DESIGN.md`** — the settled design and the reasoning behind it, including
  the decision log. Read this before changing anything.
- **`NEXT-SESSION.md`** — implementation state, where the interesting code is,
  and what is left.
- **`deploy/`** — running it as an always-on server, and publishing the
  student-facing artifact.
- **`template/`** — the project students copy. Danish, because the course is.
