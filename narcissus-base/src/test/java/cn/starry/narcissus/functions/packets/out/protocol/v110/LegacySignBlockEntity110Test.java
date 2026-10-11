package cn.starry.narcissus.functions.packets.out.protocol.v110;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.world.SignTileEntity;
import cn.starry.narcissus.functions.world.nbt.CompoundTag;
import cn.starry.narcissus.functions.world.nbt.IntTag;
import cn.starry.narcissus.functions.world.nbt.StringTag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the 1.10+ Update Block Entity sign encoding: 1.8-style packed
 * position, action byte and a named root NBT compound without a length prefix.
 * The schematic {@link SignTileEntity} constructor is package-private, so it
 * is reached reflectively from this protocol package; the same assertions
 * also exist as a direct test under {@code functions.world}.
 */
class LegacySignBlockEntity110Test {

    @Test
    void preservesExistingJsonSignTextAndHasNoNbtLengthPrefix() throws Exception {
        var data = new CompoundTag();
        data.put("x", new IntTag(-5)); data.put("y", new IntTag(90)); data.put("z", new IntTag(7));
        String json = "{\"text\":\"line1\",\"color\":\"blue\"}";
        data.put("Text1", new StringTag(json)); data.put("Text2", new StringTag(""));
        data.put("Text3", new StringTag("")); data.put("Text4", new StringTag(""));
        Constructor<SignTileEntity> ctor = SignTileEntity.class.getDeclaredConstructor(CompoundTag.class);
        ctor.setAccessible(true);
        var bytes = new ByteArrayOutputStream();
        new PacketPlayOutUpdateBlockEntity110(ctor.newInstance(data), (byte) 9).write(new PacketSerializer(bytes));
        var in = new PacketSerializer(new ByteArrayInputStream(bytes.toByteArray()));
        long pos = in.readLong();
        assertEquals(-5, (int) (pos >> 38));
        assertEquals(90, (int) ((pos >> 26) & 0xfff));
        assertEquals(7, (int) (pos << 38 >> 38));
        assertEquals(9, in.readUnsignedByte());
        byte[] nbt = in.readBytes(in.available());
        assertTrue(nbt.length > 3);
        assertEquals(10, nbt[0] & 0xff); // immediately follows action; never a VarInt byte length
        assertEquals(0, nbt[1]); assertEquals(0, nbt[2]);
        var raw = new ByteArrayInputStream(nbt);
        CompoundTag decoded = cn.starry.narcissus.functions.world.nbt.NBTTag.readTag(raw).toCompoundTag();
        assertEquals(json, decoded.get("Text1").toStringTag().getValue());
        assertEquals(0, raw.available());
    }
}
