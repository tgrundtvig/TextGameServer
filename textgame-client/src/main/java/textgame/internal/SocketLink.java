package textgame.internal;

import java.io.IOException;
import textgame.GameServerException;
import textgame.protocol.Message;
import textgame.protocol.MessageChannel;

/** A {@link Link} over a real TCP connection. */
public final class SocketLink implements Link {

    private final MessageChannel channel;

    private SocketLink(MessageChannel channel) {
        this.channel = channel;
    }

    public static SocketLink connect(String host, int port) {
        try {
            return new SocketLink(MessageChannel.connect(host, port));
        } catch (IOException e) {
            throw new GameServerException(
                    "Could not reach the game server at " + host + ":" + port + "."
                            + " Check that the server is running and that the address and port"
                            + " are right.", e);
        }
    }

    @Override
    public void send(Message message) {
        try {
            channel.send(message);
        } catch (IOException e) {
            throw new GameServerException("Lost the connection to the game server.", e);
        }
    }

    @Override
    public Message receive() {
        try {
            return channel.receive();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void close() {
        channel.close();
    }
}
