package cn.starry.narcissus.functions.packets.out.protocol.v47;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;

import java.io.IOException;
import java.util.UUID;

/**
 * 1.8 Spawn Player (0x0C): VarInt id, UUID, fixed-point Int coordinates
 * (value * 32), angle bytes, Short current item and the 0x7F metadata
 * terminator.
 */
public final class PacketPlayOutSpawnPlayer47 implements PacketOut {
    private final int entityId;
    private final UUID uuid;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public PacketPlayOutSpawnPlayer47(int entityId, UUID uuid,
                                      double x, double y, double z,
                                      float yaw, float pitch) {
        this.entityId = entityId;
        this.uuid = uuid;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    @Override
    public void write(PacketSerializer out) throws IOException {
        out.writeVarInt(this.entityId);
        out.writeUUID(this.uuid);
        out.writeInt((int) Math.floor(this.x * 32.0D));
        out.writeInt((int) Math.floor(this.y * 32.0D));
        out.writeInt((int) Math.floor(this.z * 32.0D));
        out.writeByte((byte) (int) (this.yaw * 256.0F / 360.0F));
        out.writeByte((byte) (int) (this.pitch * 256.0F / 360.0F));
        out.writeShort((short) 0); // current item
        out.writeByte((byte) 0x7F); // metadata terminator
    }
}
