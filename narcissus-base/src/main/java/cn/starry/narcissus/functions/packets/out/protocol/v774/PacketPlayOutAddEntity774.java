package cn.starry.narcissus.functions.packets.out.protocol.v774;

import cn.starry.narcissus.functions.packets.PacketSerializer;
import cn.starry.narcissus.functions.packets.out.PacketOut;
import java.io.IOException;
import java.util.UUID;

/** Modern Add Entity includes a compact LpVec3; zero movement has a one-byte encoding. */
public record PacketPlayOutAddEntity774(int entityId, UUID uuid, int typeId,
        double x, double y, double z, float yaw, float pitch) implements PacketOut {
    @Override public void write(PacketSerializer out) throws IOException {
        out.writeVarInt(entityId);
        out.writeUUID(uuid);
        out.writeVarInt(typeId);
        out.writeDouble(x);
        out.writeDouble(y);
        out.writeDouble(z);
        out.writeByte((byte) 0); // LpVec3.ZERO
        out.writeByte(ModernPacketEncoding.angle(pitch));
        out.writeByte(ModernPacketEncoding.angle(yaw));
        out.writeByte(ModernPacketEncoding.angle(yaw));
        out.writeVarInt(0); // entity-dependent spawn data
    }
}
