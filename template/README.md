# Lav dit eget spil

Et Maven-projekt, du kan kopiere og bygge dit eget tekstspil i. To klasser —
resten klarer biblioteket.

## Kom i gang

1. **Kopiér denne mappe** og omdøb den til dit spil.
2. **Åbn mappen i IntelliJ** (File → Open → vælg mappen, ikke filerne i den).
   IntelliJ henter selv biblioteket ned. Første gang tager det et øjeblik.
3. **Omdøb de to klasser** til noget, der passer til dit spil. Højreklik på
   klassenavnet → Refactor → Rename. `MyGame` og `MyGameMatch` skal begge
   omdøbes. `Spiller` skal du lade være.

Der ligger tre filer: `MyGame` og `MyGameMatch` er **dit spil**, og `Spiller`
er bare en grøn pil, der starter spiller-programmet.

## Kodeordet

Klasseserveren kræver et kodeord, så det ikke er hele internettet, der kan koble
sig på jeres spil.

**Lav en fil, der hedder `kodeord.txt`**, i projektmappen — samme sted som
`pom.xml` ligger. I filen skriver du det ord, du har fået af din underviser, og
ikke andet. Så finder programmet det selv; du skal ikke kalde noget.

```
mit-spil/
├── pom.xml
├── kodeord.txt      <- den her, som du selv laver
└── src/
```

**Kodeordet må ikke på GitHub.** Et offentligt repo kan alle læse, og så kan
alle koble sig på. Derfor står `kodeord.txt` i `.gitignore` — så tager git den
ikke med, heller ikke hvis du skriver `git add .`. Tjek det med `git status`:
filen skal *ikke* stå på listen.

Det er derfor kodeordet ligger i en fil og ikke i koden: koden deler du, filen
beholder du.

## Sådan spiller du

Du skal have **flere programmer i gang på én gang**: dit spil, og én spiller for
hver, der er med. I IntelliJ bliver de til hver sin fane nederst i Run-vinduet.

0. **Lav `kodeord.txt`** først, hvis du ikke har gjort det (se ovenfor). Uden
   den bliver du afvist, og programmet skriver hvorfor.

1. **Start dit spilprogram** — grøn pil ud for `main` i `MyGame`. Nu står dit
   spil i lobbyen på klasseserveren, og alle kan se det.

2. **Start en spiller** — grøn pil ud for `main` i `Spiller`.

   Skal I være flere på den samme computer, skal IntelliJ have lov at køre
   `Spiller` mere end én gang: **Run → Edit Configurations…**, vælg `Spiller`,
   og sæt flueben i **Allow multiple instances** (den ligger under
   *Modify options*). Uden det flueben genstarter IntelliJ bare den spiller,
   der allerede kører.

   Du skriver dine svar nede i Run-vinduet, præcis som når du bruger en
   `Scanner`.

3. **I hvert spiller-vindue**: skriv et navn, vælg spillet, og lav et bord i det
   ene vindue. I det andet skriver du **bordets navn** ved "Which game?" — så
   hopper du direkte derhen. Skriv `ready` begge steder, og spillet går i gang.

Serveren kører hele tiden, så du skal ikke selv starte noget. Den står som
`game.tobiasgrundtvig.dk` i `MyGame` og i `Spiller`.

## Hvis I spiller et andet sted

Begge filer skal pege det samme sted hen. Er det din underviser, der kører
serveren i klasselokalet, skal du bytte adressen ud to steder:

```java
GameServer.connect("game.tobiasgrundtvig.dk", 4000)                   // i MyGame
PlayerClient.main(new String[] {"game.tobiasgrundtvig.dk", "4000"});  // i Spiller
```

Det er de eneste to steder, adressen står.

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
