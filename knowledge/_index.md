---
type: project-node
summary: Orientation for TextGameServer — a TCP server plus a student-facing Java framework for turn-based text games; where the design record, code, deploy notes and course template live, and what this tree holds.
verified: 2026-09-10
related:
  - path: foundation:/knowledge/meta/consumers/TextGameServer
    rationale: The registry entry records this repo's archetype tension (students link against the client jar, the teacher runs the server) — described there, not resolved here.
---

# TextGameServer

A server for turn-based, text-based multiplayer games, built so that a
first-semester Java student writes a networked multiplayer game as one
`play(Room)` method and never touches a socket. The server holds a lobby
and named tables and routes lines; it has no game rules and never runs
student code. A student's game runs on the student's own laptop and
dials in as a TCP client, exactly as players do. Java 21, Maven, no
third-party dependencies (JUnit in tests only).

**Status (verified 2026-09-10):** built. Version 0.5.0 — after a
four-reviewer pass on 2026-09-10 — is published to
`https://maven.tobiasgrundtvig.dk` and the server has run on prodesk as
`game.tobiasgrundtvig.dk:4000` since 2026-09-04, reachable from the
school wifi (verified 2026-09-10). Not yet used by a real class.

## Where things are

| Path | What it settles |
|---|---|
| `DESIGN.md` | The design and its decision log (§10), plus what is still open (§11). Source of truth; read before changing anything. A formatted twin lives at <https://claude.ai/code/artifact/5c068bd7-b6cb-404f-a0ec-eac5c3178ae7> — a design change updates both. |
| `NEXT-SESSION.md` | Implementation state, the wire format in one list, a table of where the interesting code is, what each test layer proves, and what is left. |
| `WEB-PLAYER.md` | The plan for a browser player (in Danish): a bridge module `textgame-web`, hand-written WebSocket, deployment facts verified 2026-09-04. A convenience, since port 4000 works from school. |
| `deploy/README.md` | Classroom laptop vs. prodesk; the Coolify application's ids, port mapping and cleared FQDN; the router forward; the Maven repository on Caddy; the two `rsync`/Caddyfile traps; how to read the server log. |
| `deploy/publish-maven.sh` | Publishes `textgame-client`, `textgame-protocol` and the parent pom; refuses to overwrite a published version unless `--force`. |
| `template/` | The Maven project students copy, in Danish: `MyGame`, `MyGameMatch`, `StartPlayer`, and a README with the five rules. |
| `Dockerfile` | Builds and runs the tests (a red build never becomes a running server), then runs the server jar as a non-root user with a plain-TCP healthcheck. |
| `kodeord.txt` | The class password a client sends first; students keep theirs next to their `pom.xml`, out of git. |
| `knowledge/` | This tree. `STARTUP.md` at the root is the session handoff. |

## The shape

```
textgame-protocol   wire format (MessageType is the whole of it); internal to both sides
textgame-server     TextGameServer, Hub, Table, PlayerSession, Endpoint — lobby, tables, routing
textgame-client     Game, Match, Player, Room, Answers, GameServer — the only module students see
                    + textgame.internal.* (PlayerImpl, RoomImpl, MatchTable, HostRuntime, Prompts, …)
                    + textgame.player.PlayerClient, the ready-made console player
textgame-example    NumberDuel, RockPaperScissors, Impostor, LiarsDice, and the end-to-end tests
```

The student-facing API, in full:

```java
public interface Game  { String name(); String description();
                         int minPlayers(); int maxPlayers(); Match newMatch(); }
public interface Match { void play(Room room); }
```

plus `Player` (`name`, `tell`, `ask`, `askInt`, `askDouble`, `askYesNo`,
`askChoice`, `askChoiceIndex`), `Room` (`players`, `tellAll`, `only`,
`without`, `askAll*`) and `Answers`; signatures in `DESIGN.md` §3–4. A
student's `main` is `GameServer.connect(host, port).host(new NumberDuel())`.
The one method that is the whole design is `PlayerImpl.ask`: it sends
`PROMPT_ONE` and blocks on a queue, on the student's laptop. All input
validation and re-prompting happens there, never on the server.

`mvn install` produces two self-contained jars,
`textgame-server/target/textgame-server.jar` and
`textgame-client/target/textgame-client.jar`, with the protocol classes
shaded in; the published Maven artifacts stay thin. Server settings, none
student-facing: `-Dtextgame.idleSeconds` (120; 0 switches it off),
`-Dtextgame.maxTablesPerGame` (20), and the password as
`TEXTGAME_PASSWORD` or `-Dtextgame.password`. In the container the port
is `TEXTGAME_PORT`.

## What this tree holds

One node — this orientation, written 2026-09-10 when the repo was
brought up to the foundation's tree conventions. The repo's settled
knowledge already lives in the documents above, and it stays there
rather than being copied in. The deploy traps (`rsync -a` copying a
`700` staging mode onto the web root, so every artifact answers `403`;
the Caddyfile serving six live domains) and the runtime behaviour
(keepalive catches an idle vanished machine in about a minute, the
retransmission limit catches a mid-transfer one in about fifteen) are in
`deploy/README.md`; give one its own child here, with `symptoms:`, when a
session needs to find it by its error text.
