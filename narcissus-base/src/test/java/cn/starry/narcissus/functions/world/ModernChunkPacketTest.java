package cn.starry.narcissus.functions.world;

import cn.starry.narcissus.functions.modern.BlockEntityTypeMap;
import cn.starry.narcissus.functions.modern.LegacyBlockMap;
import cn.starry.narcissus.functions.modern.ModernBlockStateMap;
import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutBlockEntityData774;
import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutChunkWithLight774;
import cn.starry.narcissus.functions.world.nbt.CompoundTag;
import cn.starry.narcissus.functions.world.nbt.IntTag;
import cn.starry.narcissus.functions.world.nbt.NBTTag;
import cn.starry.narcissus.functions.world.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModernChunkPacketTest
{
    @Test
    void writesModernStatesAndUnnamedBlockEntityNbt() throws Exception
    {
        ModernBlockPalette palette = new ModernBlockPalette();
        World world = new World(1, 1, 0, 0, palette);
        world.setModernBlockState(0, 20, 0, palette.indexOf("minecraft:stone"));

        CompoundTag data = new CompoundTag();
        data.put("id", new StringTag("minecraft:sign"));
        data.put("x", new IntTag(0));
        data.put("y", new IntTag(20));
        data.put("z", new IntTag(0));
        data.put("Text1", new StringTag("{\"text\":\"{nar_ver}\"}"));
        world.addModernBlockEntity(new ModernBlockEntity("minecraft:sign",
                new BlockPosition(0, 20, 0), data));

        Chunk chunk = world.getChunks()[0][0];
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new PacketPlayOutChunkWithLight774(world, chunk, 24, 40,
                LegacyBlockMap.load("v774"), ModernBlockStateMap.load("v774"),
                BlockEntityTypeMap.load("v774"), "1.21.11", -64, true, false)
                .write(new PacketSerializer(bytes));

        PacketSerializer input = new PacketSerializer(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(0, input.readInt());
        assertEquals(0, input.readInt());
        assertEquals(1, input.readVarInt());
        assertEquals(4, input.readVarInt());
        int heightmapLongs = input.readVarInt();
        input.readBytes(heightmapLongs * 8);
        int chunkDataLength = input.readVarInt();
        assertTrue(chunkDataLength > 0);
        input.readBytes(chunkDataLength);

        assertEquals(1, input.readVarInt());
        assertEquals(0, input.readUnsignedByte());
        assertEquals(20, input.readShort());
        assertEquals(7, input.readVarInt());
        CompoundTag decoded = NBTTag.readUnnamedTag(
                new ByteArrayInputStream(input.readBytes(input.available()))).toCompoundTag();
        assertEquals("minecraft:sign", decoded.get("id").toStringTag().getValue());
        assertEquals(0, decoded.get("x").toIntTag().getValue());
        assertEquals(20, decoded.get("y").toIntTag().getValue());
        assertEquals(0, decoded.get("z").toIntTag().getValue());
        String decodedText = decoded.get("Text1").toStringTag().getValue();
        assertTrue(decodedText.contains("1.21.11"));
        assertTrue(decodedText.contains("\"color\":\"blue\""));
        assertEquals("{\"text\":\"{nar_ver}\"}", data.get("Text1").toStringTag().getValue());
    }

    @Test
    void emptyChunkBodyIsReusableAcrossCoordinates() throws Exception
    {
        ByteArrayOutputStream firstBytes = new ByteArrayOutputStream();
        new PacketPlayOutChunkWithLight774(0, 0, 16, 56, true, false)
                .write(new PacketSerializer(firstBytes));

        ByteArrayOutputStream secondBytes = new ByteArrayOutputStream();
        new PacketPlayOutChunkWithLight774(17, -4, 16, 56, true, false)
                .write(new PacketSerializer(secondBytes));

        byte[] first = firstBytes.toByteArray();
        byte[] second = secondBytes.toByteArray();
        assertEquals(first.length, second.length);
        assertArrayEquals(java.util.Arrays.copyOfRange(first, 8, first.length),
                java.util.Arrays.copyOfRange(second, 8, second.length));
    }

    @Test
    void refreshesModernBlockEntityDataAfterChunkLoad() throws Exception
    {
        CompoundTag data = new CompoundTag();
        data.put("Text1", new StringTag("{nar_ver}"));
        ModernBlockEntity entity = new ModernBlockEntity("minecraft:sign",
                new BlockPosition(18, 20, 34), data);
        int typeId = BlockEntityTypeMap.load("v774").resolve("minecraft:sign");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new PacketPlayOutBlockEntityData774(entity, typeId, "1.21.11")
                .write(new PacketSerializer(bytes));

        PacketSerializer input = new PacketSerializer(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(packBlockPosition(18, 20, 34), input.readLong());
        assertEquals(typeId, input.readVarInt());
        CompoundTag decoded = NBTTag.readUnnamedTag(
                new ByteArrayInputStream(input.readBytes(input.available()))).toCompoundTag();
        assertEquals("§91.21.11", decoded.get("Text1").toStringTag().getValue());
        assertEquals("{nar_ver}", data.get("Text1").toStringTag().getValue());
    }

    private static long packBlockPosition(int x, int y, int z)
    {
        return ((long) (x & 0x3FFFFFF) << 38)
                | ((long) (y & 0xFFF) << 26)
                | (z & 0x3FFFFFFL);
    }
}
