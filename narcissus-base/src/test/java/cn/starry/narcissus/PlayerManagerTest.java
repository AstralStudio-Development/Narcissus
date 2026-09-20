package cn.starry.narcissus;

import cn.starry.narcissus.functions.config.Configuration;
import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerManagerTest {
    @Test
    void forwardedUuidCannotBeOverwrittenByLoginStartUuid() throws IOException {
        UUID forwarded = UUID.fromString("01234567-89ab-cdef-0123-456789abcdef");
        UUID loginStart = UUID.fromString("fedcba98-7654-3210-fedc-ba9876543210");
        NetworkManager networkManager = new NetworkManager(null, Configuration.builder().build());

        try (ServerSocketChannel listener = ServerSocketChannel.open()) {
            listener.bind(new InetSocketAddress("127.0.0.1", 0));
            try (SocketChannel client = SocketChannel.open(listener.getLocalAddress());
                 SocketChannel accepted = listener.accept()) {
                PlayerManager playerManager = new PlayerManager(networkManager, accepted);
                playerManager.setForwardedUuid(forwarded);
                playerManager.setUuid(loginStart);

                assertEquals(forwarded, playerManager.getUuid());
                assertTrue(playerManager.isUuidProvided());
                assertTrue(playerManager.isUuidForwarded());
            }
        }
    }
}
