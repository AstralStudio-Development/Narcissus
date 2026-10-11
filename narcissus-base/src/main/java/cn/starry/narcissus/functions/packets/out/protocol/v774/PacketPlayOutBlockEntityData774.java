package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import cn.starry.narcissus.functions.world.ModernBlockEntity;
import cn.starry.narcissus.functions.world.SignTextUtil;
import cn.starry.narcissus.functions.world.nbt.CompoundTag;
import cn.starry.narcissus.functions.world.nbt.NBTTag;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

public final class PacketPlayOutBlockEntityData774 implements PacketOut {
    private final long position;
    private final int typeId;
    private final byte[] nbt;
    public PacketPlayOutBlockEntityData774(ModernBlockEntity entity, int typeId, String version) {
        var pos = entity.getPosition();
        this.position = ModernPacketEncoding.position(pos.getX(), pos.getY(), pos.getZ());
        if (typeId < 0) throw new IllegalArgumentException("Unregistered block entity type");
        this.typeId = typeId;
        this.nbt = encodeNbt(entity.getData(), version);
    }
    static byte[] encodeNbt(CompoundTag original, String version) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bytes.write(original.getId());
            original.write(bytes, false);
            CompoundTag copy = NBTTag.readUnnamedTag(new ByteArrayInputStream(bytes.toByteArray())).toCompoundTag();
            SignTextUtil.replacePlaceholders(copy, version);
            bytes.reset();
            bytes.write(copy.getId());
            copy.write(bytes, false);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot encode block entity NBT", e);
        }
    }
    @Override public void write(PacketSerializer out) throws IOException {
        out.writeLong(position);
        out.writeVarInt(typeId);
        out.writeRawBytes(nbt);
    }
}
