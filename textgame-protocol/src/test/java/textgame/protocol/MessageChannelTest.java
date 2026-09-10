package textgame.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ServerSocket;
import java.net.Socket;
import jdk.net.ExtendedSocketOptions;
import org.junit.jupiter.api.Test;

/**
 * The one thing a channel does beyond carrying lines: it keeps the connection under
 * watch, so that a machine that vanishes is noticed rather than waited for forever.
 */
class MessageChannelTest {

    @Test
    void everyChannelHasKeepaliveOnWithShortTimers() throws Exception {
        try (ServerSocket listener = new ServerSocket(0);
             MessageChannel dialed = MessageChannel.connect("localhost", listener.getLocalPort());
             Socket acceptedSocket = listener.accept();
             MessageChannel accepted = new MessageChannel(acceptedSocket)) {

            for (MessageChannel channel : new MessageChannel[] {dialed, accepted}) {
                Socket socket = channel.socket();
                assertTrue(socket.getKeepAlive(), "keepalive is off on " + socket);

                // The timers are a platform extension; where they exist they must be ours,
                // because the OS default is two hours and two hours is the bug.
                if (socket.supportedOptions().contains(ExtendedSocketOptions.TCP_KEEPIDLE)) {
                    assertEquals(MessageChannel.KEEPALIVE_IDLE_SECONDS,
                            socket.getOption(ExtendedSocketOptions.TCP_KEEPIDLE));
                }
                if (socket.supportedOptions().contains(ExtendedSocketOptions.TCP_KEEPINTERVAL)) {
                    assertEquals(MessageChannel.KEEPALIVE_INTERVAL_SECONDS,
                            socket.getOption(ExtendedSocketOptions.TCP_KEEPINTERVAL));
                }
                if (socket.supportedOptions().contains(ExtendedSocketOptions.TCP_KEEPCOUNT)) {
                    assertEquals(MessageChannel.KEEPALIVE_PROBES,
                            socket.getOption(ExtendedSocketOptions.TCP_KEEPCOUNT));
                }
            }
        }
    }
}
