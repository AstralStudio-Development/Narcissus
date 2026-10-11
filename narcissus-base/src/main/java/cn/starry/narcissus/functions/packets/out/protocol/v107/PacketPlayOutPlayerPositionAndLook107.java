package cn.starry.narcissus.functions.packets.out.protocol.v107;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;

import java.io.IOException;

/**
 * 1.9+ Player Position And Look (0x2E): doubles, floats, on-ground Bool then
 * the VarInt teleport id that the client must confirm (1.9 only; removed
 * again in 1.9.4+ via the matching read-side change, harmless as 0).
 */
public final class PacketPlayOutPlayerPositionAndLook107 implements PacketOut {
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final byte onGround;
    private final int teleportId;

    public PacketPlayOutPlayerPositionAndLook107(double x, double y, double z,
                                                 float yaw, float pitch,
                                                 byte onGround, int teleportId) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.onGround = onGround;
        this.teleportId = teleportId;
    }

    @Override
    public void write(PacketSerializer out) throws IOException {
        out.writeDouble(this.x);
        out.writeDouble(this.y);
        out.writeDouble(this.z);
        out.writeFloat(this.yaw);
        out.writeFloat(this.pitch);
        out.writeBoolean(this.onGround != 0);
        out.writeVarInt(this.teleportId);
    }
}
