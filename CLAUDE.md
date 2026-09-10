# Claude Code — TextGameServer

A server for turn-based, text-based multiplayer games, built so a
first-semester Java student writes a networked game as one method and
never touches a socket. Java 21, Maven, no dependencies.

**Status (verified 2026-09-10):** built; 0.5.0 published to
`maven.tobiasgrundtvig.dk` and live on prodesk as
`game.tobiasgrundtvig.dk:4000`; 113 tests green; not yet used by a real
class.

## Where things are

- `DESIGN.md` — the settled design and decision log. Source of truth;
  read before changing anything.
- `NEXT-SESSION.md` — state, the interesting code, what is left;
  `WEB-PLAYER.md` — the browser-player plan.
- `deploy/README.md` — prodesk, the Maven repository, the server log.
- `knowledge/_index.md` — orientation; `STARTUP.md` — the handoff.
- Code: `textgame-client/src/main/java/textgame/` is the student API,
  `internal/PlayerImpl.ask` the whole design; `textgame-server/.../Hub`
  the lobby; `textgame-protocol/.../MessageType` the wire format;
  `template/` what students copy.

## Rules of this codebase

1. Student-API simplicity beats idiomatic Java: no callbacks, lambdas,
   generics, threads or exceptions in what students write — they are
   first-semester.
2. The server does networking and logistics only — no game rules, never
   student code; games run on students' laptops and dial in as TCP
   clients.
3. Blocking, not event-driven: `play(Room)` is ordinary loops and
   `p.ask` blocks like a `Scanner`; the framework absorbs disconnects,
   timeouts and threads.
4. Validation and re-prompting happen in the student's JVM; the server
   routes raw lines.
5. No third-party dependencies in `protocol`, `server` or `client`;
   tests may use JUnit.
6. An error a student can hit says what to do, in plain language —
   never a bare stack trace or an internal type name.
7. Keep `textgame-client` readable in one sitting; every method there
   is one somebody has to teach.
8. A `Game` takes the plain name (`NumberDuel`), its `Match` the suffix
   (`NumberDuelMatch`), so examples and template read alike.
9. A published version never changes bytes — bump the number; students
   pin it.
10. A design change updates `DESIGN.md` and its formatted artifact
    together.

## Session start and end

Run `/wakeup` at session start and `/hibernate` at the end. They
delegate to `/do wakeup` / `/do hibernate` against the foundation at
`$KNOWLEDGE_FOUNDATION_PATH`. The hooks in `.claude/settings.json`
inject the session pack (`kt wakeup`) at `SessionStart` and capture
the session transcript.

If `/wakeup` says the env var is unset, add this to your shell
profile and restart your shell:

```bash
export KNOWLEDGE_FOUNDATION_PATH=~/Development/GitHub/knowledge-foundation
```

## Profile

`.profile.yml`: `archetype: run`; java-library, github-vcs, coolify-deployment; software-engineering, teaching.
