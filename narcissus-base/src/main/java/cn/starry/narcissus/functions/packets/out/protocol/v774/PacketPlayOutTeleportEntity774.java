package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import java.io.IOException;

public class PacketPlayOutTeleportEntity774 implements PacketOut {
    private final int entityId;
    private final double x, y, z;
    private final float yaw, pitch;
    public PacketPlayOutTeleportEntity774(int entityId, double x, double y, double z, float yaw, float pitch) {
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }
    @Override public void write(PacketSerializer out) throws IOException {
        out.writeVarInt(entityId);
        ModernPacketEncoding.positionMove(out, x, y, z, yaw, pitch);
        out.writeBoolean(true); // on ground
    }
}
