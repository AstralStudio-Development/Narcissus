package cn.starry.narcissus.functions.world;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.protocol.v110.PacketPlayOutUpdateBlockEntity110;
import cn.starry.narcissus.functions.world.nbt.CompoundTag;
import cn.starry.narcissus.functions.world.nbt.IntTag;
import cn.starry.narcissus.functions.world.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the 1.10+ Update Block Entity sign encoding: 1.8-style packed
 * position, action byte and a named root NBT compound without a length prefix.
 * Lives in {@code functions.world} because SignTileEntity's schematic
 * constructor is package-private.
 */
class LegacySignBlockEntity110Test {

    @Test
    void encodesLegacyPackedPositionActionAndNamedRootNbt() throws Exception {
        CompoundTag data = new CompoundTag();
        data.put("x", new IntTag(5)); data.put("y", new IntTag(60)); data.put("z", new IntTag(-7));
        data.put("Text1", new StringTag("line1")); data.put("Text2", new StringTag("line2"));
        data.put("Text3", new StringTag("")); data.put("Text4", new StringTag("line4"));
        var sign = new SignTileEntity(data);
        var bytes = new ByteArrayOutputStream();
        new PacketPlayOutUpdateBlockEntity110(sign, (byte) 9).write(new PacketSerializer(bytes));
        var in = new PacketSerializer(new ByteArrayInputStream(bytes.toByteArray()));
        long pos = in.readLong();
        assertEquals(5, (int) (pos >> 38));
        assertEquals(60, (int) ((pos >> 26) & 0xfff));
        assertEquals(-7, (int) (pos << 38 >> 38));
        assertEquals(9, in.readUnsignedByte());
        byte[] nbt = in.readBytes(in.available());
        assertTrue(nbt.length > 3);
        assertEquals(10, nbt[0] & 0xff);
        assertEquals(0, nbt[1]); assertEquals(0, nbt[2]); // empty root name
        var raw = new ByteArrayInputStream(nbt);
        CompoundTag decoded = cn.starry.narcissus.functions.world.nbt.NBTTag.readTag(raw).toCompoundTag();
        assertEquals(0, raw.available());
        assertEquals("{\"text\":\"line1\"}", decoded.get("Text1").toStringTag().getValue());
        assertEquals(5, decoded.get("x").toIntTag().getValue());
        assertEquals(60, decoded.get("y").toIntTag().getValue());
        assertEquals(-7, decoded.get("z").toIntTag().getValue());
    }
}
