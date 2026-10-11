package cn.starry.narcissus.functions.packets.out.protocol.v110;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import cn.starry.narcissus.functions.world.SignTileEntity;
import cn.starry.narcissus.functions.world.nbt.CompoundTag;
import cn.starry.narcissus.functions.world.nbt.StringTag;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/** 1.10+ uses a classic named root compound, directly after the action byte. */
public final class PacketPlayOutUpdateBlockEntity110 implements PacketOut {
    private final long position;
    private final byte action;
    private final byte[] nbt;

    public PacketPlayOutUpdateBlockEntity110(SignTileEntity sign, byte action) {
        var pos = sign.getBlockPosition();
        this.position = ((pos.getX() & 0x3ffffffL) << 38)
                | ((pos.getY() & 0xfffL) << 26) | (pos.getZ() & 0x3ffffffL);
        this.action = action;
        CompoundTag compound = new CompoundTag();
        compound.setName("");
        compound.put("Text1", new StringTag(component(sign.getText1())));
        compound.put("Text2", new StringTag(component(sign.getText2())));
        compound.put("Text3", new StringTag(component(sign.getText3())));
        compound.put("Text4", new StringTag(component(sign.getText4())));
        compound.put("id", new StringTag(sign.getId()));
        compound.put("x", new cn.starry.narcissus.functions.world.nbt.IntTag(pos.getX()));
        compound.put("y", new cn.starry.narcissus.functions.world.nbt.IntTag(pos.getY()));
        compound.put("z", new cn.starry.narcissus.functions.world.nbt.IntTag(pos.getZ()));
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bytes.write(compound.getId());
            compound.write(bytes, true);
            nbt = bytes.toByteArray();
        } catch (IOException e) { throw new java.io.UncheckedIOException(e); }
    }
    private static String component(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("\"")) return text;
        return cn.starry.narcissus.util.TextUtil.toJsonComponent(text);
    }
    @Override public void write(PacketSerializer out) throws IOException {
        out.writeLong(position);
        out.writeByte(action);
        out.writeRawBytes(nbt); // no length prefix, and no compression
    }
}
