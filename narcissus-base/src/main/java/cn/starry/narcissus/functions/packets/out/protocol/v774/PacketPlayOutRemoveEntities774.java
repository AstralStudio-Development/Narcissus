package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import java.io.IOException;

public class PacketPlayOutRemoveEntities774 implements PacketOut {
    private final int[] entityIds;
    public PacketPlayOutRemoveEntities774(int... entityIds) { this.entityIds = entityIds.clone(); }
    @Override public void write(PacketSerializer out) throws IOException {
        out.writeVarInt(entityIds.length);
        for (int id : entityIds) out.writeVarInt(id);
    }
}
