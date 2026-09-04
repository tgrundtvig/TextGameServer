# TextGameServer

A server for turn-based, text-based multiplayer games, written for **first-semester Java
students** to build games against. Java 21, Maven, no dependencies.

**Read `DESIGN.md` before changing anything.** It is the settled design and the source of
truth. `NEXT-SESSION.md` has current implementation state and the suggested build order.

## The three things that shape every decision here

1. **The audience is first-semester students.** Simplicity of the student-facing API beats
   idiomatic Java, every time. No callbacks, no lambdas, no generics, no threads, no
   exceptions in anything they write. If a change makes the framework nicer but the student
   API harder, it is the wrong change.
2. **The server does networking and logistics only.** No game rules, and it never runs student
   code. Games run on students' own laptops and dial out as TCP clients, exactly like players
   do. All input validation and re-prompting happens client-side, in the student's JVM.
3. **Blocking, not event-driven.** A game is one `play(Room)` method driven by ordinary loops,
   where `p.ask("...")` blocks like a `Scanner`. The framework absorbs the resulting
   complexity — disconnects, timeouts, threads — so students never see it.

## Student-facing API, in full

```java
public interface Game  { String name(); String description();
                         int minPlayers(); int maxPlayers(); Match newMatch(); }
public interface Match { void play(Room room); }
```

Plus `Player` (`name`, `tell`, `ask`, `askInt`, `askDouble`, `askYesNo`, `askChoice`,
`askChoiceIndex`), `Room` (`players`, `tellAll`, `only`, `without`, `askAll*`) and `Answers`.
Full signatures in `DESIGN.md` §3–4.

Naming convention in examples and templates: the `Game` takes the plain name (`NumberDuel`),
the `Match` takes the suffix (`NumberDuelMatch`).

## Modules

| Module | Contents |
|---|---|
| `textgame-protocol` | Wire format. Internal to both sides. |
| `textgame-server` | The hosted server: lobby, named tables, routing. |
| `textgame-client` | `Game`, `Match`, `Player`, `Room`, `Answers`, `GameServer`, and the ready-made console player client. **The only module students depend on or read.** |
| `textgame-example` | Worked example games. |

## Conventions

- Java 21. Virtual threads for connection and match handling.
- No third-party dependencies in `protocol`, `server` or `client`. Tests may use JUnit.
- Error messages that a student can hit must say what to do about it, in plain language —
  never a bare stack trace or a framework-internal type name.
- Keep `textgame-client` small enough to read in one sitting. Resist adding to the student
  API; every method there is a method someone has to teach.
