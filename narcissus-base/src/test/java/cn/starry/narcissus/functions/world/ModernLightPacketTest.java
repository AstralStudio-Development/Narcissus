package cn.starry.narcissus.functions.world;

import cn.starry.narcissus.functions.modern.LegacyBlockMap;
import cn.starry.narcissus.functions.modern.ModernBlockStateMap;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutChunkWithLight774;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutLightUpdate774;
import cn.starry.narcissus.functions.world.lighting.BlockLightingProperties;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModernLightPacketTest
{
    @Test
    void chunkWithLightWritesRealMasksAndNibbleArrays() throws Exception
    {
        ModernBlockPalette palette = new ModernBlockPalette();
        World world = new World(1, 1, 0, 0, palette);
        world.setModernBlockState(1, 10, 1, palette.indexOf("minecraft:glowstone"));
        ModernBlockStateMap states = ModernBlockStateMap.load("v774");
        LegacyBlockMap legacy = LegacyBlockMap.load("v774");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new PacketPlayOutChunkWithLight774(world, world.getChunks()[0][0], 16, 56,
                legacy, states, null, "1.21.11", 0, false, false)
                .write(new PacketSerializer(bytes));

        PacketSerializer input = new PacketSerializer(new ByteArrayInputStream(bytes.toByteArray()));
        input.readInt();
        input.readInt();
        skipHeightmaps(input);
        input.readBytes(input.readVarInt());
        assertEquals(0, input.readVarInt()); // block entities
        LightData data = readLightData(input);

        assertTrue(data.skyData.isEmpty());
        assertTrue(data.emptySky.isEmpty());
        assertEquals(18, union(data.blockData, data.emptyBlock).cardinality());
        assertTrue(intersection(data.blockData, data.emptyBlock).isEmpty());
        assertEquals(data.blockData.cardinality(), data.blockArrays.size());
        assertFalse(data.blockArrays.isEmpty());
        assertTrue(data.blockArrays.stream().anyMatch(ModernLightPacketTest::containsLevelFifteen));
        assertTrue(data.blockArrays.stream().anyMatch(ModernLightPacketTest::containsLevelFourteen));
        assertEquals(0, input.available());
    }

    @Test
    void lightUpdateUsesChangedSectionMasksAndEmptyArraysAfterEmitterRemoval() throws Exception
    {
        ModernBlockPalette palette = new ModernBlockPalette();
        World world = new World(1, 1, 0, 0, palette);
        world.setModernBlockState(1, 10, 1, palette.indexOf("minecraft:glowstone"));
        ModernBlockStateMap states = ModernBlockStateMap.load("v774");
        world.prepareModernLighting(BlockLightingProperties.load("v774", "1.21.11", states.size()),
                LegacyBlockMap.load("v774"), states, 0, 16, false);
        assertTrue(world.setBlockAir(1, 10, 1));
        List<WorldLighting.ChunkLightChange> changes = world.consumeLightChanges();
        assertTrue(changes.size() > 1, "removing an edge-reaching source also clears halo chunks");
        WorldLighting.ChunkLightChange change = changes.stream()
                .filter(candidate -> candidate.chunkX() == 0 && candidate.chunkZ() == 0)
                .findFirst().orElseThrow();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new PacketPlayOutLightUpdate774(change.chunkX(), change.chunkZ(), world.getLighting(),
                change.skySections(), change.blockSections()).write(new PacketSerializer(bytes));

        PacketSerializer input = new PacketSerializer(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(0, input.readVarInt());
        assertEquals(0, input.readVarInt());
        LightData data = readLightData(input);
        assertEquals(change.skySections(), union(data.skyData, data.emptySky));
        assertEquals(change.blockSections(), union(data.blockData, data.emptyBlock));
        assertTrue(data.blockData.isEmpty(), "removed emitter leaves changed sections explicitly empty");
        assertEquals(change.blockSections(), data.emptyBlock);
        assertTrue(data.blockArrays.isEmpty());
        assertEquals(0, input.available());
    }

    @Test
    void eighteenLightSectionsAlignWithMinSectionMinusFour() throws Exception
    {
        ModernBlockPalette palette = new ModernBlockPalette();
        World world = new World(1, 1, 0, 0, palette);
        world.setModernBlockState(0, 0, 0, palette.indexOf("minecraft:glowstone"));
        ModernBlockStateMap states = ModernBlockStateMap.load("v774");
        LegacyBlockMap legacy = LegacyBlockMap.load("v774");
        world.prepareModernLighting(BlockLightingProperties.load("v774", "1.21.11", states.size()),
                legacy, states, -64, 16, false);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new PacketPlayOutChunkWithLight774(world, world.getChunk(0, 0), 16, 56,
                legacy, states, null, "1.21.11", -64, false, false)
                .write(new PacketSerializer(bytes));
        PacketSerializer input = new PacketSerializer(new ByteArrayInputStream(bytes.toByteArray()));
        input.readInt();
        input.readInt();
        skipHeightmaps(input);
        input.readBytes(input.readVarInt());
        assertEquals(0, input.readVarInt());
        LightData data = readLightData(input);

        assertEquals(18, union(data.blockData, data.emptyBlock).cardinality());
        assertTrue(data.blockData.get(5), "world Y 0 is light bit 5 when min section is -4");
        assertEquals(15, nibble(arrayForBit(data.blockData, data.blockArrays, 5), 0, 0, 0));
    }

    @Test
    void lightUpdatePreservesSignedNegativeChunkCoordinates() throws Exception
    {
        ModernBlockPalette palette = new ModernBlockPalette();
        World world = new World(1, 1, -1, -1, palette);
        world.setModernBlockState(-16, 10, -16, palette.indexOf("minecraft:glowstone"));
        ModernBlockStateMap states = ModernBlockStateMap.load("v774");
        world.prepareModernLighting(BlockLightingProperties.load("v774", "1.21.11", states.size()),
                LegacyBlockMap.load("v774"), states, 0, 16, false);
        assertTrue(world.setBlockAir(-16, 10, -16));
        WorldLighting.ChunkLightChange change = world.consumeLightChanges().stream()
                .filter(candidate -> candidate.chunkX() == -1 && candidate.chunkZ() == -1)
                .findFirst().orElseThrow();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new PacketPlayOutLightUpdate774(change.chunkX(), change.chunkZ(), world.getLighting(),
                change.skySections(), change.blockSections()).write(new PacketSerializer(bytes));
        PacketSerializer input = new PacketSerializer(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(-1, input.readVarInt());
        assertEquals(-1, input.readVarInt());
        readLightData(input);
        assertEquals(0, input.available());
    }

    @Test
    void emptyHaloChunkUsesAuthoritativeCrossBoundaryLight() throws Exception
    {
        ModernBlockPalette palette = new ModernBlockPalette();
        World world = new World(1, 1, 0, 0, palette);
        world.setModernBlockState(15, 10, 0, palette.indexOf("minecraft:glowstone"));
        ModernBlockStateMap states = ModernBlockStateMap.load("v774");
        world.prepareModernLighting(BlockLightingProperties.load("v774", "1.21.11", states.size()),
                LegacyBlockMap.load("v774"), states, 0, 16, false);
        assertEquals(14, world.getLighting().blockLightAt(16, 10, 0));

        LightData halo = chunkLightData(new PacketPlayOutChunkWithLight774(
                world, 1, 0, 16, 56, 0, false, false));
        assertTrue(halo.blockData.get(1), "light section one carries world Y 0..15");
        byte[] section = arrayForBit(halo.blockData, halo.blockArrays, 1);
        assertEquals(14, nibble(section, 0, 10, 0));

        LightData far = chunkLightData(new PacketPlayOutChunkWithLight774(
                world, 3, 0, 16, 56, 0, false, false));
        assertTrue(far.blockData.isEmpty());
        assertEquals(18, far.emptyBlock.cardinality());
    }

    private static LightData chunkLightData(PacketPlayOutChunkWithLight774 packet) throws Exception
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        packet.write(new PacketSerializer(bytes));
        PacketSerializer input = new PacketSerializer(new ByteArrayInputStream(bytes.toByteArray()));
        input.readInt();
        input.readInt();
        skipHeightmaps(input);
        input.readBytes(input.readVarInt());
        assertEquals(0, input.readVarInt());
        return readLightData(input);
    }

    private static byte[] arrayForBit(BitSet mask, List<byte[]> arrays, int bit)
    {
        int arrayIndex = mask.get(0, bit).cardinality();
        assertTrue(mask.get(bit));
        return arrays.get(arrayIndex);
    }

    private static int nibble(byte[] data, int x, int y, int z)
    {
        int index = (y << 8) | (z << 4) | x;
        int packed = data[index >> 1] & 0xFF;
        return (index & 1) == 0 ? packed & 0xF : packed >>> 4;
    }

    private static void skipHeightmaps(PacketSerializer input) throws Exception
    {
        int count = input.readVarInt();
        for (int i = 0; i < count; i++)
        {
            input.readVarInt();
            input.readBytes(input.readVarInt() * 8);
        }
    }

    private static LightData readLightData(PacketSerializer input) throws Exception
    {
        BitSet skyData = readBitSet(input);
        BitSet blockData = readBitSet(input);
        BitSet emptySky = readBitSet(input);
        BitSet emptyBlock = readBitSet(input);
        List<byte[]> skyArrays = readArrays(input);
        List<byte[]> blockArrays = readArrays(input);
        return new LightData(skyData, blockData, emptySky, emptyBlock, skyArrays, blockArrays);
    }

    private static BitSet readBitSet(PacketSerializer input) throws Exception
    {
        int longs = input.readVarInt();
        long[] words = new long[longs];
        for (int i = 0; i < longs; i++)
            words[i] = input.readLong();
        return BitSet.valueOf(words);
    }

    private static List<byte[]> readArrays(PacketSerializer input) throws Exception
    {
        int count = input.readVarInt();
        List<byte[]> arrays = new ArrayList<>();
        for (int i = 0; i < count; i++)
        {
            byte[] data = input.readBytes(input.readVarInt());
            assertEquals(2048, data.length);
            arrays.add(data);
        }
        return arrays;
    }

    private static boolean containsLevelFifteen(byte[] data)
    {
        for (byte packed : data)
        {
            if ((packed & 0xF) == 15 || ((packed >>> 4) & 0xF) == 15)
                return true;
        }
        return false;
    }

    private static boolean containsLevelFourteen(byte[] data)
    {
        for (byte packed : data)
        {
            if ((packed & 0xF) == 14 || ((packed >>> 4) & 0xF) == 14)
                return true;
        }
        return false;
    }

    private static BitSet union(BitSet first, BitSet second)
    {
        BitSet result = (BitSet) first.clone();
        result.or(second);
        return result;
    }

    private static BitSet intersection(BitSet first, BitSet second)
    {
        BitSet result = (BitSet) first.clone();
        result.and(second);
        return result;
    }

    private record LightData(BitSet skyData, BitSet blockData,
                             BitSet emptySky, BitSet emptyBlock,
                             List<byte[]> skyArrays, List<byte[]> blockArrays)
    {
    }
}
