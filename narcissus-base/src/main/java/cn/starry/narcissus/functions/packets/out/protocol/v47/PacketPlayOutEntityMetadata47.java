package cn.starry.narcissus.functions.packets.out.protocol.v47;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;

import java.io.IOException;

/**
 * 1.8 Entity Metadata (0x1C) for the tracked player entity: VarInt entity id,
 * the legacy skin-layer Byte entry (header byte 13, matching the original
 * limbo wire format) with the combined skin parts value, and the 0x7F
 * terminator.
 */
public final class PacketPlayOutEntityMetadata47 implements PacketOut {
    private final int entityId;
    private final int skinParts;

    public PacketPlayOutEntityMetadata47(int entityId, int skinParts) {
        this.entityId = entityId;
        this.skinParts = skinParts;
    }

    @Override
    public void write(PacketSerializer out) throws IOException {
        out.writeVarInt(this.entityId);
        out.writeByte((byte) 13); // legacy skin-layer metadata header byte
        out.writeByte((byte) this.skinParts);
        out.writeByte((byte) 0x7F); // metadata terminator
    }
}
