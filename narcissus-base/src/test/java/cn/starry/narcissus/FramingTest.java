package cn.starry.narcissus;

import cn.starry.narcissus.functions.config.Configuration;
import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the incremental inbound accumulator on PlayerManager together with
 * the shared VarInt frame header decoder: split headers, joined frames and
 * consuming exactly one frame at a time.
 */
class FramingTest {

    private static PlayerManager newPlayer() throws Exception {
        // Bind a throwaway acceptor so we can create a real connected SocketChannel
        // and construct a PlayerManager against it.
        Configuration cfg = Configuration.builder().ip("127.0.0.1").build();
        NetworkManager manager = new NetworkManager(null, cfg);
        manager.bind("127.0.0.1", 0);
        int port = manager.getBoundAddress().getPort();

        Thread accept = new Thread(() -> {
            try {
                while (true) manager.select();
            } catch (Exception ignored) {
            }
        }, "framing-accept");
        accept.setDaemon(true);
        accept.start();

        SocketChannel client = SocketChannel.open(new InetSocketAddress("127.0.0.1", port));
        client.configureBlocking(false);
        PlayerManager player = new PlayerManager(manager, client);
        manager.stop();
        return player;
    }

    /** Simulates NetworkManager frame extraction over the player's accumulator. */
    private static byte[] pollFrame(PlayerManager player) throws IOException {
        byte[] data = player.getInboundData();
        int available = player.getInboundLength();
        int[] header = PacketSerializer.tryReadVarInt(data, 0, available);
        if (header == null) return null;
        int frameLength = header[0];
        int headerBytes = header[1];
        if (available - headerBytes < frameLength) return null;
        byte[] frame = new byte[frameLength];
        System.arraycopy(data, headerBytes, frame, 0, frameLength);
        player.consumeInbound(headerBytes + frameLength);
        return frame;
    }

    @Test
    void singleFrameDeliveredInPieces() throws Exception {
        PlayerManager player = newPlayer();
        // frame length 3, body {0x00, 0x01, 0x02}; deliver header and body separately
        player.appendInbound(new byte[]{0x03});
        assertNull(pollFrame(player), "frame incomplete after only the length byte");
        player.appendInbound(new byte[]{0x00, 0x01});
        assertNull(pollFrame(player), "frame incomplete with partial body");
        player.appendInbound(new byte[]{0x02});
        assertArrayEquals(new byte[]{0x00, 0x01, 0x02}, pollFrame(player));
        assertNull(pollFrame(player));
    }

    @Test
    void multipleFramesInOneChunk() throws Exception {
        PlayerManager player = newPlayer();
        // two frames: len1={0x0A}, len2={0x0B,0x0C}
        player.appendInbound(new byte[]{0x01, 0x0A, 0x02, 0x0B, 0x0C});
        assertArrayEquals(new byte[]{0x0A}, pollFrame(player));
        assertArrayEquals(new byte[]{0x0B, 0x0C}, pollFrame(player));
        assertNull(pollFrame(player));
    }

    @Test
    void largeFrameWithMultiByteLength() throws Exception {
        PlayerManager player = newPlayer();
        int len = 300;
        byte[] body = new byte[len];
        for (int i = 0; i < len; i++) body[i] = (byte) (i & 0xFF);

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        PacketSerializer s = new PacketSerializer(out);
        s.writeVarInt(len);
        s.writeRawBytes(body);
        player.appendInbound(out.toByteArray());

        byte[] frame = pollFrame(player);
        assertNotNull(frame);
        assertEquals(len, frame.length);
        assertArrayEquals(body, frame);
    }
}
