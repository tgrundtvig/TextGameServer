# Web-spiller — plan

En browser-indgang, så man kan spille uden Java, uden Maven og uden IntelliJ.
Skrevet 2026-09-04 til at bygge efter i næste session. `DESIGN.md` er stadig
kilden til, hvordan systemet hænger sammen; det her ændrer intet i den.

## Hvorfor

**Firewallen er den egentlige grund.** Spilserveren lytter på port 4000, og om
en skole lukker den ud, ved vi ikke før testen i næste uge. En WebSocket kører
på 443 gennem den Caddy, der allerede står der — så virker web-spilleren, også
hvis 4000 er lukket.

Derudover: ingen installation. En telefon i frikvarteret, en fremvisning på
projektoren, en studerende der vil se hvad det er, før hun bygger noget.

**Hvad den ikke løser:** man skal stadig skrive Java for at *lave* et spil. Det
her er en dør ind til at *spille* dem.

## Formen

```
  browser ──WSS/443──▶ Caddy ──▶ Traefik ──▶ textgame-web ──TCP/4000──▶ textgame-server
                                              (broen)                    (uændret)
```

Broen er **endnu en klient** til den eksisterende server, præcis som
konsol-spilleren er det. Serveren ændres ikke med én linje. Det er den samme
grænse, resten af designet holder: serveren sender tekst, klienterne forstår den.

## Beslutninger, der allerede er taget

**Et nyt modul, `textgame-web`.** Ikke inde i `textgame-server`, som forbliver en
ren TCP-router. Broen er en klient, og klienter bor for sig selv.

**Ingen afhængigheder, håndskrevet WebSocket.** Java 21 har ingen WebSocket-
*server*. At hente Jetty ind for at få én ville koste mere læsbarhed, end det
sparer kode: det, vi skal bruge, er ét HTTP-upgrade-håndtryk og tekst-frames.
Resten af repoet håndskriver allerede sin egen wire-protokol; det her er mindre
end den. **Verificeret:** JDK'en *har* en WebSocket-**klient**
(`java.net.http.WebSocket`), så broen kan testes ende-til-ende uden en eneste
afhængighed.

**Broen er dum.** Den oversætter linje ⇄ frame og ellers ingenting. Den kender
ikke lobbyen, ikke borde og ikke spilregler — samme aftale som serveren har med
sig selv. Al menu-logik ligger i browseren, ligesom den ligger i konsol-klienten.

**Broen serverer også siden.** Ét `java -jar textgame-web.jar 8080` giver både
HTML'en og WebSocket'en, så hele web-spilleren kan køres lokalt uden Caddy og
uden Docker. Én ting at deploye, én ting at fejlfinde.

**Broen forbinder kun til den konfigurerede server.** Adressen kommer fra
miljøet, aldrig fra klienten. En bro, der forbinder til en vært, browseren
udpeger, er en åben proxy — det er den fejl, der er værd at nævne, før den
bliver skrevet.

## Spørgsmål, der skal afgøres i sessionen

**Menuer som knapper?** Et `PROMPT` kommer som færdig tekst — `"Your move?\n
1) rock\n  2) paper"`. Browseren *kunne* parse den tilbage til knapper, men at
regne baglæns fra formateret tekst er skrøbeligt. Alternativet er at sende
valgmulighederne struktureret, hvilket ændrer protokollen og rammer begge
klienter. **Anbefaling: hverken-eller i v1.** Vis teksten som den er, med et
tekstfelt under. Tag knapperne op igen, når resten virker.

**Sproget.** Konsol-klienten siger `Which game?` og `It's not your turn.` på
engelsk. Web-spilleren er et godt sted at beslutte, om spillerfladen skal være
dansk — og hvis den skal, hører beslutningen til før UI'et skrives, ikke efter.

**Adresse.** `spil.apps.tobiasgrundtvig.dk` rammer den `*.apps`-wildcard, der
allerede står i Caddyfilen, og kræver **ingen ændring af infrastruktur**. Et
pænere navn koster en Caddy-blok. Start med wildcarden.

## Byggerækkefølge

Hver skive ender i noget, der kan demonstreres.

**0 — Modulet.** `textgame-web` med en `main`, der serverer én statisk side over
almindelig HTTP. Beviser HTTP-laget, før WebSocket kommer oveni.

**1 — Håndtrykket.** GET med `Upgrade: websocket` → `101` med
`Sec-WebSocket-Accept` = base64(SHA-1(nøgle + GUID)). `MessageDigest` og
`Base64` er i JDK'en. Test: JDK'ens WebSocket-klient forbinder og ekkoer.

**2 — Frames.** Tekst (0x1), close (0x8), ping/pong (0x9/0xA) og fortsættelses-
frames (0x0). Klient→server er altid maskeret, server→klient må aldrig være det.
Længdefelterne er 7, 7+16 eller 7+64 bit — det er der, fejlene sidder.

**3 — Broen.** Ved åbning: åbn en TCP-forbindelse til spilserveren. Pump derefter
linjer den ene vej og frames den anden. Luk begge, når én lukker. Test: en
JDK-WebSocket-klient spiller en hel kamp igennem broen, som `EndToEndTest` gør
det over rå TCP.

**4 — Browseren.** Samme tilstandsmaskine som `PlayerClient`: navn → spilliste →
bordliste → bord → kamp. Ren HTML og JavaScript, intet framework.

**5 — Kodeordet.** Loginfelt, der sender `PASSWORD` som første besked. Gem i
`sessionStorage`, så en genindlæsning ikke spørger igen.

**6 — Deploy.** Coolify-app, `dockerfile`, FQDN på `*.apps`-wildcarden.

**7 — Finpudsning.** Mobil-layout, genforbindelse, og så knapperne fra
"spørgsmål" ovenfor, hvis de stadig virker som en god idé.

## Test

Broen kan testes fuldt ud uden en browser, hvilket er hele pointen med at bruge
JDK'ens WebSocket-klient:

- **Håndtryk** — forkerte requests afvises, en rigtig får `101` med den rigtige
  accept-nøgle.
- **Frames** — grænserne: 125, 126 og 65536 bytes; maskeret input; fortsættelses-
  frames; ping besvares med pong; close lukker pænt.
- **Ende-til-ende** — rigtig server, rigtig bro, JDK-WebSocket-klient, hel kamp.
  Det er den test, der betyder noget, og den ligner `EndToEndTest`.
- **Drift mellem klienterne** — en test, der læser beskednavnene ud af
  JavaScript'en og sammenligner med `MessageType.values()`. Billig, og fanger
  netop den fejl, to klienter inviterer til.

Browseren selv: manuelt i denne omgang. `playwright-testing` findes i
knowledge-foundation, hvis det senere skal automatiseres.

## Deployment — verificeret 2026-09-04

- **Adressering.** Containerens netværks-alias er `<uuid>-<tidsstempel>` og
  **skifter ved hver deploy**, så navne-opslag duer ikke. Den publicerede port
  på hosten gør: fra `coolify`-netværket svarer `10.0.0.1:4000`. Sæt
  `TEXTGAME_HOST` og `TEXTGAME_PORT` som miljøvariable.
- **Ruten.** Broen taler HTTP, så den kan gå gennem Coolifys Traefik og den
  eksisterende `*.apps.tobiasgrundtvig.dk`-blok i Caddyfilen. **Ingen ændring i
  Caddyfilen** — modsat spilserveren, der måtte have sin egen port.
- **Tjek i sessionen:** at både Caddy og Traefik lader `Upgrade`-headeren gå
  igennem. Begge gør det som standard, men det er værd at se, ikke antage.

## Risici

**Håndskrevet WebSocket.** Frame-håndteringen er det eneste sted, hvor der er
rigtige fælder. Modsvaret er grænsetestene i skive 2 — skriv dem, før broen
bruges til noget.

**To klienter at holde i sync.** Hver protokolændring rammer nu både konsollen og
browseren. Det er den blivende omkostning ved det her, ikke bygge-arbejdet.
Drift-testen ovenfor gør den synlig i stedet for tavs.

**En offentlig webside.** Kodeord-porten gælder stadig, men en browser er en
lavere tærskel end en Java-jar. Sæt et loft over samtidige forbindelser pr.
IP i broen, mens vi er der.

## Omfang

En god dags arbejde for skive 0–6. Skive 7 er så meget eller lidt, som der er
lyst til.

Det haster ikke: virker port 4000 fra skolen, er det her en luksus. Virker den
ikke, er det svaret — og så er det pengene værd.
