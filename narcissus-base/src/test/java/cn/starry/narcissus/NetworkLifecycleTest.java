package cn.starry.narcissus;

import cn.starry.narcissus.functions.config.Configuration;
import cn.starry.narcissus.functions.managers.NetworkManager;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the NetworkManager bind/stop lifecycle to guarantee the listener can
 * be rebound immediately after shutdown (no leaked selector/thread/socket).
 */
class NetworkLifecycleTest {

    private static NetworkManager start(int port) throws Exception {
        // Port validation lives in the Configuration builder; the bind() port is
        // passed explicitly, so use a valid placeholder for the config object.
        Configuration cfg = Configuration.builder().ip("127.0.0.1").build();
        NetworkManager manager = new NetworkManager(null, cfg);
        manager.bind("127.0.0.1", port);
        Thread thread = new Thread(() -> {
            try {
                while (true) manager.select();
            } catch (Exception ignored) {
            }
        }, "test-net-" + port);
        thread.setDaemon(true);
        thread.start();
        return manager;
    }

    @Test
    void bindsToEphemeralPortAndReportsAddress() throws Exception {
        NetworkManager manager = start(0);
        try {
            InetSocketAddress bound = manager.getBoundAddress();
            assertNotNull(bound);
            assertTrue(bound.getPort() > 0);
        } finally {
            manager.stop();
        }
    }

    @Test
    void acceptsAConnection() throws Exception {
        NetworkManager manager = start(0);
        try {
            int port = manager.getBoundAddress().getPort();
            try (SocketChannel client = SocketChannel.open(new InetSocketAddress("127.0.0.1", port))) {
                assertTrue(client.isConnected());
            }
        } finally {
            manager.stop();
        }
    }

    @Test
    void portCanBeReboundAfterStop() throws Exception {
        NetworkManager first = start(0);
        int port = first.getBoundAddress().getPort();
        first.stop();

        // Give the selector thread a brief moment to release the socket.
        long deadline = System.currentTimeMillis() + 3000L;
        Exception last = null;
        NetworkManager second = null;
        while (System.currentTimeMillis() < deadline) {
            try {
                Configuration cfg = Configuration.builder().ip("127.0.0.1").build();
                second = new NetworkManager(null, cfg);
                second.bind("127.0.0.1", port);
                break;
            } catch (Exception ex) {
                last = ex;
                second = null;
                Thread.sleep(50L);
            }
        }
        try {
            assertNotNull(second, "port was not rebindable after stop: " + last);
            assertEquals(port, second.getBoundAddress().getPort());
        } finally {
            if (second != null) second.stop();
        }
    }

    @Test
    void stopIsIdempotent() throws Exception {
        NetworkManager manager = start(0);
        manager.stop();
        assertDoesNotThrow(manager::stop);
    }
}
