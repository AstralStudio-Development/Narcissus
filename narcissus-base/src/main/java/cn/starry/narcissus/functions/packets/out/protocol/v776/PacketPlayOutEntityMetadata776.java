package cn.starry.narcissus.functions.packets.out.protocol.v776;

import cn.starry.narcissus.functions.packets.out.protocol.v774.PacketPlayOutEntityMetadata774;

/** 26.2 uses the same metadata serializers and field indices, at a different packet id. */
public final class PacketPlayOutEntityMetadata776 extends PacketPlayOutEntityMetadata774 {
    public PacketPlayOutEntityMetadata776(int entityId) { super(entityId); }
    public PacketPlayOutEntityMetadata776(int entityId, int skinParts) { super(entityId, skinParts); }
    public PacketPlayOutEntityMetadata776(int entityId, int skinParts, int poseId,
            boolean sleeping, int x, int y, int z) {
        super(entityId, skinParts, poseId, sleeping, x, y, z);
    }
}
