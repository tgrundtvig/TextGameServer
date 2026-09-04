package textgame.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads the shared class password out of a file next to the project's {@code pom.xml}.
 *
 * <p>A file rather than a line of code, so that the password does not travel with the source:
 * the template's {@code .gitignore} keeps it out of git, and pushing your game to GitHub does
 * not publish the class's password with it. Students meet that idea here for the first time,
 * which is the point.
 *
 * <p>The framework reads it, because reading a file is several weeks further along the
 * syllabus than writing a game is. There is nothing to call and nothing to import — but the
 * file is visible in the project, and the error when it is missing says exactly where it was
 * looked for.
 *
 * <p>The name is Danish because the course is.
 */
public final class PasswordFile {

    /** What the file is called, in the folder the program is run from. */
    public static final String NAME = "kodeord.txt";

    private PasswordFile() {
    }

    /** The password, or {@code null} if there is no such file. */
    public static String read() {
        Path path = path();
        if (!Files.isReadable(path)) {
            return null;
        }
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8).strip();
            return text.isEmpty() ? null : text;
        } catch (IOException e) {
            return null;
        }
    }

    /** Where the file is looked for, spelled out, for error messages worth reading. */
    public static Path path() {
        return Path.of(System.getProperty("user.dir", "."), NAME).toAbsolutePath();
    }

    /** What to tell somebody whose password is missing or wrong. */
    public static String howToFixIt() {
        return "Put the class password in a file called " + NAME + " next to your pom.xml."
                + " The framework looked here: " + path();
    }
}
