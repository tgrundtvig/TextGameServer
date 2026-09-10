# Running TextGameServer as an always-on server

Two ways to run this, and they answer different needs. Pick both.

## 1. In the classroom — the teacher's laptop

```bash
TEXTGAME_PASSWORD=<the class word> java -jar textgame-server/target/textgame-server.jar 4000
```

Students point their game and their player client at the teacher's machine on
the classroom network. The password is optional here — nobody outside the room
can reach the port — but students' `kodeord.txt` is sent whether or not the
server wants it, so the same file works against both servers. No infrastructure, no DNS, no firewall to argue with,
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
| Env | `TEXTGAME_PORT=4000`, `TEXTGAME_PASSWORD=<the class word>` |

**Reachable from the internet** as `game.tobiasgrundtvig.dk:4000` — the
router forward in step 4 is in place, and the school-wifi test further down
went through it. Also on the LAN (`192.168.1.100:4000`) and the tailnet
(`prodesk-ubuntu:4000`).

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

**3. Env.** `TEXTGAME_PORT=4000` and `TEXTGAME_PASSWORD` — the word students
put in `kodeord.txt`. Without it the server is open to the whole internet, and
it says so in its first log line. Optional: `JAVA_TOOL_OPTIONS` for
`-Dtextgame.idleSeconds` (0 switches the idle timeout off) and
`-Dtextgame.maxTablesPerGame`. Changing the password drops nobody, but
everybody reconnecting afterwards needs the new word.

**4. Router.** Forward WAN `4000` → `192.168.1.100:4000`, TCP. This is the one
step that is not in Coolify — 80 and 443 are already forwarded, nothing else
is. The router is at `192.168.1.1`; prodesk's LAN address is static enough in
practice, but pin a DHCP reservation for it while you are in there, because the
forward breaks silently if that address ever moves.

Nothing else on prodesk is in the way: `ufw` is inactive, iptables `INPUT`
policy is `ACCEPT`, the container is `restart=unless-stopped` and Docker is
enabled at boot, so the server comes back on its own after a reboot.

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

## Can the school reach port 4000? Yes — verified 2026-09-10

Tested from the school wifi (SSID `EK`, gateway `10.136.128.1`): `nc -vz` to
`game.tobiasgrundtvig.dk 4000` succeeded, and a raw line sent to it came back
with the server's own password refusal — so the whole path works, not just the
handshake. `443` was checked alongside as a control. The web player in
`WEB-PLAYER.md` is therefore a convenience, not a necessity.

The worry was that many school and campus networks allow outbound `443` and
`80` and block everything else, and there is no clean way around that here:
`443` on this host belongs to Caddy, serving six public domains, and one public
IP cannot lend it out. That worry did not materialise for this network. If a
different network is ever in play, the check is one command from a machine on
its wifi:

```bash
nc -vz game.tobiasgrundtvig.dk 4000        # or: telnet game.tobiasgrundtvig.dk 4000
```

Better still, play through it, which tests the whole path rather than the
handshake:

```bash
java -jar textgame-client.jar game.tobiasgrundtvig.dk 4000
```

You should get `Connected to class server.` and a lobby. If it hangs or is
refused, the school blocks the port.

If 4000 is blocked somewhere else, the options, cheapest first:

1. Use the classroom laptop server (option 1 above) during lessons and accept
   that prodesk is for home use only. This costs nothing and is already how the
   design expects a lesson to run.
2. Try another port — some networks allow high ports selectively.
3. Build Caddy with `xcaddy` and the `layer4` plugin, terminate TLS on `443`
   and route by SNI to the game port, and wrap the client socket in
   `SSLSocketFactory`. This works and is genuinely how you would beat a
   firewall — but it replaces the Caddy binary that serves six live public
   domains, and it puts TLS into the code students read. Not worth it unless
   option 1 is also unavailable.

## The Maven repository

Separate from the game server, and the thing students actually need first: a
static Maven repository so their poms resolve `textgame-client`.

| | |
|---|---|
| URL | `https://maven.tobiasgrundtvig.dk` |
| On disk | `/var/www/maven` on prodesk, `tog:tog`, dirs 755 / files 644 |
| Served by | Caddy, a `file_server browse` block beside the six static sites |
| Published | `textgame-parent`, `textgame-protocol`, `textgame-client`; every version since 0.1.0 |
| Publish with | `deploy/publish-maven.sh` (`--force` to overwrite a version) |

**It is on 443**, so unlike the game port it works from any network that allows
ordinary web traffic — which is most of the point of self-hosting it rather
than handing out jars.

Verified 2026-09-04 from an empty local repository over the public URL: a
student project declaring only `textgame-client` compiled, pulling
`textgame-protocol` transitively.

**Published versions do not change.** Students pin a version; if the same
number came back with different bytes, a project that built in September would
fail in November for no visible reason. The publish script refuses to overwrite
an existing version unless forced.

**Two traps worth knowing**, both already handled in the script:

- `rsync -a` copies the *staging directory's* mode onto the web root. The
  staging dir is a `mktemp -d`, which is `700`, so the whole repo silently
  becomes unreadable by Caddy — every artifact answers `403` while looking
  perfectly fine over SSH. The script uses `rsync -rlt --chmod=D755,F644`.
- The Caddyfile serves six live public domains. Change it with a backup and
  `caddy validate` first, then `systemctl reload` — never restart.

## Operating it

```bash
ssh prodesk-ubuntu
docker ps --filter name=<app-id-substring>
docker logs -f --since 10m <container>
```

The server prints one line per game program that registers or leaves, and one
per player joining or leaving, each with why the connection ended. If a
student's game vanishes from the lobby, or stays there when it should not, that
is the log to read:

```
[server] Blackjack is now hosted from /85.x.x.x:51234
[server] mani joined from /85.x.x.x:51236
[server] mani left: connection lost (Connection timed out)
[server] Blackjack is no longer hosted: connection lost (Connection reset)
```

`said goodbye` is a clean quit, `closed the connection` a socket closed without
one, and `connection lost (...)` is the kernel's own reason. `Connection timed
out` means TCP keepalive gave up on a machine that vanished without closing —
a reboot, a lid closed, a wifi drop — after about a minute of silence.
`Connection reset` means the machine was back and refused the probe.

**A vanished machine that was mid-transfer** — the server was sending to it
when it died — is not caught by keepalive, which only watches idle
connections. TCP's retransmission limit catches it instead, which with the
kernel default (`net.ipv4.tcp_retries2 = 15`) takes roughly fifteen minutes.
Lowering that to 8 brings it under two minutes; it is a per-container sysctl
(`--sysctl net.ipv4.tcp_retries2=8`), which Coolify may or may not pass through
in `custom_docker_run_options` — unverified, and not needed until it bites.

**Redeploys drop every connected player**, and a match in progress dies with
it — Coolify stops the old container before starting the new one. Deploy
between lessons, not during one.

**Image pruning.** Something on prodesk prunes unreferenced Docker images
(`foundation:/knowledge/machines/prodesk` — this repo's images are built by
Coolify and referenced by a running container, so they survive; a hand-built
`docker build` image with nothing running would not).
