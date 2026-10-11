package cn.starry.narcissus.functions.world;

import cn.starry.narcissus.functions.modern.ModernBlockStateMap;
import cn.starry.narcissus.functions.world.nbt.CompoundTag;
import cn.starry.narcissus.functions.world.nbt.NBTTag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchematicTest
{
    @Test
    void readsNestedSpongeV3AndPlacesModernStatesAndBlockEntities() throws Exception
    {
        World world = Schematic.centered(readFixture(true), 0, 90, 0).getWorld();

        assertTrue(world.hasModernBlockStates());
        Chunk placedChunk = world.getChunkAtWorldPos(0, 0);
        assertNotNull(placedChunk);
        assertEquals("minecraft:stone", world.getModernState(
                placedChunk.getModernStateKey(0, 90, 0)));
        assertEquals(1, placedChunk.getModernBlockEntities().size());
        ModernBlockEntity entity = placedChunk.getModernBlockEntities().get(0);
        assertEquals("minecraft:sign", entity.getId());
        assertEquals(0, entity.getPosition().getX());
        assertEquals(90, entity.getPosition().getY());
        assertEquals(0, entity.getPosition().getZ());
        assertEquals(0, entity.getData().get("x").toIntTag().getValue());
        assertEquals(90, entity.getData().get("y").toIntTag().getValue());
        assertEquals(0, entity.getData().get("z").toIntTag().getValue());
    }

    @Test
    void unwrapsWorldEditOuterSchematicCompound() throws Exception
    {
        CompoundTag outer = new CompoundTag();
        outer.put("Schematic", readFixture(true));
        World world = Schematic.centered(outer, 0, 90, 0).getWorld();
        assertTrue(world.hasModernBlockStates());
        assertNotNull(world.getChunkAtWorldPos(0, 0));
    }

    @Test
    void keepsSpongeV2RootLayoutCompatible() throws Exception
    {
        World world = Schematic.toWorld(readFixture(false), 10, 20, 30);
        assertTrue(world.hasModernBlockStates());
        Chunk chunk = world.getChunks()[0][0];
        assertEquals("minecraft:stone", world.getModernState(chunk.getModernStateKey(11, 20, 14)));
        assertEquals(1, chunk.getModernBlockEntities().size());
    }

    @Test
    void complexModernBlockEntityPayloadsRoundTripAsNetworkNbt() throws Exception
    {
        try (InputStream raw = Schematic.class.getResourceAsStream("/world/complex-modern.schem"))
        {
            assertNotNull(raw);
            CompoundTag nbt = NBTTag.readTag(new GZIPInputStream(raw)).toCompoundTag();
            World world = Schematic.centered(nbt, 0, 90, 0).getWorld();
            int checked = 0;
            for (Chunk[] column : world.getChunks())
            {
                for (Chunk chunk : column)
                {
                    if (chunk == null)
                        continue;
                    for (ModernBlockEntity entity : chunk.getModernBlockEntities())
                    {
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        NBTTag.writeTag(bytes, entity.getData(), false);
                        entity.getData().write(bytes, false);
                        CompoundTag decoded = NBTTag.readUnnamedTag(
                                new ByteArrayInputStream(bytes.toByteArray())).toCompoundTag();
                        assertEquals(entity.getId(), decoded.get("id").toStringTag().getValue());
                        assertEquals(entity.getPosition().getX(), decoded.get("x").toIntTag().getValue());
                        assertEquals(entity.getPosition().getY(), decoded.get("y").toIntTag().getValue());
                        assertEquals(entity.getPosition().getZ(), decoded.get("z").toIntTag().getValue());
                        checked++;
                    }
                }
            }
            assertTrue(checked > 0);
        }
    }

    @Test
    void oldLegacySchematicStillLoads() throws Exception
    {
        try (InputStream raw = Schematic.class.getResourceAsStream("/world.schematic"))
        {
            assertNotNull(raw);
            CompoundTag nbt = NBTTag.readTag(new GZIPInputStream(raw)).toCompoundTag();
            Schematic.Placement placement = Schematic.centered(nbt, 0, 90, 0);
            assertFalse(placement.isModern());
            assertEquals(35, placement.getWidth());
            assertEquals(57, placement.getHeight());
            assertEquals(35, placement.getLength());
        }
    }

    @Test
    void modernStateMapCanonicalizesOrderAndMissingDefaultProperties()
    {
        ModernBlockStateMap map = ModernBlockStateMap.load("v774");
        assertEquals(0, map.resolve("minecraft:air"));
        assertTrue(map.resolve("minecraft:jungle_leaves[persistent=true,distance=1]") != 0);
        assertTrue(map.resolve("minecraft:jungle_leaves[distance=1,persistent=true]") != 0);
        assertTrue(map.resolve("minecraft:player_head[rotation=6]") != 0);
    }

    private static CompoundTag readFixture(boolean nested) throws Exception
    {
        return NBTTag.readTag(new ByteArrayInputStream(buildFixture(nested))).toCompoundTag();
    }

    private static byte[] buildFixture(boolean nested) throws IOException
    {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(10);
        writeString(out, "Schematic");
        writeNamedInt(out, "Version", nested ? 3 : 2);
        writeNamedInt(out, "DataVersion", 4325);
        writeNamedShort(out, "Width", (short) 2);
        writeNamedShort(out, "Height", (short) 1);
        writeNamedShort(out, "Length", (short) 1);
        writeNamedIntArray(out, "Offset", new int[]{0, 0, 0});

        if (nested)
        {
            writeNamedCompoundStart(out, "Blocks");
            writePalette(out);
            writeNamedByteArray(out, "Data", new byte[]{0, 1});
            writeBlockEntities(out);
            out.writeByte(0);
            out.writeByte(0);
        }
        else
        {
            writeNamedCompoundStart(out, "Palette");
            writePalettePayload(out);
            out.writeByte(0);
            writeNamedByteArray(out, "BlockData", new byte[]{0, 1});
            writeBlockEntities(out);
        }
        out.writeByte(0);
        out.flush();
        return bytes.toByteArray();
    }

    private static void writePalette(DataOutputStream out) throws IOException
    {
        writeNamedCompoundStart(out, "Palette");
        writePalettePayload(out);
        out.writeByte(0);
    }

    private static void writePalettePayload(DataOutputStream out) throws IOException
    {
        writeNamedInt(out, "minecraft:air", 0);
        writeNamedInt(out, "minecraft:stone", 1);
    }

    private static void writeBlockEntities(DataOutputStream out) throws IOException
    {
        writeTagHeader(out, 9, "BlockEntities");
        out.writeByte(10);
        out.writeInt(1);
        writeNamedIntArray(out, "Pos", new int[]{1, 0, 0});
        writeNamedString(out, "Id", "minecraft:sign");
        writeNamedCompoundStart(out, "Data");
        writeNamedString(out, "Text1", "hello");
        writeNamedString(out, "Text2", "");
        writeNamedString(out, "Text3", "");
        writeNamedString(out, "Text4", "");
        out.writeByte(0);
        out.writeByte(0);
    }

    private static void writeNamedCompoundStart(DataOutputStream out, String name) throws IOException
    {
        writeTagHeader(out, 10, name);
    }

    private static void writeNamedByteArray(DataOutputStream out, String name, byte[] value) throws IOException
    {
        writeTagHeader(out, 7, name);
        out.writeInt(value.length);
        out.write(value);
    }

    private static void writeNamedIntArray(DataOutputStream out, String name, int[] value) throws IOException
    {
        writeTagHeader(out, 11, name);
        out.writeInt(value.length);
        for (int item : value)
            out.writeInt(item);
    }

    private static void writeNamedInt(DataOutputStream out, String name, int value) throws IOException
    {
        writeTagHeader(out, 3, name);
        out.writeInt(value);
    }

    private static void writeNamedShort(DataOutputStream out, String name, short value) throws IOException
    {
        writeTagHeader(out, 2, name);
        out.writeShort(value);
    }

    private static void writeNamedString(DataOutputStream out, String name, String value) throws IOException
    {
        writeTagHeader(out, 8, name);
        writeString(out, value);
    }

    private static void writeTagHeader(DataOutputStream out, int id, String name) throws IOException
    {
        out.writeByte(id);
        writeString(out, name);
    }

    private static void writeString(DataOutputStream out, String value) throws IOException
    {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeShort(bytes.length);
        out.write(bytes);
    }
}
