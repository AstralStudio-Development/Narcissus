package cn.starry.narcissus.functions.world;

import cn.starry.narcissus.functions.config.Configuration;
import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.managers.PlayerManager;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.Status;
import cn.starry.narcissus.functions.packets.in.protocol.v774.PacketPlayInPlayerAction774;
import cn.starry.narcissus.functions.packets.protocols.ModernProtocol;
import cn.starry.narcissus.functions.packets.protocols.Protocol47;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerActionRelightTest
{
    @Test
    void diggingSendsBlockUpdateThenCrossChunkLightUpdatesForBothModernProtocols() throws Exception
    {
        assertDiggingRelight(774, 0x08, 0x2f);
        assertDiggingRelight(776, 0x08, 0x30);
    }

    @Test
    void legacyLoginHaloSenderQueuesAdjacentEmptyLightChunks() throws Exception
    {
        World template = new World(1, 1);
        template.setBlock(15, 10, 0, 50, (byte) 0);
        NetworkManager network = new NetworkManager(template,
                Configuration.builder().dimension((byte) 0).build());
        try (ServerSocketChannel listener = ServerSocketChannel.open())
        {
            listener.bind(new InetSocketAddress("127.0.0.1", 0));
            try (SocketChannel client = SocketChannel.open(listener.getLocalAddress());
                 SocketChannel accepted = listener.accept())
            {
                PlayerManager player = new PlayerManager(network, accepted);
                player.setProtocol(network.getProtocolByVersionId(47));
                player.setProtocolId(47);
                player.setStatus(Status.PLAY);
                player.setWorld(network.createPlayerWorld());
                ((Protocol47) player.getProtocol()).sendLightHaloChunks(player);

                boolean foundEastHalo = false;
                ByteBuffer frame;
                while ((frame = player.getOutboundQueue().poll()) != null)
                {
                    PacketSerializer payload = packetPayload(frame);
                    assertEquals(0x21, payload.readVarInt());
                    int chunkX = payload.readInt();
                    int chunkZ = payload.readInt();
                    if (chunkX == 1 && chunkZ == 0)
                        foundEastHalo = true;
                }
                assertTrue(foundEastHalo);
            }
        }
        finally
        {
            network.stop();
        }
    }

    private static void assertDiggingRelight(int version, int blockUpdateId, int lightUpdateId)
            throws Exception
    {
        Configuration configuration = Configuration.builder().dimension((byte) 1).build();
        NetworkManager network = new NetworkManager(null, configuration);
        try (ServerSocketChannel listener = ServerSocketChannel.open())
        {
            listener.bind(new InetSocketAddress("127.0.0.1", 0));
            try (SocketChannel client = SocketChannel.open(listener.getLocalAddress());
                 SocketChannel accepted = listener.accept())
            {
                PlayerManager player = new PlayerManager(network, accepted);
                player.setProtocol(network.getProtocolByVersionId(version));
                player.setProtocolId(version);
                player.setStatus(Status.PLAY);
                ModernProtocol protocol = (ModernProtocol) player.getProtocol();

                ModernBlockPalette palette = new ModernBlockPalette();
                World world = new World(1, 1, 0, 0, palette);
                world.setModernBlockState(15, 10, 0, palette.indexOf("minecraft:glowstone"));
                world.prepareModernLighting(protocol.getLightingProperties(), protocol.getLegacyBlockMap(),
                        protocol.getModernBlockStateMap(), protocol.getMinY(), protocol.getSectionCount(),
                        protocol.hasSkyLight());
                World otherPlayerWorld = world.copy();
                player.setWorld(world);

                PacketPlayInPlayerAction774 action = new PacketPlayInPlayerAction774();
                ByteArrayOutputStream actionBytes = new ByteArrayOutputStream();
                PacketSerializer actionWriter = new PacketSerializer(actionBytes);
                actionWriter.writeVarInt(2); // FINISHED_DIGGING
                actionWriter.writeLong(packBlockPosition(15, 10, 0));
                actionWriter.writeByte((byte) 1);
                actionWriter.writeVarInt(7);
                action.readPacket(new PacketSerializer(
                        new ByteArrayInputStream(actionBytes.toByteArray())));
                action.handlePacket(network, player);

                assertEquals(0, world.getLighting().blockLightAt(16, 10, 0));
                assertEquals(14, otherPlayerWorld.getLighting().blockLightAt(16, 10, 0));

                ByteBuffer first = player.getOutboundQueue().poll();
                assertEquals(blockUpdateId, packetId(first));
                boolean foundNeighborHalo = false;
                ByteBuffer frame;
                while ((frame = player.getOutboundQueue().poll()) != null)
                {
                    PacketSerializer payload = packetPayload(frame);
                    assertEquals(lightUpdateId, payload.readVarInt());
                    int chunkX = payload.readVarInt();
                    int chunkZ = payload.readVarInt();
                    if (chunkX == 1 && chunkZ == 0)
                        foundNeighborHalo = true;
                }
                assertTrue(foundNeighborHalo, "digging must clear propagated light in the adjacent empty chunk");
                assertFalse(world.consumeLightChanges().iterator().hasNext(),
                        "the action handler must consume the relight diff after enqueueing packets");
            }
        }
        finally
        {
            network.stop();
        }
    }

    private static int packetId(ByteBuffer frame) throws Exception
    {
        return packetPayload(frame).readVarInt();
    }

    private static PacketSerializer packetPayload(ByteBuffer frame) throws Exception
    {
        assertTrue(frame != null);
        byte[] framed = new byte[frame.remaining()];
        frame.get(framed);
        PacketSerializer input = new PacketSerializer(new ByteArrayInputStream(framed));
        int length = input.readVarInt();
        byte[] payload = input.readBytes(length);
        assertEquals(0, input.available());
        return new PacketSerializer(new ByteArrayInputStream(payload));
    }

    private static long packBlockPosition(int x, int y, int z)
    {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (y & 0xFFF) << 26)
                | (z & 0x3FFFFFFL);
    }
}
