# Running TextGameServer as an always-on server

Two ways to run this, and they answer different needs. Pick both.

## 1. In the classroom — the teacher's laptop

```bash
java -jar textgame-server/target/textgame-server.jar 4000
```

Students point their game and their player client at the teacher's machine on
the classroom network. No infrastructure, no DNS, no firewall to argue with,
and it works when the internet does not. **This is the primary way to run a
lesson**, and the design assumes it.

## 2. Between lessons — prodesk

So students can keep playing each other's games from home. This is the setup
documented below.

---

## What makes this deployment unusual

**It is not HTTP.** The whole estate's edge — Caddy on `192.168.1.100:80/443`,
Coolify's Traefik on `8088`/`8443` — routes by `Host:` header, and none of it
can carry a raw TCP stream. Stock Caddy has no `layer4` module (verified: the
prodesk build is stock v2.11.4). So this service **cannot sit behind the normal
`*.apps.tobiasgrundtvig.dk` path**; it needs a TCP port of its own, published
straight to the host.

Coolify can do exactly that, and only that:

- `ports_mappings: 4000:4000` publishes the container port on the host's
  `0.0.0.0` — which is what a public game server wants.
- The IP-scoped form (`127.0.0.1:4000:4000`) is **rejected**, and
  `custom_docker_run_options: -p ...` is **silently dropped**. See
  `foundation:/knowledge/tools/infra/coolify/host-ip-port-bind/`.
- Because nothing HTTP is served, the auto-assigned `*.sslip.io` FQDN must be
  **cleared** — otherwise Traefik publicly routes an app that has no HTTP.

## What is deployed

Live on prodesk since 2026-09-04, and verified: a game program on a laptop
registered in the lobby, two console clients joined a named table and played a
full match of Rock Paper Scissors through it.

| | |
|---|---|
| Coolify project | `TextGameServer` (`sg4tyuww53uuquexs0b3zrka`) |
| Application | `textgame-server` (`d9apckci8wbgmuceotglgmhh`) |
| Source | public GitHub, `tgrundtvig/TextGameServer`, branch `main` |
| Build pack | `dockerfile` |
| Port | `ports_exposes 4000`, `ports_mappings 4000:4000` → host `0.0.0.0:4000` |
| FQDN | **none** — deliberately cleared; nothing HTTP is served |
| Env | `TEXTGAME_PORT=4000` |

**Reachable today** on the LAN (`192.168.1.100:4000`) and the tailnet
(`prodesk-ubuntu:4000`). **Not reachable from the internet** — verified
2026-09-04: `212.60.124.173:443` is open, `:4000` is closed, because the
router forwards 80 and 443 and nothing else. Step 4 below is what changes
that, and it is the one step that is not in Coolify.

## Setting it up

**1. Coolify application.** Source: the public-GitHub source on
`tgrundtvig/TextGameServer`, branch `main`, build pack `dockerfile`. The repo's
`Dockerfile` builds and runs the tests; a red build never becomes a running
server. (Auto-deploy on push needs a webhook added on the GitHub side, since
this uses the public source rather than a GitHub App — not wired yet; deploy
with `POST /api/v1/deploy?uuid=<app-uuid>` meanwhile.)

**2. Port.** `ports_mappings: 4000:4000`. Leave the FQDN empty
(`PATCH /api/v1/applications/{uuid}` with `{"domains": ""}`, then confirm
`fqdn` reads back `null`).

**3. Env.** `TEXTGAME_PORT=4000`. Optional: `JAVA_TOOL_OPTIONS` for
`-Dtextgame.idleSeconds` and `-Dtextgame.maxTablesPerGame`.

**4. Router.** Forward WAN `4000` → `192.168.1.100:4000`. This is the one step
that is not in Coolify — 80 and 443 are already forwarded, nothing else is.

**5. DNS.** `game.tobiasgrundtvig.dk` already resolves to `212.60.124.173`
(the domain is wildcarded), so no record needs adding. Students use:

```bash
java -jar textgame-client.jar game.tobiasgrundtvig.dk 4000
```

and in their own game's `main`:

```java
GameServer.connect("game.tobiasgrundtvig.dk", 4000).host(new NumberDuel());
```

**6. Verify from outside.** From a machine that is *not* on the LAN or the
tailnet:

```bash
nc -vz game.tobiasgrundtvig.dk 4000
```

## The one real risk: school outbound firewalls

Many school and campus networks allow outbound `443` and `80` and block
everything else. **Port 4000 may simply not be reachable from the classroom.**
There is no clean way around it here: `443` on this host belongs to Caddy,
serving six public domains, and one public IP cannot lend it out.

So: **test from the actual school network before relying on it.** If 4000 is
blocked, the options, cheapest first:

1. Use the classroom laptop server (option 1 above) during lessons and accept
   that prodesk is for home use only.
2. Try another port — some networks allow high ports selectively.
3. Build Caddy with `xcaddy` and the `layer4` plugin, terminate TLS on `443`
   and route by SNI to the game port, and wrap the client socket in
   `SSLSocketFactory`. This works and is genuinely how you would beat a
   firewall — but it replaces the Caddy binary that serves six live public
   domains, and it puts TLS into the code students read. Not worth it unless
   option 1 is also unavailable.

## Operating it

```bash
ssh prodesk-ubuntu
docker ps --filter name=<app-id-substring>
docker logs -f --since 10m <container>
```

The server prints one line per game program that registers and one per
disconnect. If a student's game vanishes from the lobby, that is the log to
read.

**Redeploys drop every connected player**, and a match in progress dies with
it — Coolify stops the old container before starting the new one. Deploy
between lessons, not during one.

**Image pruning.** Something on prodesk prunes unreferenced Docker images
(`foundation:/knowledge/machines/prodesk` — this repo's images are built by
Coolify and referenced by a running container, so they survive; a hand-built
`docker build` image with nothing running would not).
