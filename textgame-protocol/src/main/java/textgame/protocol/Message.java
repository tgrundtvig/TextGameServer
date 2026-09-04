package textgame.protocol;

import java.util.ArrayList;
import java.util.List;

/**
 * One line of the wire protocol, parsed.
 *
 * <p>A message is a {@link MessageType}, zero or more space-free arguments, and
 * optionally a free-text field that runs to the end of the line. Which of those
 * a message carries is fixed by its type.
 *
 * @param type the message type
 * @param args the fixed arguments; exactly {@link MessageType#argCount()} of them
 * @param text the free-text field, or {@code null} when the type has none
 */
public record Message(MessageType type, List<String> args, String text) {

    public Message {
        if (type == null) {
            throw new ProtocolException("message type is required");
        }
        args = List.copyOf(args);
        if (args.size() != type.argCount()) {
            throw new ProtocolException(
                    type + " takes " + type.argCount() + " argument(s), got " + args.size());
        }
        for (String arg : args) {
            if (arg.isEmpty() || arg.chars().anyMatch(Character::isWhitespace)) {
                throw new ProtocolException(
                        type + " argument must be non-empty and space-free: '" + arg + "'");
            }
        }
        if (type.hasText() == (text == null)) {
            throw new ProtocolException(
                    type + (type.hasText() ? " requires text" : " takes no text"));
        }
    }

    /** A message with no free-text field. */
    public static Message of(MessageType type, String... args) {
        return new Message(type, List.of(args), null);
    }

    /** A message that is nothing but free text. */
    public static Message withText(MessageType type, String text) {
        return new Message(type, List.of(), text);
    }

    public static Message withText(MessageType type, String arg, String text) {
        return new Message(type, List.of(arg), text);
    }

    public static Message withText(MessageType type, String arg1, String arg2, String text) {
        return new Message(type, List.of(arg1, arg2), text);
    }

    public static Message withText(MessageType type, String arg1, String arg2, String arg3,
                                   String text) {
        return new Message(type, List.of(arg1, arg2, arg3), text);
    }

    public static Message withText(MessageType type, String arg1, String arg2, String arg3,
                                   String arg4, String text) {
        return new Message(type, List.of(arg1, arg2, arg3, arg4), text);
    }

    /** The argument at {@code index}, counting from zero. */
    public String arg(int index) {
        return args.get(index);
    }

    /** Renders this message as a single line, without the line terminator. */
    public String encode() {
        StringBuilder line = new StringBuilder(type.name());
        for (String arg : args) {
            line.append(' ').append(arg);
        }
        if (text != null) {
            line.append(' ').append(escape(text));
        }
        return line.toString();
    }

    /**
     * Parses one line into a message.
     *
     * @throws ProtocolException if the word is unknown or the arguments do not fit
     */
    public static Message decode(String line) {
        String rest = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;

        String[] head = rest.split(" ", 2);
        MessageType type;
        try {
            type = MessageType.valueOf(head[0]);
        } catch (IllegalArgumentException e) {
            throw new ProtocolException("unknown message: '" + head[0] + "'");
        }
        rest = head.length > 1 ? head[1] : "";

        List<String> args = new ArrayList<>(type.argCount());
        for (int i = 0; i < type.argCount(); i++) {
            String[] split = rest.split(" ", 2);
            if (split[0].isEmpty()) {
                throw new ProtocolException(type + " is missing argument " + (i + 1));
            }
            args.add(split[0]);
            rest = split.length > 1 ? split[1] : "";
        }

        if (!type.hasText() && !rest.isEmpty()) {
            throw new ProtocolException(type + " takes no text, but got: '" + rest + "'");
        }
        return new Message(type, args, type.hasText() ? unescape(rest) : null);
    }

    /** Hides the characters that would otherwise break the one-message-per-line framing. */
    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String unescape(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c != '\\' || i + 1 == text.length()) {
                out.append(c);
                continue;
            }
            char next = text.charAt(++i);
            switch (next) {
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case '\\' -> out.append('\\');
                default -> out.append('\\').append(next);
            }
        }
        return out.toString();
    }

    @Override
    public String toString() {
        return encode();
    }
}
