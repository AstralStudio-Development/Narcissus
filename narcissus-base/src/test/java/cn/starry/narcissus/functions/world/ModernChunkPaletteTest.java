package cn.starry.narcissus.functions.world;

import cn.starry.narcissus.functions.modern.LegacyBlockMap;
import cn.starry.narcissus.functions.modern.ModernBlockStateMap;
import cn.starry.narcissus.functions.modern.ModernChunk;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModernChunkPaletteTest
{
    @Test
    void packsLocalPaletteEntriesInClientLowToHighOrder() throws Exception
    {
        ModernBlockPalette palette = new ModernBlockPalette();
        World world = new World(1, 1, 0, 0, palette);
        int stoneKey = palette.indexOf("minecraft:stone");
        int dirtKey = palette.indexOf("minecraft:dirt");
        world.setModernBlockState(0, 0, 0, stoneKey);
        world.setModernBlockState(1, 0, 0, dirtKey);

        ModernBlockStateMap stateMap = ModernBlockStateMap.load("v774");
        int stoneId = stateMap.resolve("minecraft:stone");
        int dirtId = stateMap.resolve("minecraft:dirt");
        byte[] encoded = ModernChunk.buildChunkData(world, world.getChunks()[0][0], 16, 0,
                LegacyBlockMap.load("v774"), stateMap, false);

        DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));
        assertEquals(2, input.readShort());
        assertEquals(4, input.readUnsignedByte());
        int paletteSize = readVarInt(input);
        int[] blockPalette = new int[paletteSize];
        for (int i = 0; i < paletteSize; i++)
            blockPalette[i] = readVarInt(input);

        int entriesPerLong = 64 / 4;
        int longCount = (4096 + entriesPerLong - 1) / entriesPerLong;
        long first = input.readLong();
        int firstSlot = (int) (first & 0xF);
        int secondSlot = (int) ((first >>> 4) & 0xF);
        assertEquals(stoneId, blockPalette[firstSlot]);
        assertEquals(dirtId, blockPalette[secondSlot]);
        for (int i = 1; i < longCount; i++)
            input.readLong();
    }

    @Test
    void offsetsOverworldSchematicSectionsByFour() throws Exception
    {
        ModernBlockPalette palette = new ModernBlockPalette();
        World world = new World(1, 1, 0, 0, palette);
        world.setModernBlockState(0, 20, 0, palette.indexOf("minecraft:stone"));

        byte[] encoded = ModernChunk.buildChunkData(world, world.getChunks()[0][0],
                24, -64, 40, LegacyBlockMap.load("v774"),
                ModernBlockStateMap.load("v774"), false);
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(encoded));

        // Source Y=20 is source section 1. With minY=-64 it belongs in packet section 5.
        for (int packetSection = 0; packetSection < 5; packetSection++)
        {
            assertEquals(0, input.readShort());
            skipSingleValuePalette(input); // air block states
            skipSingleValuePalette(input); // plains biome
        }
        assertEquals(1, input.readShort());
        assertEquals(true, ModernChunk.nonEmptySections(world, world.getChunks()[0][0], 24, -64).get(6));
    }

    @Test
    void heightmapStoresDistanceAboveDimensionMinY()
    {
        ModernBlockPalette palette = new ModernBlockPalette();
        World world = new World(1, 1, 0, 0, palette);
        world.setModernBlockState(0, 20, 0, palette.indexOf("minecraft:stone"));
        ModernBlockStateMap stateMap = ModernBlockStateMap.load("v774");

        long[] end = ModernChunk.buildMotionBlockingHeightmap(
                world, world.getChunks()[0][0], 16, 0, stateMap);
        long[] overworld = ModernChunk.buildMotionBlockingHeightmap(
                world, world.getChunks()[0][0], 24, -64, stateMap);

        assertEquals(21L, end[0] & 0x1FFL);
        assertEquals(85L, overworld[0] & 0x1FFL);
    }

    private static void skipSingleValuePalette(DataInputStream input) throws IOException
    {
        assertEquals(0, input.readUnsignedByte());
        readVarInt(input);
    }

    private static int readVarInt(DataInputStream input) throws IOException
    {
        int value = 0;
        int shift = 0;
        while (true)
        {
            int part = input.readUnsignedByte();
            value |= (part & 0x7F) << shift;
            if ((part & 0x80) == 0)
                return value;
            shift += 7;
        }
    }
}
