package cn.starry.narcissus.functions.world;

import cn.starry.narcissus.functions.config.Configuration;
import cn.starry.narcissus.functions.managers.NetworkManager;
import cn.starry.narcissus.functions.modern.*;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutChunkWithLight774;
import cn.starry.narcissus.functions.packets.protocols.ModernProtocol;
import cn.starry.narcissus.functions.world.lighting.BlockLightingProperties;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.BitSet;
import static org.junit.jupiter.api.Assertions.*;

class Protocol777LightMaskEncodingTest {
    @Test void protocol777WritesByteArrayMasksForChunksAndIncrementalUpdates() throws Exception {
        var network = new NetworkManager(null, Configuration.builder().build());
        var protocol = (ModernProtocol) network.getProtocolByVersionId(777);
        assertTrue(protocol.hasByteArrayLightMasks());
        assertFalse(((ModernProtocol) network.getProtocolByVersionId(775)).hasByteArrayLightMasks());
        var world = new World(1, 1);
        world.setBlock(0, 0, 0, 0, (byte) 0);
        var states = ModernBlockStateMap.load("v777");
        world.prepareModernLighting(BlockLightingProperties.load("v777", "26.3", states.size()),
                LegacyBlockMap.load("v777"), states, 0, 16, false);
        var update = input(protocol.createLightUpdate(0, 0, world.getLighting(), new BitSet(), null));
        assertEquals(0, update.readVarInt()); assertEquals(0, update.readVarInt());
        assertEmptyLightMasks(update);
        var chunk = input(new PacketPlayOutChunkWithLight774(world, 0, 0, 16, 58, 0, false, true, true));
        chunk.readInt(); chunk.readInt();
        int heightmaps = chunk.readVarInt();
        for (int i = 0; i < heightmaps; i++) { chunk.readVarInt(); chunk.readBytes(chunk.readVarInt() * 8); }
        chunk.readBytes(chunk.readVarInt()); assertEquals(0, chunk.readVarInt());
        assertEmptyLightMasks(chunk);
    }
    private static void assertEmptyLightMasks(PacketSerializer in) throws Exception {
        assertEquals(0, in.readVarInt()); // sky byte-array length
        assertEquals(0, in.readVarInt()); // block byte-array length
        assertEquals(0, in.readVarInt()); // empty-sky byte-array length
        assertEquals(3, in.readVarInt());
        assertArrayEquals(new byte[]{(byte) 0xff, (byte) 0xff, 3}, in.readBytes(3));
        assertEquals(0, in.readVarInt()); assertEquals(0, in.readVarInt());
        assertEquals(0, in.available());
    }
    private static PacketSerializer input(PacketOut packet) throws Exception {
        var bytes = new ByteArrayOutputStream(); packet.write(new PacketSerializer(bytes));
        return new PacketSerializer(new ByteArrayInputStream(bytes.toByteArray()));
    }
}
