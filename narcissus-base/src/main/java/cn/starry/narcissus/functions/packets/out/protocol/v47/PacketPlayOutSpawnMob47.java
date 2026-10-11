package cn.starry.narcissus.functions.packets.out.protocol.v47;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;

import java.io.IOException;

/**
 * 1.8 Spawn Mob (0x0F): VarInt id, UByte type, fixed-point Int position,
 * angle bytes, Short velocity triple and the full invisible-armor-stand
 * metadata blob ending in the 0x7F terminator. Type 30 is the armor stand
 * used as the NPC seat entity.
 */
public final class PacketPlayOutSpawnMob47 implements PacketOut {
    private static final byte ARMOR_STAND_TYPE = 30;

    private final int entityId;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public PacketPlayOutSpawnMob47(int entityId, double x, double y, double z,
                                   float yaw, float pitch) {
        this.entityId = entityId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    @Override
    public void write(PacketSerializer out) throws IOException {
        out.writeVarInt(this.entityId);
        out.writeByte(ARMOR_STAND_TYPE);
        out.writeInt((int) Math.floor(this.x * 32.0D));
        out.writeInt((int) Math.floor(this.y * 32.0D));
        out.writeInt((int) Math.floor(this.z * 32.0D));
        out.writeByte((byte) (int) (this.yaw * 256.0F / 360.0F));
        out.writeByte((byte) (int) (this.pitch * 256.0F / 360.0F));
        out.writeByte((byte) (int) (this.yaw * 256.0F / 360.0F)); // head yaw
        out.writeShort((short) 0); // velocity X
        out.writeShort((short) 0); // velocity Y
        out.writeShort((short) 0); // velocity Z
        // Metadata: index 0 Byte flags = 0x20 (invisible), index 10 Byte
        // armor-stand flags 0x19, then the terminator.
        out.writeByte((byte) 0x00);
        out.writeByte((byte) 0x20);
        out.writeByte((byte) 10);
        out.writeByte((byte) 0x19);
        out.writeByte((byte) 0x7F);
    }
}
