# Lav dit eget spil

Et Maven-projekt, du kan kopiere og bygge dit eget tekstspil i. To klasser —
resten klarer biblioteket.

## Kom i gang

1. **Kopiér denne mappe** og omdøb den til dit spil.
2. **Åbn mappen i IntelliJ** (File → Open → vælg mappen, ikke filerne i den).
   IntelliJ henter selv biblioteket ned. Første gang tager det et øjeblik.
3. **Omdøb de to klasser** til noget, der passer til dit spil. Højreklik på
   klassenavnet → Refactor → Rename. `MyGame` og `MyGameMatch` skal begge
   omdøbes.

## Sådan spiller du

Du skal bruge **tre vinduer**: ét til spillet og ét til hver spiller.

1. **Start en server.** Under udviklingen kører du din egen:

   ```
   java -jar textgame-server.jar 4000
   ```

2. **Start dit spilprogram** — grøn pil ud for `main` i `MyGame`. Nu står dit
   spil i lobbyen.

3. **Start en spiller** — højreklik på `PlayerClient` i
   `External Libraries → textgame-client → textgame.player` → Run.
   Gør det én gang for hver spiller. Du kan også køre den fra en terminal:

   ```
   mvn compile exec:java -Dexec.mainClass=textgame.player.PlayerClient
   ```

I hvert spiller-vindue: skriv et navn, vælg spillet, og lav et bord i det ene
vindue. I det andet skriver du **bordets navn** ved "Which game?" — så hopper du
direkte derhen. Skriv `ready` begge steder, og spillet går i gang.

## De fem regler

1. **Skriv to klasser.** En `Game`, der siger hvad spillet hedder og hvor mange
   der kan være med, og en `Match`, der spiller det.
2. **`newMatch()` returnerer altid et nyt objekt.** `return new MyGameMatch();`
   — aldrig et felt, aldrig det samme objekt to gange.
3. **Ingen `static` felter med spildata**, og intet foranderligt på din `Game`.
   Flere spil kan køre samtidig i det samme program.
4. **Brug aldrig `System.out` eller `Scanner` i spillet.** Tekst ud sker med
   `tell` og `tellAll`, tekst ind med `ask`. `System.out.println` skriver på
   *din* skærm, hvor ingen spillere kan se det — fint til fejlfinding,
   ubrugeligt til at spille.
5. **Når `play()` er færdig, er spillet slut.** Vil du stoppe før tid, så
   `return`.

## Hvad du kan spørge om

En `Player` (én spiller):

| | |
|---|---|
| `p.name()` | spillerens navn |
| `p.tell("...")` | skriv til spilleren |
| `p.ask("...")` | spørg, og få linjen præcis som den blev skrevet |
| `p.askInt("...")` / `p.askInt("...", 1, 100)` | spørg om et helt tal |
| `p.askDouble("...")` | spørg om et tal med komma |
| `p.askYesNo("...")` | ja eller nej |
| `p.askChoice("...", "sten", "saks")` | menu — giver teksten tilbage |
| `p.askChoiceIndex("...", "sten", "saks")` | samme menu — giver nummeret (fra 0) |

Et `Room` (hele bordet):

| | |
|---|---|
| `room.players()` | alle spillere, i den rækkefølge de kom |
| `room.tellAll("...")` | skriv til alle |
| `room.only(p)` / `room.without(p)` | kun nogle af dem |
| `room.askAll("...")`, `askAllInt`, `askAllYesNo`, `askAllChoice` | spørg alle på én gang |

Spørger du alle på én gang, får du et `Answers` tilbage: `svar.get(p)`,
`svar.getInt(p)`, `svar.getYesNo(p)`, `svar.getIndex(p)`.

## Ting, du ikke skal bekymre dig om

Biblioteket klarer dem:

- **Forkert input.** Skriver en spiller `banan` til `askInt`, bliver hun spurgt
  igen. Dit spil ser det aldrig.
- **En spiller mister forbindelsen.** Spillet stopper pænt for de andre. Du
  skriver ingen fejlhåndtering.
- **Nogen skriver uden for tur.** Det bliver afvist, ikke gemt.
- **Flere borde samtidig.** Hvert bord kører for sig selv.
