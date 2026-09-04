package textgame.example;

import java.util.Locale;
import textgame.Game;
import textgame.GameServer;

/**
 * Runs one of the example games against any server.
 *
 * <pre>{@code
 * java -jar textgame-example.jar NumberDuel                     // a server on this machine
 * java -jar textgame-example.jar NumberDuel prodesk-ubuntu 4000 // the class server
 * }</pre>
 *
 * <p>This exists so the examples themselves do not have to. A student's game says
 * {@code GameServer.connect("localhost", 4000)} in its own {@code main} and changes that one
 * line when it is time to play against everybody else — that is the whole story, and the
 * examples keep telling it. A launcher with argument parsing in front of it would teach the
 * launcher instead.
 */
public final class RunExample {

    private RunExample() {
    }

    public static void main(String[] args) {
        if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h")) {
            usage();
            return;
        }
        Game game = byName(args[0]);
        if (game == null) {
            System.out.println("There is no example called \"" + args[0] + "\".");
            usage();
            return;
        }
        String host = args.length > 1 ? args[1] : "localhost";
        int port = 4000;
        if (args.length > 2) {
            try {
                port = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                System.out.println("\"" + args[2] + "\" is not a port number.");
                usage();
                return;
            }
        }
        System.out.println("Hosting " + game.name() + " on " + host + ":" + port
                + " — stop this program to take it out of the lobby.");
        GameServer.connect(host, port).host(game);
    }

    /** Lenient on purpose: NumberDuel, numberduel and number-duel all mean the same game. */
    private static Game byName(String wanted) {
        return switch (simplify(wanted)) {
            case "numberduel" -> new NumberDuel();
            case "rockpaperscissors", "rps" -> new RockPaperScissors();
            case "impostor" -> new Impostor();
            default -> null;
        };
    }

    private static String simplify(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static void usage() {
        System.out.println();
        System.out.println("Usage: java -jar textgame-example.jar <game> [host] [port]");
        System.out.println();
        System.out.println("  NumberDuel           2-4 players. Guess the number first.");
        System.out.println("  RockPaperScissors    2-6 players. Three rounds, thrown at once.");
        System.out.println("  Impostor             3-8 players. Everybody got the same word."
                + " Almost everybody.");
        System.out.println();
        System.out.println("host defaults to localhost, port to 4000.");
    }
}
