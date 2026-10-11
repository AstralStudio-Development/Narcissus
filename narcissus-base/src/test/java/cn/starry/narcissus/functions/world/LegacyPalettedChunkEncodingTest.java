package cn.starry.narcissus.functions.world;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import cn.starry.narcissus.functions.packets.out.protocol.v107.PacketPlayOutChunkData107;
import cn.starry.narcissus.functions.packets.out.protocol.v110.PacketPlayOutChunkData110;
import cn.starry.narcissus.functions.packets.out.protocol.v340.PacketPlayOutKeepAlive340;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class LegacyPalettedChunkEncodingTest {
    @Test void v107AndV110UseGlobalPalettesWithSpanningThirteenBitEntries() throws Exception {
        var world = new World(1, 1);
        world.setBlock(9, 0, 0, 255, (byte) 15); // state 4095 crosses a long boundary at bit 53
        world.setBlock(10, 0, 0, 1, (byte) 0);
        Chunk chunk = world.getChunk(0, 0);
        for (boolean newer : new boolean[]{false, true}) {
            PacketOut packet = newer ? new PacketPlayOutChunkData110(chunk, true) : new PacketPlayOutChunkData107(chunk, false);
            var bytes = new ByteArrayOutputStream(); packet.write(new PacketSerializer(bytes));
            var in = new PacketSerializer(new ByteArrayInputStream(bytes.toByteArray()));
            assertEquals(0, in.readInt()); assertEquals(0, in.readInt()); assertTrue(in.readBoolean());
            assertEquals(1, in.readVarInt());
            var payload = new PacketSerializer(new ByteArrayInputStream(in.readBytes(in.readVarInt())));
            assertEquals(13, payload.readUnsignedByte()); assertEquals(0, payload.readVarInt());
            assertEquals(832, payload.readVarInt());
            long[] packed = new long[832]; for (int i = 0; i < packed.length; i++) packed[i] = payload.readLong();
            assertEquals(4095, state(packed, 9)); assertEquals(16, state(packed, 10));
            payload.readBytes(2048); if (newer) payload.readBytes(2048);
            payload.readBytes(256); assertEquals(0, payload.available());
            if (newer) assertEquals(0, in.readVarInt()); // added in 1.9.3
            assertEquals(0, in.available());
        }
    }
    @Test void v340KeepAliveIsEightBytesNotAnInt() throws Exception {
        var bytes = new ByteArrayOutputStream();
        new PacketPlayOutKeepAlive340(0x0102030405060708L).write(new PacketSerializer(bytes));
        assertArrayEquals(new byte[]{1,2,3,4,5,6,7,8}, bytes.toByteArray());
    }
    private static int state(long[] data, int index) {
        int bit = index * 13, word = bit >>> 6, shift = bit & 63;
        long value = data[word] >>> shift;
        if (shift > 51) value |= data[word + 1] << (64 - shift);
        return (int) (value & 8191);
    }
}
