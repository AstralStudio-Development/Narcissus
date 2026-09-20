package cn.starry.narcissus.functions.world;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.protocol.v47.PacketPlayOutChunkData47;
import cn.starry.narcissus.functions.world.lighting.BlockLightingProperties;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyChunkLightPacketTest
{
    private static final BlockLightingProperties V47 =
            BlockLightingProperties.load("v47", "1.8.9", 4096);

    @Test
    void serializesVanillaSectionOrderAndRealNibbles() throws Exception
    {
        World world = new World(1, 1);
        world.setBlock(15, 10, 0, 50, (byte) 0); // torch, emission 14
        world.prepareLegacyLighting(V47, true);

        DecodedChunk decoded = decode(new PacketPlayOutChunkData47(
                world.getChunk(0, 0), world.getLighting(), true));
        assertEquals(0, decoded.chunkX);
        assertEquals(0, decoded.chunkZ);
        assertEquals(3, decoded.primaryMask);
        int selectedSections = Integer.bitCount(decoded.primaryMask);
        assertEquals(selectedSections * (8192 + 2048 + 2048) + 256, decoded.payload.length);

        int blockIndex = ((10 << 8) | 15) * 2;
        assertEquals(0x20, decoded.payload[blockIndex] & 0xFF);
        assertEquals(0x03, decoded.payload[blockIndex + 1] & 0xFF);
        int blockLightOffset = selectedSections * 8192;
        int skyLightOffset = blockLightOffset + selectedSections * 2048;
        assertEquals(14, nibble(decoded.payload, blockLightOffset, 15, 10, 0));
        assertEquals(15, nibble(decoded.payload, skyLightOffset, 15, 10, 0));
    }

    @Test
    void serializesBlockLightHaloIntoAnEmptyNeighborChunk() throws Exception
    {
        World world = new World(1, 1);
        world.setBlock(15, 10, 0, 50, (byte) 0);
        world.prepareLegacyLighting(V47, true);
        assertEquals(13, world.getLighting().blockLightAt(16, 10, 0));

        DecodedChunk decoded = decode(new PacketPlayOutChunkData47(
                1, 0, world.getLighting(), true));
        assertEquals(1, decoded.chunkX);
        assertEquals(3, decoded.primaryMask);
        int selectedSections = Integer.bitCount(decoded.primaryMask);
        for (int index = 0; index < selectedSections * 8192; index++)
            assertEquals(0, decoded.payload[index]);
        assertEquals(13, nibble(decoded.payload, selectedSections * 8192, 0, 10, 0));
    }

    @Test
    void omitsSkyArraysInNoSkyDimension() throws Exception
    {
        World world = new World(1, 1, -1, -1);
        world.setBlock(-16, 10, -16, 89, (byte) 0);
        world.prepareLegacyLighting(V47, false);

        DecodedChunk decoded = decode(new PacketPlayOutChunkData47(
                world.getChunk(-1, -1), world.getLighting(), false));
        assertEquals(-1, decoded.chunkX);
        assertEquals(-1, decoded.chunkZ);
        assertEquals(3, decoded.primaryMask);
        int selectedSections = Integer.bitCount(decoded.primaryMask);
        assertEquals(selectedSections * (8192 + 2048) + 256, decoded.payload.length);
        assertEquals(15, nibble(decoded.payload, selectedSections * 8192, 0, 10, 0));
    }

    private static DecodedChunk decode(PacketPlayOutChunkData47 packet) throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        packet.write(new PacketSerializer(bytes));
        PacketSerializer input = new PacketSerializer(new ByteArrayInputStream(bytes.toByteArray()));
        int chunkX = input.readInt();
        int chunkZ = input.readInt();
        assertTrue(input.readBoolean());
        int primaryMask = input.readShort() & 0xFFFF;
        byte[] payload = input.readBytes(input.readVarInt());
        assertEquals(0, input.available());
        return new DecodedChunk(chunkX, chunkZ, primaryMask, payload);
    }

    private static int nibble(byte[] payload, int offset, int x, int y, int z)
    {
        int index = (y << 8) | (z << 4) | x;
        int packed = payload[offset + (index >> 1)] & 0xFF;
        return (index & 1) == 0 ? packed & 0xF : packed >>> 4;
    }

    private record DecodedChunk(int chunkX, int chunkZ, int primaryMask, byte[] payload)
    {
    }
}
