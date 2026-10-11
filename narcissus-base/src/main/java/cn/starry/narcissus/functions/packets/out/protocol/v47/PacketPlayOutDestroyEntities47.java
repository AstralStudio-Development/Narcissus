package cn.starry.narcissus.functions.packets.out.protocol.v47;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;

import java.io.IOException;

/** 1.8 Destroy Entities (0x13): VarInt count followed by VarInt entity ids. */
public final class PacketPlayOutDestroyEntities47 implements PacketOut {
    private final int[] entityIds;

    public PacketPlayOutDestroyEntities47(int... entityIds) {
        this.entityIds = entityIds.clone();
    }

    @Override
    public void write(PacketSerializer out) throws IOException {
        out.writeVarInt(this.entityIds.length);
        for (int entityId : this.entityIds)
            out.writeVarInt(entityId);
    }
}
