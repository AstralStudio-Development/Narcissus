package cn.starry.narcissus.functions.packets.out.protocol.v47;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;

import java.io.IOException;

/** 1.8 Player Position And Look (0x08): doubles, floats, then on-ground bool. */
public final class PacketPlayOutPlayerPositionAndLook47 implements PacketOut {
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final byte onGround;

    public PacketPlayOutPlayerPositionAndLook47(double x, double y, double z,
                                                float yaw, float pitch, byte onGround) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.onGround = onGround;
    }

    @Override
    public void write(PacketSerializer out) throws IOException {
        out.writeDouble(this.x);
        out.writeDouble(this.y);
        out.writeDouble(this.z);
        out.writeFloat(this.yaw);
        out.writeFloat(this.pitch);
        out.writeBoolean(this.onGround != 0);
    }
}
