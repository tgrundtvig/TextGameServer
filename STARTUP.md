# STARTUP — TextGameServer

Written 2026-09-10 by the session that brought this repo up to the
foundation's tree conventions (branch `foundation-conventions-2026-09-10`).
This is the handoff `/wakeup` reads; rewrite it at every `/hibernate`.
There were no session logs to draw on — `sessions/` does not exist yet;
the logger creates it from the next session on.

## Current state

- **Built and released.** `0.5.0` (commit `4185782`, 2026-09-10) is the
  outcome of a four-reviewer pass after the first student report; what
  changed is in that commit and in `DESIGN.md` §10. Published to
  `https://maven.tobiasgrundtvig.dk`; `template/` pins it.
- **Deployed.** Coolify app `textgame-server` on prodesk since
  2026-09-04, `0.0.0.0:4000`, FQDN deliberately empty, reachable as
  `game.tobiasgrundtvig.dk:4000` — from the school wifi too, verified
  2026-09-10 (`deploy/README.md`). Auto-deploy on push is not wired;
  deploy with `POST /api/v1/deploy?uuid=<app-uuid>`.
- **Tests.** 113 pass, 0 fail, 0 skipped (run 2026-09-10 on prodesk).
- **Ceremony.** PR #1 (2026-09-10) added `.claude/`, the hooks and the
  `.gitignore` line. This session added `knowledge/_index.md` (the tree
  now exists, one node), rewrote `CLAUDE.md` to the foundation's
  six-part shape, and wrote this file. `.profile.yml` untouched:
  `archetype: run`; java-library, github-vcs, coolify-deployment;
  software-engineering, teaching.
- **Not yet run with a real class.** Everything downstream of that —
  the two-minute idle timeout above all — is still a guess
  (`NEXT-SESSION.md`, "What is genuinely left").

## Facts not worth rediscovering

- **`mvn install` fails on stock Ubuntu Maven.** prodesk has Maven
  3.8.7 (Java 25); its default `maven-compiler-plugin` 3.1 ignores
  `maven.compiler.release` and fails with `Source option 5 is no longer
  supported`. `mvn test -Dmaven.compiler.source=21
  -Dmaven.compiler.target=21` builds and passes; the `Dockerfile`'s
  `maven:3.9-eclipse-temurin-21` image is unaffected, so Coolify builds
  are fine. Pinning the compiler plugin in `pom.xml` would remove the
  trap; not done here (out of this session's scope).
- The registry entry in the foundation
  (`foundation:/knowledge/meta/consumers/TextGameServer`) records an
  archetype tension — students *link* against `textgame-client`, the
  teacher *runs* `textgame-server` — flagged, not resolved. Do not
  resolve it from this repo; the decision is the foundation's.
- `deploy/README.md` points at `foundation:/knowledge/machines/prodesk`,
  which does not exist in the foundation (checked 2026-09-10). The
  image-pruning fact it cites has no foundation node to point to.
- `DESIGN.md` has a formatted twin at
  <https://claude.ai/code/artifact/5c068bd7-b6cb-404f-a0ec-eac5c3178ae7>;
  a design change updates both.
- `NEXT-SESSION.md` says 113 tests; the foundation's registry entry
  says 71 (the count when it was onboarded, 2026-09-04). 113 is current.
- `WEB-PLAYER.md` is in Danish, as is `template/`; code and the other
  docs are English (commit `485e0f8`).

## Scope for the next session

In `NEXT-SESSION.md`'s order of value: run it with a real class before
tuning anything; then the browser player per `WEB-PLAYER.md` (slices
0–6 are a day's work; no infrastructure change needed, it rides the
`*.apps` wildcard); folding `template/` into the course material waits
for week 43+. The two design questions still open are spectators and
reconnecting (`DESIGN.md` §11).
